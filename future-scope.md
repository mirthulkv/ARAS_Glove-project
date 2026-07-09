# Future Scope / Roadmap

The current prototype validates the core concept — sensor-fused drowsiness detection, radar-based collision sensing, IMU-based accident detection, and Bluetooth-to-SMS emergency alerting — at **TRL 4** (lab-validated). The following areas are identified for further development:

## 1. Power & Portability
- Replace USB/laptop power with a rechargeable LiPo battery and onboard charging/regulation circuit for true wearable independence.
- Optimize firmware sampling and radio usage for lower power draw; evaluate migrating from Bluetooth Classic (SPP) to **Bluetooth Low Energy (BLE)**.

## 2. Location & Connectivity
- Add an onboard GPS module so the glove itself can supply location data, removing dependence on the phone's GPS being active and accurate.
- Explore cellular (GSM/LTE-M) fallback for SOS transmission in case the paired phone is unreachable.

## 3. Software / App
- Migrate the full Bluetooth + SOS pipeline from `MainActivity` into `SOSForegroundService` so monitoring continues reliably when the app is backgrounded or the screen is off (the service scaffold is already included as `SosService.kt`).
- Build a live dashboard in the Android app: real-time heart rate/HRV trend, ride history/logs, and an emergency-contact management screen (currently a single hardcoded number).
- Add configurable alert thresholds (HRV drop %, acceleration threshold, radar cooldown) exposed via the app UI instead of firmware constants.

## 4. Sensing & Algorithms
- Explore on-device machine learning (e.g., TinyML on ESP32) for more robust drowsiness and accident classification beyond fixed thresholds.
- Add sensor-fusion confidence scoring to reduce false positives/negatives further, especially for the accident-detection threshold.
- Extend RCWL-0516 radar coverage to a genuinely bidirectional (front + rear) sensor pair, as originally scoped in the invention disclosure.

## 5. Mechanical / Field Readiness
- Waterproof/ruggedized enclosure suitable for real riding conditions (rain, vibration, temperature extremes).
- Ergonomic redesign for long-duration wearability and glove sizing.
- On-road field trials to validate against the current indoor bench-test results (see [testing-results.md](testing-results.md)).

## 6. Broader Integration
- GPS-based geofencing/route tracking for fleet or delivery-rider safety use cases.
- Integration with smart-city/ITS infrastructure for aggregated road-hazard reporting.
- Multi-user/family "watch" mode where SOS alerts can notify multiple contacts or a monitoring dashboard.
