# Individual Call Connecting Screen Update

## Summary
Updated the individual call connecting screen (`activity_male_call_connecting.xml`) to match the modern design of the random call connecting screen.

## Changes Made

### 1. Layout Design (`activity_male_call_connecting.xml`)

#### Background
- **Before**: Gradient background (`@drawable/bg_gradiend`)
- **After**: Clean white background (`@drawable/bg_white`)

#### New UI Elements Added
1. **Status Bar Padding**: Added for proper spacing at the top
2. **Connection Status Card**: Shows "Connecting" text with animated dots
3. **Animated Connecting Dots**: Three dots with pulsing animation
4. **Modern Avatar Containers**: 
   - Top avatar (receiver) with wave ring animation
   - Bottom avatar (user) with pulse ring animation
   - MaterialCardView with rounded corners and elevation
5. **Connection Line**: Subtle vertical line connecting the two avatars
6. **Animated Double Arrow**: Smaller, centered icon between avatars
7. **Info Container**: Clean text layout for connection messages
8. **Progress Container**: Simplified progress bar with descriptive text
9. **Cancel Button**: Clickable text button at the bottom

#### Layout Structure
```
ConstraintLayout
├── Status Bar Padding
├── Title (Audio/Video Session)
├── Connection Status Card
│   ├── Connection Status Text
│   └── Connecting Dots (animated)
├── Top Avatar Container
│   ├── Wave Ring (animated)
│   └── MaterialCardView
│       └── Receiver Image
├── Connection Line
├── Double Arrow Icon (animated)
├── Bottom Avatar Container
│   ├── Pulse Ring (animated)
│   ├── MaterialCardView
│   │   └── User Image
│   └── "You" Badge
├── Info Container
│   ├── Connection Message
│   └── User Name (hidden by default)
├── Progress Container
│   ├── Progress Text
│   ├── Progress Bar
│   └── Timer (hidden by default)
└── Cancel Button
```

### 2. Activity Code (`MaleCallConnectingActivity.kt`)

#### New Methods Added

1. **`startSimpleAnimations()`**
   - Handles initial fade-in animation for the title
   - Triggers the connecting dots animation

2. **`startConnectingDotsAnimation()`**
   - Animates the three dots with alternating opacity
   - Creates a smooth pulsing effect
   - Runs every 500ms while the activity is active

#### Updated Methods

1. **`initUI()`**
   - Changed from loading GIF to loading SVG for the double arrow
   - Added call to `startSimpleAnimations()`
   - Added click listener for the cancel button

#### Key Changes
- **Double Arrow**: Changed from `double_arrow_gif` to `double_arrow_svg` for better performance
- **Cancel Button**: Now functional with proper back press handling
- **Animations**: Added smooth fade-in and dot pulsing animations

## Design Features

### Visual Improvements
1. **Clean White Background**: More modern and professional
2. **Subtle Animations**: Wave rings and pulse effects around avatars
3. **Connecting Dots**: Animated dots to show active connection attempt
4. **Better Spacing**: Improved margins and padding throughout
5. **Material Design**: MaterialCardView with shadows for depth
6. **Minimalist Icons**: Smaller, more subtle double arrow icon

### User Experience Improvements
1. **Cancel Button**: Easy way to cancel the connection
2. **Progress Feedback**: Clear progress bar with descriptive text
3. **Connection Status**: Always visible at the top
4. **Smooth Transitions**: Fade-in animations for better feel

## Compatibility

### Maintained Elements
- All existing IDs are preserved for backward compatibility
- Progress bar functionality unchanged
- Image loading logic remains the same
- Hidden elements for compatibility:
  - `circular_progress`
  - `wave_ring_2`
  - `wave_ring_3`
  - `fl_connection_animation`

## Files Modified
1. `/app/src/main/res/layout/activity_male_call_connecting.xml`
2. `/app/src/main/java/com/gmwapp/hima/agora/male/MaleCallConnectingActivity.kt`

## Testing Notes
- No linter errors detected
- All view IDs preserved for existing code references
- Cancel button functionality tested with back press handling
- Animation performance optimized with proper cleanup

## Design Consistency
The individual call connecting screen now matches the design pattern of:
- Random call connecting screen (`activity_agora_random_call.xml`)
- Modern call UI standards
- App-wide design language

