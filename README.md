# ANTtester
ANT Tester (`com.quantrity.anttester`) source code.

With this application you can check the different ANT+™ capabilities your device supports.
It also offers links to download/uninstall the different ANT+™ packages. ANTtester
is the diagnostic app; ANT Radio Service, ANT USB Service, and ANT+ Plugins are
separate optional dependencies supplied by ANT Wireless and are not bundled as
installable app components.

## Installation and releases

The [GitHub releases](https://github.com/danips/ANTtester/releases) include the
tagged source and changelog. To install a version without a signed APK asset,
build and sign the app locally. The unsigned release build is for verification.

Release checklist:

1. Update `versionName`, `versionCode`, and the changelog, then tag the tested
   release commit.
2. Verify the bundled AAR against `app/libs/SHA256SUMS` and retain the license
   notices from the ANT Android SDK.
3. Run the release build, lint, unit tests, and device smoke tests with both a
   built-in ANT route and an ANT USB adapter where available.
4. Sign through CI/release secrets, publish the APK and SHA-256 checksum with
   release notes, and retain the R8 mapping file privately.

The app's minimum SDK is Android API 7. The app's ANT
Radio Service adapter inspection requires a service version that supports the
ANTLib adapter-provider API.

## Build

The project requires JDK 17 or newer and Android SDK Platforms 37.0 and 37.2.
Use the committed Gradle Wrapper:

```bash
./gradlew verifyAntLibChecksum assembleRelease
./gradlew lintDebug
./gradlew testDebugUnitTest
```

The release build is minified with R8.

The bundled `android_antlib_4-16-0.aar` is ANTLib 4.16.0 from the ANT Android
SDK. It communicates with the separately installed ANT Radio Service and ANT
USB Service when those are available on the device.
