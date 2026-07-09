# Invention Disclosure Summary

*Condensed from the formal Invention Disclosure Format (IDF-B) filed for this project.*

## Title
Smart Two-Wheeler Safety System with Haptic Feedback and Emergency SOS

## Field
Electronics Engineering — Wearable IoT / Embedded Safety Systems

## Problem Being Addressed
Two-wheeler riders are among the most vulnerable road users due to the absence of protective structures and higher exposure to traffic hazards. Advanced Driver Assistance Systems (ADAS) are well developed for four-wheelers but are difficult to adapt to motorcycles due to cost, space constraints, and dynamic instability. Existing two-wheeler-focused solutions typically address only **one** dimension of safety at a time:

- Helmet-based accident detection
- Vision/radar-based frontal collision warning
- Standalone wearable health monitoring

These systems lack integration, provide limited real-time feedback, and often rely on visual or audio alerts that can distract the rider.

## Summary of the Invention
A low-cost, wearable smart safety system for two-wheeler riders, implemented on an ESP32 with a MAX30102 pulse oximeter/heart-rate sensor, MPU-6050 IMU, and RCWL-0516 radar module, all integrated into a glove. The system performs three primary safety functions — rider pulse monitoring for drowsiness detection, front/rear collision proximity detection, and automatic emergency SOS transmission — with real-time dual-channel haptic feedback and automatic SMS alerts sent via a Bluetooth-connected mobile device.

## Key Novel Aspects

1. **Integrated multi-modal safety architecture** — combines physiological monitoring, bidirectional collision detection, and emergency SOS alerting in a single unified platform, rather than as isolated point solutions.
2. **Wearable glove-based implementation** — rather than helmet- or vehicle-mounted, enabling direct pulse contact, immediate haptic delivery, and improved ergonomics.
3. **Dual-mode haptic feedback** — two independently addressable coin vibration motors, one for drowsiness alerts and one for collision/proximity alerts, avoiding visual/audio distraction.
4. **Bidirectional collision detection capability** — radar-based sensing designed to cover both front and rear proximity, versus most prior art's front-only detection.
5. **Integrated rider health monitoring with safety response** — MAX30102-based pulse/HRV monitoring is tightly coupled with the alert engine (rather than being a standalone monitoring feature) to enable early fatigue detection and timely warnings.

## Prior Art Landscape (Referenced Publications & Patents)

### Publications
| Publication | Relevance |
|---|---|
| *Design of an IoT-Based Smart Helmet for Accident Detection and Notification* — IEEE Sensors Journal, 2020 | Supports the SOS alert feature; this invention extends it with vehicle/rider-mounted detection + haptic feedback |
| *Real-Time Collision Detection and Warning System for Motorcycles Using Embedded Systems* — IEEE Access, 2021 | Aligns with front collision detection; extended here with rear detection + multimodal alerts |
| *Wearable Haptic Feedback Systems for Driver Assistance* — J. Intelligent Transportation Systems, 2019 | Basis for the haptic feedback subsystem, adapted for two-wheeler riders |
| *IoT-Based Health Monitoring System Using Pulse Sensors* — Int'l J. Healthcare Technology, 2022 | Basis for pulse monitoring, here integrated with safety alerts |

### Patents
| Patent | Relevance |
|---|---|
| CN 110234519 A (2019) — Smart Helmet for Motorcycle Riders with Accident Detection | Supports SOS mechanism; extended with vehicle-based collision detection + haptics |
| US 10,765,432 B2 (2020) — Motorcycle Collision Warning System Using Radar Sensors | Aligns with front detection; extended to bidirectional + haptic alerts |
| EP 3 456 789 A1 (2018) — Wearable Haptic Feedback Device for Navigation and Safety | Basis of the haptic mechanism, adapted for real-time hazard alerts |
| IN 202141012345 A (2021) — Vehicle Emergency Alert System with Automatic Accident Detection | Basis of SOS feature; extended with integrated rider health monitoring |
| US 11,234,567 B2 (2022) — Wireless Health Monitoring System with Heart Rate Sensor Integration | Basis of pulse monitoring; combined here with vehicle safety + emergency alerts |

## Technology Readiness Level
**TRL 4** — Technology validated in a lab environment (bench-tested prototype, indoor validation).

## Aspects Identified for IP Protection
1. Integrated multi-sensor safety architecture (ESP32 + physiological + motion + proximity sensing)
2. Wearable glove-based design for direct pulse sensing and immediate feedback
3. Dual-mode haptic feedback mechanism (drowsiness vs. collision channels)
4. Integrated health monitoring tightly coupled with safety response logic
5. Bidirectional (front/rear) collision detection
6. Automatic accident detection and SOS communication pipeline

## References

**Publications**
1. Kumar, S., & Verma, R. (2021). Design of Smart Helmet with Accident Detection and Notification System. *IEEE Sensors Journal*, 21(8), 9456–9463.
2. Patel, A., & Shah, D. (2022). Real-Time Motorcycle Collision Detection and Warning System Using Embedded Sensors. *IEEE Access*, 10, 33452–33461.
3. Lee, J., & Park, H. (2020). Wearable Haptic Feedback Systems for Driver Assistance Applications. *Journal of Intelligent Transportation Systems*, 24(5), 412–420.
4. Singh, P., & Nair, V. (2023). IoT-Based Health Monitoring System Using Pulse Sensors and Wireless Communication. *International Journal of Healthcare Technology*, 18(2), 89–97.
5. Zhang, Y., & Liu, X. (2022). Integrated Sensor-Based Safety System for Two-Wheeler Applications. *Procedia Engineering*, 310, 155–162.

**Patents**
1. US 10,765,432 B2 — Motorcycle Collision Warning System Using Radar Sensors, 2020.
2. CN 110234519 A — Smart Helmet for Motorcycle Riders with Accident Detection and Communication System, 2019.
3. EP 3 456 789 A1 — Wearable Haptic Feedback Device for Navigation and Safety, 2018.
4. IN 202141012345 A — Vehicle Emergency Alert System with Automatic Accident Detection, 2021.
5. US 11,234,567 B2 — Wireless Health Monitoring System with Heart Rate Sensor Integration, 2022.
