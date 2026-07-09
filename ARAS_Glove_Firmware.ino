#include <Wire.h>
#include <MPU6050.h>
#include "MAX30105.h"  // SparkFun MAX3010x library
#include "BluetoothSerial.h"

BluetoothSerial SerialBT;
// ===== HARDWARE PINS =====
#define RCWL_PIN        34    // RCWL-0516 digital output pin
#define VIBRATION_PIN   25    // Coin vibration motor pin (PWM capable)

MAX30105 particleSensor;
MPU6050 mpu;

// ===== HRV / RMSSD =====
#define IBI_BUFFER_SIZE     20    // number of IBIs to compute RMSSD over
#define MIN_IBI_MS          400   // reject IBIs below this (>150 BPM)
#define MAX_IBI_MS          1500  // reject IBIs above this (<40 BPM)
#define ARTIFACT_THRESH 0.35  // reject if IBI deviates >35% from previous
#define MIN_PEAK_INTERVAL 400  // ms
#define BASELINE_SAMPLES    15    // RMSSD readings to build baseline
#define IR_THRESHOLD 60000
float ibiBuffer[IBI_BUFFER_SIZE];
int ibiCount = 0;

float rmssdBuffer[BASELINE_SAMPLES];
int rmssdIndex = 0;
bool baselineReady = false;
float baselineRMSSD = 0;

// ===== PEAK DETECTION STATE =====
long lastPeakTime = 0;
long lastIBI = 0;
bool peakDetected = false;

unsigned long lastSampleTime = 0;
long irPrev = 0;
long irPrev2 = 0;

// ===== MOTION (MPU6050) =====
float motionBaseline = 0;
bool motionReady = false;

// ===== RCWL-0516 RADAR =====
unsigned long lastRCWLTrigger = 0;
#define RCWL_COOLDOWN   2000  // 2 second cooldown between detections
bool objectDetected = false;

// ===== ALERT PRIORITY SYSTEM =====
enum AlertType {
  NONE = 0,
  OBJECT_DETECTED = 1,    // Priority 3 (lowest)
  DROWSINESS = 2,         // Priority 2
  ACCIDENT = 3            // Priority 1 (highest)
};

AlertType currentAlert = NONE;
unsigned long alertStartTime = 0;
#define ALERT_DURATION  3000  // How long to maintain an alert before checking again

// ===================================================
// VIBRATION PATTERNS
// ===================================================

void vibrateAccident() {
  // ACCIDENT: Continuous strong vibration (3 seconds)
  Serial.println("🚨 VIBRATION: ACCIDENT (continuous)");
  digitalWrite(VIBRATION_PIN, HIGH);
  delay(3000);
  digitalWrite(VIBRATION_PIN, LOW);
}

void vibrateDrowsiness() {
  // DROWSINESS: 3 long pulses (500ms ON, 200ms OFF)
  Serial.println("⚠️  VIBRATION: DROWSINESS (3 long pulses)");
  for (int i = 0; i < 3; i++) {
    digitalWrite(VIBRATION_PIN, HIGH);
    delay(500);
    digitalWrite(VIBRATION_PIN, LOW);
    delay(200);
  }
}

void vibrateObjectDetected() {
  // OBJECT DETECTED: 2 short pulses (150ms ON, 100ms OFF)
  Serial.println("👁️  VIBRATION: OBJECT DETECTED (2 short pulses)");
  for (int i = 0; i < 2; i++) {
    digitalWrite(VIBRATION_PIN, HIGH);
    delay(150);
    digitalWrite(VIBRATION_PIN, LOW);
    delay(100);
  }
}

// Execute vibration based on alert type
void executeVibration(AlertType alert) {
  switch(alert) {
    case ACCIDENT:
      vibrateAccident();
      break;
    case DROWSINESS:
      vibrateDrowsiness();
      break;
    case OBJECT_DETECTED:
      vibrateObjectDetected();
      break;
    default:
      break;
  }
}

// ===================================================
// RCWL-0516 RADAR DETECTION
// ===================================================
bool checkRCWL() {
  int rcwlState = digitalRead(RCWL_PIN);
  
  if (rcwlState == HIGH) {
    unsigned long now = millis();
    
    // Debounce: only trigger if cooldown period has passed
    if (now - lastRCWLTrigger > RCWL_COOLDOWN) {
      lastRCWLTrigger = now;
      Serial.println("👁️  RCWL: Moving object detected!");
      return true;
    }
  }
  
  return false;
}

// ===================================================
// PEAK DETECTION (simple slope-based on IR signal)
// ===================================================
bool detectPeak(long irValue) {
  bool peak = false;
  unsigned long now = millis();

  // peak when previous sample is higher than neighbors
  if ((irPrev > irPrev2) && (irPrev > irValue) && (irPrev > IR_THRESHOLD)) {

    // ignore peaks that are too close (refractory period)
    if (now - lastPeakTime > MIN_PEAK_INTERVAL) {
      peak = true;
    }
  }

  irPrev2 = irPrev;
  irPrev = irValue;

  return peak;
}
// ===================================================
// ARTIFACT REJECTION
// ===================================================
bool isValidIBI(long ibi) {
  if (ibi < MIN_IBI_MS || ibi > MAX_IBI_MS) return false;

  // 🔥 NEW: allow first few IBIs freely
  if (ibiCount < 5) return true;

  if (lastIBI > 0) {
    float deviation = abs((float)(ibi - lastIBI)) / (float)lastIBI;
    if (deviation > ARTIFACT_THRESH) return false;
  }

  return true;
}

