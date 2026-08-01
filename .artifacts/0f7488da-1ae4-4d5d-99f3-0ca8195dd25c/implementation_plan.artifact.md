# Fix Build Errors and Potential Runtime Crashes

This plan addresses the current build failure (JDK misconfiguration and Gradle version mismatch) and several potential runtime crashes identified in the codebase.

## User Review Required

> [!IMPORTANT]
> **Manual IDE Setting Change Required**:
> The project's build is currently failing because Android Studio is configured to use a JDK path from "Processing.app" which does not exist or is invalid.
> To fix this, you MUST:
> 1. Go to **Settings** (or **Settings...** on macOS).
> 2. Navigate to **Build, Execution, Deployment** -> **Build Tools** -> **Gradle**.
> 3. Change the **Gradle JDK** to use the **Embedded JDK** (jbr).

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle-wrapper.properties](file:///Users/arthur/Documents/30%20Seconds%20Ago/gradle/wrapper/gradle-wrapper.properties)
- Downgrade Gradle from `9.6.1` to `8.10.2` for better compatibility with Android Gradle Plugin `8.7.3`.

---

### UI & Activity Safety

#### [MODIFY] [MainActivity.kt](file:///Users/arthur/Documents/30%20Seconds%20Ago/app/src/main/java/com/thirtysecondsago/thorreplay/MainActivity.kt)
- Add `runCatching` to `openClip` to prevent crashes if no app is installed to view MP4 files.
- Add null checks and more robust error handling in `getDisplayOptions`.

#### [MODIFY] [VibrationHelper.kt](file:///Users/arthur/Documents/30%20Seconds%20Ago/app/src/main/java/com/thirtysecondsago/thorreplay/util/VibrationHelper.kt)
- Add null checks for the `Vibrator` service to prevent `NullPointerException` on devices without vibration hardware.

---

### Service Safety

#### [MODIFY] [DisplayIndicatorService.kt](file:///Users/arthur/Documents/30%20Seconds%20Ago/app/src/main/java/com/thirtysecondsago/thorreplay/display/DisplayIndicatorService.kt)
- Add `runCatching` around `windowManager.addView` to prevent crashes if overlay permissions are revoked at runtime or if the window token is invalid.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` (after the user fixes the JDK in IDE) to verify the build.
- I will attempt a `gradle_build` again after the Gradle version downgrade.

### Manual Verification
- Deploy the app to a device and:
  1. Trigger a replay to verify `VibrationHelper` and `ReplayBufferService`.
  2. Open a saved clip to verify the `Intent` handling.
  3. Toggle the display indicator overlay.
