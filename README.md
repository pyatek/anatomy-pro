This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
## Build notes

Do not run `./gradlew updateDaemonJvm`. It writes `gradle/gradle-daemon-jvm.properties`,
whose Daemon JVM criteria make Android Studio sync fail instantly and silently on
macOS 26 with `Service 'SystemInfo' is not available`. The IDE runs the Gradle Tooling
API client with native-platform services disabled, and the criteria file sends the
launcher into toolchain provisioning, which needs those services. The CLI is unaffected,
so the failure looks like a broken project. Set the daemon JVM through Android Studio's
Gradle JDK setting instead.

Shared build configuration lives in `build-logic/` as the `anatomypro.kmp.library` and
`anatomypro.kmp.compose` convention plugins. Module build files should stay short; if one
grows past a handful of lines, the configuration probably belongs in a convention plugin.
