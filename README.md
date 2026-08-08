# ANTtester
ANT Tester ([com.quantrity.anttester](https://play.google.com/store/apps/details?id=com.quantrity.anttester)) source code

With this application you can check the different ANT+™ capabilities your device supports.
It also offers links to download/uninstall the different ANT+™ packages.

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
