# Changelog

## 2.02

Changes since v1.27.

- Redesigned the diagnostics screen with dark mode, a refresh button, and a layout that adapts to narrow screens.
- Shows whether ANT is ready, detected, or needs setup. Service details distinguish missing, disabled, and outdated components. Refreshes when USB devices attach or detach and when the app resumes.
- Detects recognized ANT USB2 and ANTUSB-m sticks without ANT Radio Service or ANT USB Service. With USB access, it reads the firmware directly from sticks that answer the query. A single stick displays one short firmware value when the readings agree or only one source supplies a version.
- Reads built-in firmware through a compatible ANT HAL when ANT Radio Service is missing or disabled, and through the legacy interface on supported devices running older versions of ANT Radio Service.
- Groups adapters by type and keeps unclassified adapters visible in their own row. A classified adapter or a separate firmware check can establish readiness. Built-in firmware and HAL rows are hidden when they have no relevant information.
- Updated the build to Android SDK 37.2 with target API 37, Android Gradle Plugin 9.5.2, and Gradle 9.6.0.
