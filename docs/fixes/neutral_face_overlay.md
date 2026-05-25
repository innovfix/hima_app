# Gender-Neutral Face Detection Overlay

**Date:** 2026-04-28

## What was fixed
The face-not-detected overlay looked female-coded (narrow neck, collar curve, person-face icon). Made the shared overlay gender-neutral so it works for both male and female callers without separate UIs.

## Changes
- `app/src/main/java/com/gmwapp/hima/views/DottedFaceGuideView.kt`
  - Replaced the bust drawing (head + narrow neck + collar arc + sloped shoulders) with a neutral oval head guide and a single symmetric shoulder arc.
  - No collar/neck styling; head oval is centered with balanced proportions.
- `app/src/main/res/drawable/ic_face_scan_frame.xml` (new)
  - Neutral viewfinder/scan-frame icon (four corner brackets + simple face oval).
- `app/src/main/res/layout/overlay_face_detection.xml`
  - Swapped bottom card icon from `baseline_face_24` to `ic_face_scan_frame`.
  - Secondary text: `Your video is blurred. To continue, show your face` -> `Keep your face inside the guide to continue`.

## Untouched
- Title text (`Face Not Detected`) and primary copy (`Show your face`) were already correct.
- Overlay show/hide logic in the four call activities — they all consume the shared `overlay_face_detection.xml`, so the change propagates automatically.

## Affected screens (shared overlay)
- `FemaleVideoCallingActivity`
- `FemaleAudioCallingActivity`
- `MaleVideoCallingActivity`
- `MaleAudioCallingActivity`

## Verify
- Trigger face-not-detected in all four call flows.
- Confirm the guide looks neutral (oval head + symmetric shoulders, no collar).
- Confirm the bottom icon is the viewfinder frame.
- Confirm secondary text reads `Keep your face inside the guide to continue`.
- Confirm overlay still hides automatically when a face is detected.
