# Testing & Experimental Results

## Experimental Setup

- ESP32 programmed using Arduino IDE for real-time processing.
- Sensors used for physiological monitoring (MAX30102), motion detection (MPU-6050), and proximity sensing (RCWL-0516).
- Two coin vibration motors for haptic alerts.
- Powered via USB connection from a laptop (5V regulated supply).
- Wearable glove prototype used for integration testing.
- Testing tools: serial monitor logging, multimeter, and a mobile device for SOS/SMS verification.

## Tests Conducted

1. **Sensor Performance Test** — All sensors were tested individually and collectively, providing stable and accurate real-time data without noticeable noise.
2. **Drowsiness Detection Test** — Heart-rate-variability (RMSSD) variation was monitored against a rolling personal baseline; threshold-based conditions (>30% RMSSD drop + low motion) successfully triggered haptic alerts (Motor 1) with minimal delay.
3. **Collision Detection Test** — The radar sensor detected nearby objects from different directions, activating the second vibration motor promptly with low false-positive rate.
4. **Accident Detection and SOS Test** — Sudden motion changes were simulated using the IMU, resulting in accurate accident detection and successful SOS alert transmission via Bluetooth to a mobile device, followed by automatic SMS dispatch.
5. **System Stability Test** — The system was operated continuously to evaluate reliability. No overheating, power instability, or performance degradation was observed.

## Results Summary

| Parameter | Measured Result | Remarks |
|---|---|---|
| Heart rate monitoring accuracy | ±3 bpm | Within acceptable biomedical sensing range |
| Collision detection response time | < 1 second | Fast real-time detection |
| Haptic feedback response | Immediate (< 1 sec) | Clear and non-intrusive alerts |
| Bluetooth SOS → SMS transmission | Successful within 3–5 seconds | Reliable emergency communication |
| System stability | Continuous operation > 30 mins | No overheating or signal loss |
| Power supply stability | 5V (USB regulated) | Stable during full operation |
| Estimated system cost | ~₹1200–₹1500 (~$15–18) | Cost-effective compared to commercial ADAS systems |

## Observations

- The system demonstrated accurate multi-sensor data acquisition and real-time processing.
- Dual haptic feedback ensured clear, immediate, and non-distracting alerts distinguishable by pattern.
- Reliable detection of drowsiness, collision risks, and accident conditions was observed across repeated trials.
- Stable operation with low latency and consistent performance over extended runs.
- The compact wearable design proved efficient, low-cost, and suitable as a foundation for real-world two-wheeler safety applications.

## Conclusion

Experimental validation confirms the proposed system operates reliably under indoor bench-test conditions. The integration of physiological monitoring, collision detection, and emergency communication provides effective real-time safety assistance, while dual haptic feedback delivers immediate, non-distracting alerts. The compact wearable design and low component cost make it a strong candidate for further field testing and real-world deployment research.

> **Note:** All results above were gathered from **controlled indoor bench testing**, not on-road field trials. Real-world validation (vibration/road noise, weather, extended battery operation, and actual riding conditions) remains part of the [future work](future-scope.md).
