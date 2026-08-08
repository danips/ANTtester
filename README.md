# ANTtester
ANT Tester (`com.quantrity.anttester`) source code.

With this application you can check the different ANT+™ capabilities your device supports.
It also offers links to download/uninstall the different ANT+™ packages. ANTtester
is the diagnostic app; ANT Radio Service, ANT USB Service, and ANT+ Plugins are
separate optional dependencies supplied by ANT Wireless and are not bundled as
installable app components.

## Installation and releases

The old Google Play listing is no longer advertised because it is not currently
available. Until a signed public release is restored, build from source. The
chosen future distribution route is a signed GitHub Release; each release must
identify the exact source tag, Android/API support, ANT service versions tested,
changelog, and SHA-256 checksum. Do not install the unsigned CI build; it is for
build verification only.

Release checklist:

1. Create a version tag matching `versionName` and increment `versionCode`.
2. Verify the bundled AAR against `app/libs/SHA256SUMS` and retain the license
   notices from the ANT Android SDK.
3. Run the release build, lint, unit tests, and device smoke tests with both a
   built-in ANT route and an ANT USB adapter where available.
4. Sign through CI/release secrets, publish the APK and SHA-256 checksum with
   release notes, and retain the R8 mapping file privately.

The supported source compatibility floor remains Android API 7. The current
release is built with JDK 17+, Android SDK 36, and ANTLib 4.16.0. The app's ANT
Radio Service adapter inspection requires a service version that supports the
ANTLib adapter-provider API.

## Build

The project requires JDK 17 or newer and Android SDK Platform 36. Use the
committed Gradle Wrapper:

```bash
./gradlew assembleRelease
./gradlew lintDebug
./gradlew testDebugUnitTest
```

The release build is minified with R8. Test it with an ANT-capable device and
with an ANT USB adapter before publishing. Increment `versionCode` and
`versionName` for every Play release.

The bundled `android_antlib_4-16-0.aar` is ANTLib 4.16.0 from the ANT Android
SDK. It communicates with the separately installed ANT Radio Service and ANT
USB Service when those are available on the device.
