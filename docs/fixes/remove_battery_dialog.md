# Remove Battery Optimization Dialog

**Date:** 2026-04-28
**File:** `app/src/main/java/com/gmwapp/hima/utils/CallPermissionHelper.kt`

## What was fixed
The "Reliable incoming calls" battery-optimization dialog was popping up for male/female users. Removed it while keeping Android 14+ full-screen-intent prompting intact.

## Changes
- Deleted `maybePromptBatteryOptimizationExemption()`.
- Removed its call from `maybePromptCallReliabilityPermissions()`.
- Cleaned unused imports: `PowerManager`, `androidx.appcompat.app.AlertDialog`.
- Removed unused constant: `KEY_BATTERY_OPT_PROMPTED`.
- Updated KDoc to describe only FSI behavior.

## Untouched
- `maybePromptFullScreenIntentPermission()` — still runs daily on Android 14+.
- `MainActivity.kt` call site at line 349.

## Verify
- `./gradlew :app:compileProductionDebugKotlin`
- Launch app as male/female user — dialog should no longer appear.
