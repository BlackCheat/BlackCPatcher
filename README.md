# BlackCPatcher — APK Inspection Studio

A Kotlin / Jetpack Compose Android Studio project for **read-only inspection** of APKs that you own or are authorized to assess.

## Implemented in this build
- Material 3 dark UI with Automatic / Manual workflow selection.
- Android Storage Access Framework APK pickers (no broad storage permission).
- Read-only APK archive inspection on a background coroutine.
- Package name, version, min SDK and target SDK metadata (via Android PackageManager).
- DEX inventory (`classes.dex`, `classes2.dex`, etc.).
- Native `.so` inventory grouped by ABI; selectable display filter.
- Manifest-derived requested permissions, activities, services, receivers and providers when exposed by Android's archive parser.
- SHA-256 fingerprint and archive-entry count.
- Per-file error reporting; selected source APKs are copied only to a temporary cache file and deleted after inspection.

## Not implemented / intentionally out of scope
- No DEX or Smali injection, mod-menu/game patching, anti-cheat bypass, or third-party game modification.
- No manifest rewriting, library replacement, APK rebuilding or APK signing in this build.
- Android's `PackageManager.getPackageArchiveInfo` does not reliably expose manifest intent-filter declarations for an uninstalled APK; launcher activity is therefore explicitly marked unavailable instead of guessed.
- The ABI preference filters the report only; it does not remove or rewrite libraries.

## Build
- Android Studio (recent stable release)
- JDK 17
- Android SDK Platform 35
- Gradle sync using the Android Gradle Plugin version declared in `build.gradle.kts`

Open the `BlackCPatcher` project directory and run the `app` configuration on an Android 8.0+ device/emulator. This source bundle does not include a Gradle wrapper; Android Studio can import the Gradle project and provision the matching Gradle distribution.

## Reliability notes
- Inspection is read-only. Original APK URIs are never written to.
- APK contents are streamed to a uniquely named temporary file in app cache and deleted in a `finally` block.
- A successful static review is not equivalent to a device build or runtime test. Build and test with Android Studio on a configured SDK/device before release.
