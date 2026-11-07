# Implementation Plan: Launcher Detection, File Explorer & V1/V2 Signing

This plan addresses the implementation of reliable launcher activity detection, an MT-Manager style APK file explorer, and a robust V1+V2 APK Signing engine.

## User Review Required

> [!IMPORTANT]
> **APK Signing Dependency:** To achieve 100% reliable V2 (and V3) APK Signature Scheme signing on-device, we will leverage Google's official `com.android.tools.build:apksig` library. I have verified that this library compiles successfully in your environment.

## Proposed Changes

### Feature 1: Reliable Launcher Auto-Detection & File Explorer

#### [MODIFY] [BinaryAxmlParser.kt](file:///C:/Users/YASEEN/Desktop/BlackCPatcher/app/src/main/java/com/blackcpatcher/data/apk/BinaryAxmlParser.kt)
- **Fix:** If the `LAUNCHER` intent is found inside an `<activity-alias>`, the parser currently records the *alias* name instead of the actual backing `.smali` class. I will update the parser to extract the `targetActivity` attribute from aliases, guaranteeing the correct entry point is hooked.

#### [NEW] [ApkFileExplorerDialog.kt](file:///C:/Users/YASEEN/Desktop/BlackCPatcher/app/src/main/java/com/blackcpatcher/ui/components/ApkFileExplorerDialog.kt)
- **UI Component:** A custom Jetpack Compose interactive file explorer mimicking MT Manager.
- **Functionality:** It will construct a virtual directory tree. The root shows the APK's root directory (e.g., `classes.dex`, `AndroidManifest.xml`). Clicking `classes.dex` virtually "mounts" it and allows the user to browse through the package structures (e.g., `com/` -> `example/` -> `MainActivity.smali`).
- **Action:** Clicking a `.smali` file will instantly return its relative path and populate the "Manual .smali Path" override text field.

#### [MODIFY] [MainDashboard.kt](file:///C:/Users/YASEEN/Desktop/BlackCPatcher/app/src/main/java/com/blackcpatcher/ui/components/MainDashboard.kt)
- **Integration:** Add a "Browse Files" OutlinedButton next to the "Detected Launcher" label. Clicking it will trigger the `ApkFileExplorerDialog` using the data from `targetReport`.

### Feature 2: Robust V1+V2 APK Signature Handling

#### [MODIFY] [ApkSignerManager.kt](file:///C:/Users/YASEEN/Desktop/BlackCPatcher/app/src/main/java/com/blackcpatcher/data/apk/ApkSignerManager.kt)
- **Fix:** Remove the incomplete manual `ZipOutputStream` V1 signer.
- **Implementation:** Utilize `com.android.apksig.ApkSigner` to perform a comprehensive signing process.
- **Output:** The final APK will receive valid V1, V2, and V3 signature blocks using the `AndroidKeyStore` debug key. This prevents Android 11+ `INSTALL_PARSE_FAILED_NO_CERTIFICATES` errors and shows as fully signed in MT Manager.

#### [MODIFY] [ApkSignerUtil.kt](file:///C:/Users/YASEEN/Desktop/BlackCPatcher/app/src/main/java/com/blackcpatcher/data/apk/ApkSignerUtil.kt)
- Refactor to pass the raw APK directly to `ApkSignerManager.kt` using `apksig`'s alignment and signing features.

## Verification Plan

### Automated Tests
- Run `gradle_build("app:assembleDebug")`
- Verify successful compilation with `apksig`.

### Manual Verification
- **File Explorer:** Select a target APK, click "Browse Files", navigate into `classes.dex`, and select a `.smali` file. Ensure the text box populates correctly.
- **Signing:** Build and sign an APK. Verify installation on the emulator/device works perfectly without verification errors.