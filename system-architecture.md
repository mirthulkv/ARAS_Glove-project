# System Architecture

## Mechanical Design

- **Structure:** The system is integrated into a wearable glove, housing electronics while preserving comfort, flexibility, and ease of use.
- **Sensor placement:**
  - Pulse sensor (MAX30102) — in contact with the rider's finger for accurate heart-rate measurement.
  - Radar sensor (RCWL-0516) — oriented outward to detect nearby vehicles/objects.
  - IMU (MPU-6050) — centrally positioned to capture motion and impact data.
- **Haptic interface:** Two coin vibration motors embedded in the glove, each driven through a BC547 transistor with a flyback diode (1N4007) for inductive kickback protection, providing distinct tactile alerts for different conditions.

## Electronic System

| Subsystem | Component | Notes |
|---|---|---|
| Controller | ESP32 Dev Board | Central processing + integrated Bluetooth (Classic/SPP) |
| Health sensor | MAX30102 (HW-605 breakout) | I2C, measures IR/red PPG signal for HRV-based drowsiness detection |
| Motion sensor | MPU-6050 | I2C, 3-axis accelerometer + gyroscope for accident/motion detection |
| Proximity sensor | RCWL-0516 | Digital output, microwave Doppler radar for collision/proximity detection |
| Actuators | 2× coin vibration motors | Driven via GPIO → BC547 → motor, with flyback diode protection |
| Communication | ESP32 BluetoothSerial (SPP) | Bridges to Android app over the SPP UUID `00001101-0000-1000-8000-00805F9B34FB` |
| Power | USB (5V regulated) | Development/demo configuration; battery power is on the [roadmap](future-scope.md) |

### Pin Summary (ESP32)

| Signal | GPIO |
|---|---|
| I2C SDA (MAX30102 + MPU6050) | 21 |
| I2C SCL (MAX30102 + MPU6050) | 22 |
| RCWL-0516 digital output | 34 |
| Vibration Motor 1 (Drowsiness) | 25 |
| Vibration Motor 2 (Collision) | 26 |

*(See the annotated schematic in [`../images/circuit-diagram.svg`](../images/circuit-diagram.svg) for the full wiring, including the flyback diode / transistor driver stage for each motor.)*

## Software Control

- **Programming environment:** Arduino IDE / Embedded C++ for ESP32.
- **Control logic:** Sensor data is continuously acquired and processed in real time using threshold- and statistics-based algorithms (see [system-architecture → algorithms](../README.md#-how-the-safety-algorithms-work) in the main README) to detect drowsiness (HRV-based), proximity hazards (radar-based), and accidents (motion-based).
- **Alert dispatch:** Based on the detected condition(s), the firmware triggers the appropriate haptic pattern and, for accident-level events, transmits an SOS message over Bluetooth to the companion Android app.

## Modes of Operation

| Mode | Trigger | Response |
|---|---|---|
| **Drowsiness Monitoring** | Sustained RMSSD (HRV) drop + low hand motion | Haptic Motor 1 — 3 long pulses (500 ms on / 200 ms off) |
| **Collision Alert** | RCWL-0516 detects nearby object within cooldown window | Haptic Motor 2 — 2 short pulses (150 ms on / 100 ms off) |
| **Emergency SOS** | MPU-6050 acceleration spike (> 3g) — probable accident | Continuous 3-second vibration + `SOS:ACCIDENT` sent via Bluetooth → Android app fetches GPS → sends emergency SMS |

## System Operation Flow

1. Sensors continuously collect physiological and environmental data.
2. The ESP32 processes inputs and evaluates risk conditions each loop iteration (~100 Hz sampling).
3. The priority-based alert engine (Accident > Drowsiness > Object Detected) determines the active alert and triggers haptic feedback and/or the Bluetooth SOS message accordingly.
4. On the phone side, the Android app maintains a persistent Bluetooth socket, watches for the `"SOS"` substring in the incoming stream, and — on detection — retrieves a GPS fix and sends an emergency SMS with a Google Maps link to the preconfigured contact.

See [`../images/system-flowchart.svg`](../images/system-flowchart.svg) for the full decision-flow diagram.
