# Firmware — ARAS Glove (ESP32)

`ARAS_Glove_Firmware.ino` is the embedded firmware that runs on the glove-mounted ESP32. It performs sensor fusion across three inputs (PPG heart-rate sensor, IMU, radar), computes HRV-based drowsiness detection, arbitrates alerts by priority, drives the two haptic motors, and sends an SOS message over Bluetooth Serial when an accident is detected.

## Required Libraries

Install via Arduino IDE Library Manager (or PlatformIO):

| Library | Purpose |
|---|---|
| `Wire` | Built-in — I2C bus for MAX30102 + MPU-6050 |
| [`MPU6050`](https://github.com/ElectronicCats/mpu6050) (jrowberg/ElectronicCats i2cdevlib) | IMU driver |
| [`MAX30105`](https://github.com/sparkfun/SparkFun_MAX3010x_Sensor_Library) (SparkFun MAX3010x Sensor Library — also used for the MAX30102) | Pulse oximeter driver |
| `BluetoothSerial` | Built-in ESP32 core — Bluetooth Classic SPP |

**Board:** ESP32 Dev Module (any standard ESP32 dev board with I2C + digital GPIO exposed)

## Pin Mapping

| Signal | ESP32 GPIO | Notes |
|---|---|---|
| I2C SDA | 21 | Shared bus — MAX30102 + MPU-6050 |
| I2C SCL | 22 | Shared bus — MAX30102 + MPU-6050 |
| RCWL-0516 OUT | 34 | Digital input, radar motion detect |
| Vibration Motor 1 (Drowsiness) | 25 | Via BC547 transistor + 1N4007 flyback diode |
| Vibration Motor 2 (Collision) | 26 | Via BC547 transistor + 1N4007 flyback diode |

Full schematic: [`../images/circuit-diagram.svg`](../images/circuit-diagram.svg)

## Key Tunable Constants

These live at the top of `ARAS_Glove_Firmware.ino` and can be adjusted for calibration:

```cpp
#define IBI_BUFFER_SIZE     20     // Rolling window size for RMSSD computation
#define MIN_IBI_MS          400    // Reject implausible IBIs (>150 BPM)
#define MAX_IBI_MS          1500   // Reject implausible IBIs (<40 BPM)
#define ARTIFACT_THRESH     0.35   // Reject IBI if it deviates >35% from previous
#define MIN_PEAK_INTERVAL   400    // Minimum ms between accepted PPG peaks
#define BASELINE_SAMPLES    15     // Number of RMSSD readings to build personal baseline
#define IR_THRESHOLD        60000  // Minimum IR amplitude to accept a peak candidate
#define RCWL_COOLDOWN       2000   // ms cooldown between radar-triggered alerts
#define ALERT_DURATION      3000   // ms an alert is held before re-evaluation
```

Drowsiness threshold (RMSSD drop from baseline) and accident threshold (acceleration magnitude) are set in `isHRVDropping()` (30% drop) and `detectAccident()` (`> 3g`) respectively — see the function bodies for exact logic.

## Flashing

1. Install the [ESP32 board package](https://github.com/espressif/arduino-esp32) in Arduino IDE.
2. Select **Tools → Board → ESP32 Dev Module**.
3. Select the correct COM/serial port.
4. Open `ARAS_Glove_Firmware.ino`, install the libraries listed above, and click **Upload**.
5. Open the Serial Monitor at **115200 baud** to see live sensor/debug output (peak detection, RMSSD, motion, alert states).

On boot, the firmware:
- Initializes I2C and both sensors (logs ✅/❌ connection status per sensor over Serial).
- Starts Bluetooth Serial advertising as **`ARAS_GLOVE`** — this name must match `ESP32_DEVICE_NAME` in the Android app's `MainActivity.kt`.
- Plays a quick double-pulse startup vibration to confirm the motor driver circuit is working.

## Message Format (Bluetooth → Android App)

On accident detection, the firmware sends a single line over Bluetooth SPP:

```
SOS:ACCIDENT|TIME:<millis_since_boot>
```

The Android app's listener treats any inbound chunk containing the substring `"SOS"` (case-insensitive) as a trigger — see `MainActivity.kt` / `SOSForegroundService.kt` in [`../android-app/`](../android-app/).
