# Changelog

## 2.02 (2026-11-22)

- Refreshed the diagnostics screen with dark mode, a clearer status layout, and a layout that adapts to narrow screens.
- Shows external ANT adapters separately from built-in ANT. Recognized ANT USB2 and ANTUSB-m sticks can be detected without ANT services. With USB access, ANTtester can also read the version directly from sticks that answer the firmware query.
- Reads the built-in ANT firmware through a compatible Android ANT HAL when ANT Radio Service is absent. Android may ask for permission to use the ANT hardware.
- Hides built-in firmware and HAL rows when there is nothing relevant to report. Keeps detected adapters visible when a firmware read is unavailable.
- Compiles against Android SDK 37.2 and targets Android 17 (API 37). Updated the build tooling to Android Gradle Plugin 9.5.2 and Gradle 9.6.0.