// ===================================================
// COMPUTE RMSSD from IBI buffer
// ===================================================
float computeRMSSD() {
  if (ibiCount < 2) return -1;  // not enough data
  
  float sumSqDiff = 0;
  int count = 0;

  for (int i = 1; i < ibiCount; i++) {
    float diff = ibiBuffer[i] - ibiBuffer[i - 1];
    sumSqDiff += diff * diff;
    count++;
  }

  if (count == 0) return -1;
  return sqrt(sumSqDiff / count);
}

float processIBI(long ibi) {

  // 🔥 1. Handle missed beats (too large interval)
  if (ibi > 1800) {
    Serial.println("[HRV] Missed beat detected, skipping");
    return -1;
  }

  // 🔥 2. Warm-up phase: accept initial IBIs freely
  if (ibiCount < 5) {
    lastIBI = ibi;
  } 
  else {
    // 🔥 3. Normal artifact rejection
    if (!isValidIBI(ibi)) {
      Serial.println("[HRV] IBI rejected (artifact)");
      return -1;
    }
    lastIBI = ibi;
  }

  // Shift buffer if full
  if (ibiCount >= IBI_BUFFER_SIZE) {
    for (int i = 0; i < IBI_BUFFER_SIZE - 1; i++) {
      ibiBuffer[i] = ibiBuffer[i + 1];
    }
    ibiCount = IBI_BUFFER_SIZE - 1;
  }

  ibiBuffer[ibiCount++] = ibi;

  // Compute RMSSD only when buffer is full
  if (ibiCount >= IBI_BUFFER_SIZE) {
    return computeRMSSD();
  }

  return -1;
}
// ===================================================
// HRV BASELINE (using RMSSD)
// ===================================================
void updateHRVBaseline(float rmssd) {
  if (!baselineReady) {
    rmssdBuffer[rmssdIndex++] = rmssd;

    if (rmssdIndex >= BASELINE_SAMPLES) {
      float sum = 0;
      for (int i = 0; i < BASELINE_SAMPLES; i++) sum += rmssdBuffer[i];
      baselineRMSSD = sum / BASELINE_SAMPLES;
      baselineReady = true;

      Serial.print("✅ RMSSD Baseline set: ");
      Serial.println(baselineRMSSD);
    }
  }
}

// ===================================================
// HRV DROP CHECK
// ===================================================
bool isHRVDropping(float rmssd) {
  if (!baselineReady) return false;
  
  float drop = ((baselineRMSSD - rmssd) / baselineRMSSD) * 100.0;

  Serial.print("RMSSD drop%: ");
  Serial.print(drop);
  Serial.print("% | ");

  return (drop > 30.0);  // >30% sustained drop = drowsiness flag
}

// ===================================================
// MOTION (MPU6050)
// ===================================================
float getMotion() {
  int16_t ax, ay, az;
  mpu.getAcceleration(&ax, &ay, &az);

  float axf = ax / 16384.0;
  float ayf = ay / 16384.0;
  float azf = az / 16384.0;

  return sqrt(axf * axf + ayf * ayf + azf * azf);
}

void updateMotionBaseline(float motion) {
  if (!motionReady) {
    motionBaseline = motion;
    motionReady = true;
  }
  motionBaseline = 0.95 * motionBaseline + 0.05 * motion;
}

bool isLowMotion(float motion) {
  float deviation = abs(motion - motionBaseline);
  Serial.print("Motion Dev: ");
  Serial.print(deviation);
  Serial.print(" | ");
  return (deviation < 0.02);
}

bool detectAccident(float motion) {
  return (motion > 3.0);
}
void sendSOS(String reason) {
  String msg = "SOS:" + reason + "|TIME:" + String(millis());
  SerialBT.println(msg);
  Serial.println("📡 BT SOS Sent: " + msg);
}
// ===================================================
// PRIORITY-BASED ALERT SYSTEM
// ===================================================
void handleAlerts(bool accident, bool drowsy, bool objectDet) {
  AlertType newAlert = NONE;
  
  // Determine highest priority alert
  if (accident) {
    newAlert = ACCIDENT;
  } else if (drowsy) {
    newAlert = DROWSINESS;
  } else if (objectDet) {
    newAlert = OBJECT_DETECTED;
  }
  
  // Only change alert if:
  // 1. New alert is higher priority, OR
  // 2. Current alert duration has expired, OR
  // 3. No current alert
  unsigned long now = millis();
  
  if (newAlert > currentAlert || 
      currentAlert == NONE || 
      (now - alertStartTime > ALERT_DURATION)) {
    
    if (newAlert != NONE && newAlert != currentAlert) {
  currentAlert = newAlert;
  alertStartTime = now;
  executeVibration(currentAlert);

  // ✅ Only send SOS for ACCIDENT
  if (newAlert == ACCIDENT) {
    sendSOS("ACCIDENT");
  }
} else if (newAlert == NONE) {
      currentAlert = NONE;
    }
  }
}

