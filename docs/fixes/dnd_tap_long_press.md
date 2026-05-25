# DND Tap + Long-Press Behavior

**Date:** 2026-04-28

## What was fixed
Tapping the DND switch used to immediately open a duration dialog. Changed it so a tap is a one-action toggle (1 hour on, or off), and long-press opens the duration picker.

## New behavior
- Tap (DND OFF) -> enables DND for **1 hour**, no dialog.
- Tap (DND ON) -> disables DND immediately.
- Long-press switch *or* the DND card row -> duration picker (`1 Hour` / `2 Hours` / `4 Hours`).
- Creator/female guard (`dnd_requires_calls_off`) still applies on both tap and long-press.

## Changes
- `app/src/main/java/com/gmwapp/hima/utils/DndController.kt`
  - Tap path now calls `callToggleDndApi(1, 1)` directly when enabling; no dialog.
  - Added `setOnLongClickListener` on `switchDnd` and the optional `cvDnd` card row -> opens duration picker.
  - Extracted `canEnableDndOrToast()` so the audio/video guard is shared between tap and long-press.
  - Added `cvDnd: View? = null` constructor param.
  - Added `DEFAULT_TAP_DURATION_HOURS = 1` constant.
- `app/src/main/res/layout/dialog_dnd_duration.xml`
  - `btn_dnd_3h` -> `btn_dnd_2h` (text `2 Hours`).
  - `btn_dnd_24h` -> `btn_dnd_4h` (text `4 Hours`).
  - `btn_dnd_1h` and cancel button unchanged.
- `app/src/main/res/values/strings.xml`
  - `dnd_duration_3h` -> `dnd_duration_2h` (`2 Hours`).
  - `dnd_duration_24h` -> `dnd_duration_4h` (`4 Hours`).
- `app/src/main/java/com/gmwapp/hima/fragments/ProfileFragment.kt`
  - Passes `cvDnd = binding.cvDnd` to `DndController`.
- `app/src/main/java/com/gmwapp/hima/fragments/ProfileFemaleFragment.kt`
  - Passes `cvDnd = binding.cvDnd` to `DndController` (creator guard still on).

## Skipped
- `Until I turn it off` option - backend `durationHours` semantics for indefinite DND not confirmed; plan said to ship finite durations first.

## Verify
- Tap DND while OFF -> enables for 1h, no dialog.
- Tap DND while ON -> disables.
- Long-press DND while OFF -> picker opens with 1h / 2h / 4h.
- Long-press DND while ON -> picker opens (extends/changes duration).
- Female/creator profile: with audio or video status ON, tap **and** long-press should both show the `dnd_requires_calls_off` toast and not enable DND.