// ===================================================
// DECISION SYSTEM
// ===================================================
void checkSystem(float rmssd, float motion, bool rcwlTriggered) {
  bool hrvFlag = isHRVDropping(rmssd);
  bool motionFlag = isLowMotion(motion);
  bool accident = detectAccident(motion);

  Serial.print("HRV drop: "); Serial.print(hrvFlag);
  Serial.print(" | Low motion: "); Serial.print(motionFlag);
  Serial.print(" | RCWL: "); Serial.print(rcwlTriggered);
  Serial.print(" | Accident: "); Serial.println(accident);

  // Determine alert conditions
  bool drowsinessDetected = (hrvFlag && motionFlag);
  
  if (drowsinessDetected) {
    Serial.println("⚠️  DROWSINESS DETECTED ⚠️");
  }
  if (accident) {
    Serial.println("🚨 ACCIDENT DETECTED 🚨");
  }
  if (rcwlTriggered) {
    Serial.println("👁️  OBJECT APPROACHING 👁️");
  }
  
  // Handle alerts with priority
  handleAlerts(accident, drowsinessDetected, rcwlTriggered);
}

long smoothIR(long newValue) {
  static long prev = 0;
  prev = (prev * 3 + newValue) / 4;  // simple low-pass filter
  return prev;
}
// ===================================================
// SETUP
// ===================================================
void setup() {
  Serial.begin(115200);
  Serial.println("=== ARAS GLOVE BOOT ===");

  SerialBT.begin("ARAS_GLOVE");
  Serial.println("Bluetooth started: ARAS_GLOVE");
  Wire.begin(21, 22);  // SDA, SCL for ESP32

  // Pin setup
  pinMode(RCWL_PIN, INPUT);
  pinMode(VIBRATION_PIN, OUTPUT);
  digitalWrite(VIBRATION_PIN, LOW);

  // MPU6050
  Serial.println("Initializing MPU6050...");
  mpu.initialize();
  if (mpu.testConnection()) {
    Serial.println("✅ MPU6050 connected");
  } else {
    Serial.println("❌ MPU6050 connection failed");
  }

  // MAX30102
  Serial.println("Initializing MAX30102...");
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("❌ MAX30102 not found. Check wiring.");
    while (1);
  }
  Serial.println("✅ MAX30102 connected");

  particleSensor.setup();
  particleSensor.setPulseAmplitudeRed(0x0A);  // low red (not used, just keeps LED alive)
  particleSensor.setPulseAmplitudeIR(0x1F);   // IR for HRV

  Serial.println("\n🚀 System Ready — Place finger on sensor...\n");
  
  // Startup vibration pattern (quick double-pulse)
  digitalWrite(VIBRATION_PIN, HIGH);
  delay(100);
  digitalWrite(VIBRATION_PIN, LOW);
  delay(100);
  digitalWrite(VIBRATION_PIN, HIGH);
  delay(100);
  digitalWrite(VIBRATION_PIN, LOW);
}

// ===================================================
// LOOP
// ===================================================
void loop() {
  // --- Check RCWL radar (independent of heart rate) ---
  bool rcwlTriggered = checkRCWL();
  
  // --- Read MAX30102 ---
  long irValue = smoothIR(particleSensor.getIR());

  // Basic finger-on check
  if (irValue < 50000) {
    // Still check for accident and RCWL even without finger on sensor
    float motion = getMotion();
    bool accident = detectAccident(motion);
    
    if (accident || rcwlTriggered) {
      handleAlerts(accident, false, rcwlTriggered);
    }
    
    delay(500);
    return;
  }

  // Peak detection
  if (detectPeak(irValue)) {
  long now = millis();

  if (lastPeakTime > 0) {
    long ibi = now - lastPeakTime;

    Serial.print("IBI: ");
    Serial.print(ibi);
    Serial.print("ms | ");

    float rmssd = processIBI(ibi);

    if (rmssd > 0) {
      Serial.print("RMSSD: ");
      Serial.print(rmssd);
      Serial.print("ms | ");

      updateHRVBaseline(rmssd);

      if (baselineReady) {
        float motion = getMotion();
        updateMotionBaseline(motion);

        Serial.print("Motion: ");
        Serial.print(motion);
        Serial.print(" | ");

        checkSystem(rmssd, motion, rcwlTriggered);
      }
    }
  }

  // ✅ MOVE THIS HERE (after IBI calc)
  lastPeakTime = now;
}

  // Read MPU even when no peak (for accident detection at any moment)
  float motion = getMotion();
  bool accident = detectAccident(motion);
  
  if (accident || rcwlTriggered) {
    // Quick check without waiting for HRV
    handleAlerts(accident, false, rcwlTriggered);
  }

  delayMicroseconds(10000);  // ~100Hz sampling
}