# Gifts Feature - Complete Documentation

## Table of Contents
1. [Overview](#overview)
2. [Where Gifts Are Available](#where-gifts-are-available)
3. [How to Send Gifts (Male User)](#how-to-send-gifts-male-user)
4. [How Females Receive Gifts](#how-females-receive-gifts)
5. [Gift Cost & Coins System](#gift-cost--coins-system)
6. [Technical Implementation](#technical-implementation)
7. [Gift Animation Flow](#gift-animation-flow)
8. [API Integration](#api-integration)

---

## Overview

The Gifts feature allows **male users** to send virtual gifts to **female users** during active audio or video calls. Gifts are purchased using coins, which are deducted from the user's remaining call time balance. When a gift is sent, both users receive visual and audio feedback through animations and sounds.

### Key Features:
- ✅ Send gifts during **audio calls**
- ✅ Send gifts during **video calls**
- ✅ Real-time gift animations
- ✅ Sound effects when gift is sent/received
- ✅ Automatic coin deduction
- ✅ Multiple gift options with different prices

---

## Where Gifts Are Available

### 1. **Audio Calling Screen** (MaleAudioCallingActivity)
- **Location**: Gift icon button visible on the calling screen
- **When**: Available throughout the entire audio call session
- **Access**: Tap the gift icon to open gift selection bottom sheet

### 2. **Video Calling Screen** (MaleVideoCallingActivity)
- **Location**: Gift icon button visible during video calls
- **When**: Available throughout the entire video call session  
- **Access**: Tap the gift icon to open gift selection bottom sheet

### Important Notes:
- Gifts can **only be sent by male users**
- Gifts can **only be received by female users**
- Both users must be in an **active call**
- Sufficient **coins/call time** required to send gifts

---

## How to Send Gifts (Male User)

### Step-by-Step Guide:

#### Step 1: Access Gift Menu
1. During an active audio or video call
2. Locate the **Gift Icon** on the calling screen
3. Tap the gift icon to open the gift selection menu

#### Step 2: Browse Available Gifts
- A **bottom sheet** appears showing all available gifts
- Gifts are displayed in a **4-column grid layout**
- Each gift shows:
  - **Gift Image/Icon**
  - **Coin Cost** (number displayed below the gift)

#### Step 3: Select a Gift
1. Browse through the available gift options
2. Tap on the desired gift to select it
3. System automatically checks if you have enough coins

#### Step 4: Automatic Validation
The system checks:
- ✅ Your remaining call time/coins
- ✅ If coins are sufficient for the selected gift
- ✅ Active call status

**If Sufficient Coins:**
- ✅ Gift is sent immediately
- ✅ Toast message: "Gift Sent Successfully!"
- ✅ Gift animation plays on both screens
- ✅ Coins are deducted from your balance
- ✅ Bottom sheet closes automatically

**If Insufficient Coins:**
- ❌ Toast message: "You don't have enough coins to send this gift!"
- ❌ Gift is not sent
- ❌ Bottom sheet remains open
- ℹ️ You can select a different (cheaper) gift or add more coins

#### Step 5: Visual Feedback
After successful gift send:
- **On Male Screen**: 
  - Gift image animates from gift icon toward female user's avatar
  - Gift fades out after reaching the avatar
  - Sound effect plays
  
- **On Female Screen**: 
  - Notification received via FCM (Firebase Cloud Messaging)
  - Gift animation automatically triggered
  - Toast message: "Gift Received"
  - Sound effect plays

---

## How Females Receive Gifts

### Automatic Reception Process:

#### Step 1: Notification Received
- **FCM Push Notification** sent from male user's device
- Notification contains:
  - Gift icon/image URL
  - Sender information
  - Gift metadata

#### Step 2: Automatic Animation Trigger
The female's app automatically:
1. Detects the incoming gift notification
2. Extracts the gift icon URL from notification
3. Triggers the gift animation sequence

#### Step 3: Visual Animation
**Animation Sequence:**
1. **Gift image appears** on screen (alpha = 1.0, fully visible)
2. **Movement animation** (2000ms duration):
   - Gift moves from its starting position
   - Travels toward the female user's avatar
   - Smooth translation animation
3. **Fade out animation** (1000ms duration):
   - Gift fades from alpha 1.0 → 0.0
   - Occurs when gift reaches the avatar
4. **Cleanup**:
   - Gift becomes invisible
   - Reset to original position for next gift

#### Step 4: Audio Feedback
- **Gift sound effect** plays automatically
- Sound file: `res/raw/gift_tune.mp3`
- Played via `BaseApplication.playSendGiftSound()`

#### Step 5: User Notification
- **Toast message** displays: "Gift Received"
- Brief confirmation shown to female user
- No action required from female user

### Female User Experience:
- **100% Automatic** - No manual interaction needed
- **Seamless** - Animation plays without interrupting the call
- **Audio + Visual** feedback for better user experience
- **Multiple gifts** can be received in sequence

---

## Gift Cost & Coins System

### How Coins Work:

#### Coin Calculation During Calls:

**Audio Calls:**
- **Rate**: 10 coins per minute
- **Example**: 5 minutes = 50 coins

**Video Calls:**
- **Rate**: 60 coins per minute  
- **Example**: 5 minutes = 300 coins

#### Gift Purchase Validation:

```
Available Coins = Remaining Call Time × Coin Rate per Minute

Example (Audio Call):
- Remaining Time: 08:30 (8 minutes 30 seconds)
- Rounded Up: 9 minutes (if seconds ≥ 30)
- Available Coins: 9 × 10 = 90 coins

If Gift Costs 50 coins:
✅ 90 ≥ 50 → Gift can be sent

If Gift Costs 100 coins:
❌ 90 < 100 → Insufficient coins
```

### Coin Deduction Flow:

1. **Before sending gift**:
   - System fetches current remaining time from server
   - Calculates available coins
   - Validates against gift price

2. **After successful gift send**:
   - Coins are deducted from user's balance on server
   - New remaining time is fetched
   - Countdown timer is updated to reflect new balance
   - Both users see updated remaining time

3. **Remaining Time Update**:
   - Male user's countdown timer automatically updates
   - FCM notification sent to female user
   - Female user's countdown also updates

---

## Technical Implementation

### Architecture Components:

#### 1. **GiftBottomSheetFragment**
**Purpose**: Display available gifts in a bottom sheet dialog

**Key Features:**
- Extends `BottomSheetDialogFragment`
- 4-column grid layout (`GridLayoutManager`)
- Observes gift data via `GiftImageViewModel`
- Validates coin balance before sending
- Triggers gift sending via `GiftViewModel`

**Constructor Parameters:**
- `callType: String` - "audio" or "video"
- `femaleId: Int` - Receiver's user ID

**Main Functions:**
```kotlin
// Fetch and display gifts
giftImageViewModel.fetchGiftImages()

// Handle gift selection
giftAdapter = GiftAdapter(requireContext()) { giftData ->
    getRemainingTime(callType) { availableCoins ->
        if (availableCoins >= giftData.coins) {
            giftViewModel.sendGift(maleUserId, femaleId, giftData.id)
        } else {
            Toast: "Not enough coins"
        }
    }
}
```

#### 2. **GiftAdapter**
**Purpose**: Display individual gift items in RecyclerView

**Layout**: `adapter_gifts.xml`
- Gift image (ImageView)
- Coin amount (TextView)

**Data Binding:**
```kotlin
Glide.with(context)
    .load(gift.gift_icon)
    .into(holder.ivGift)
    
holder.tvCoinsAmount.text = gift.coins.toString()
```

#### 3. **GiftViewModel**
**Purpose**: Handle gift sending business logic

**Functions:**
- `sendGift(userId, receiverId, giftId)` - Initiates gift send
- Posts result to `giftResponseLiveData`
- Posts errors to `giftErrorLiveData`

#### 4. **GiftRepository**
**Purpose**: API communication layer

**Function:**
```kotlin
fun sendGift(
    userId: Int,
    receiverId: Int,
    giftId: Int,
    callback: NetworkCallback<SendGiftResponse>
)
```

#### 5. **GiftManager** (Utility)
**Purpose**: Manage gift data locally

**Functions:**
- `updateGifts(giftData: List<GiftData>)` - Store gifts
- `getGiftIconsWithCoins()` - Retrieve gift map

---

## Gift Animation Flow

### Male User Animation (Sender):

```kotlin
fun animateGift(image: String) {
    val giftImage = binding.ivGiftImage
    val femaleImage = binding.ivFemaleUser
    
    // 1. Reset and make visible
    giftImage.alpha = 1f
    giftImage.visibility = View.VISIBLE
    
    // 2. Load gift image
    Glide.with(this).load(image).into(giftImage)
    
    // 3. Play sound
    BaseApplication.getInstance()?.playSendGiftSound()
    
    // 4. Calculate destination position
    val femaleCenterX = /* calculate based on female avatar position */
    val femaleCenterY = /* calculate based on female avatar position */
    
    // 5. Animate movement (2 seconds)
    giftImage.animate()
        .x(femaleCenterX)
        .y(femaleCenterY)
        .setDuration(2000)
        .withEndAction {
            // 6. Fade out (1 second)
            giftImage.animate()
                .alpha(0f)
                .setDuration(1000)
                .withEndAction {
                    // 7. Cleanup
                    giftImage.visibility = View.INVISIBLE
                    giftImage.x = startX
                    giftImage.y = startY
                }
        }
}
```

### Female User Animation (Receiver):

**Trigger**: FCM notification observed via `FcmUtils.giftReceived`

```kotlin
fun observeGiftReceived() {
    FcmUtils.giftReceived.observe(this) { giftIcon ->
        if (giftIcon != null) {
            animateGift(giftIcon) // Same animation as male user
        }
        FcmUtils.cleargiftReceived()
    }
}
```

**Animation Process:**
1. Gift image moves from starting position to female avatar
2. Smooth translation over 2 seconds
3. Fades out over 1 second
4. Resets for next gift

---

## API Integration

### Endpoints:

#### 1. **Fetch Available Gifts**
**Endpoint**: `GET /api/gifts` (or similar)  
**Response**:
```json
{
  "success": true,
  "message": "Gifts fetched successfully",
  "data": [
    {
      "id": 1,
      "gift_icon": "https://example.com/gift1.png",
      "coins": 50,
      "created_at": "2024-01-01T00:00:00Z",
      "updated_at": "2024-01-01T00:00:00Z"
    },
    {
      "id": 2,
      "gift_icon": "https://example.com/gift2.png",
      "coins": 100,
      "created_at": "2024-01-01T00:00:00Z",
      "updated_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### 2. **Send Gift**
**Endpoint**: `POST /api/send-gift`  
**Request Body**:
```json
{
  "user_id": 123,
  "receiver_id": 456,
  "gift_id": 1
}
```

**Response**:
```json
{
  "success": true,
  "message": "Gift sent successfully",
  "data": {
    "id": 1,
    "gift_icon": "https://example.com/gift1.png",
    "coins": 50,
    "created_at": "2024-01-01T00:00:00Z",
    "updated_at": "2024-01-01T00:00:00Z"
  }
}
```

#### 3. **Get Remaining Time**
**Endpoint**: `POST /api/get-remaining-time`  
**Request Body**:
```json
{
  "user_id": 123,
  "call_type": "audio"
}
```

**Response**:
```json
{
  "success": true,
  "message": "Remaining time fetched",
  "data": {
    "remaining_time": "08:30"
  }
}
```

### FCM Notification:

**Notification Structure** (sent by male user to female user):
```kotlin
fcmNotificationViewModel.sendNotification(
    senderId = maleUserId,
    receiverId = femaleUserId,
    callType = giftIcon, // Gift icon URL
    channelName = channelName,
    message = "giftSent"
)
```

**Female Receives**:
- Notification with `message = "giftSent"`
- `callType` field contains the gift icon URL
- Triggers `FcmUtils.giftReceived` LiveData
- Animation automatically plays

---

## Data Models

### GiftData
```kotlin
data class GiftData(
    val id: Int,                    // Unique gift ID
    val gift_icon: String,          // Image URL
    val coins: Int,                 // Cost in coins
    val updated_at: String,         // Last update timestamp
    val created_at: String          // Creation timestamp
)
```

### SendGiftResponse
```kotlin
data class SendGiftResponse(
    val success: Boolean,           // API success status
    val message: String,            // Response message
    val data: GiftData?            // Gift data
)
```

### GiftImageResponse
```kotlin
data class GiftImageResponse(
    val success: Boolean,           // API success status
    val message: String,            // Response message
    val data: List<GiftData>       // List of available gifts
)
```

---

## User Flow Diagrams

### Complete Gift Send Flow:

```
MALE USER                           SYSTEM                          FEMALE USER
    |                                  |                                  |
    | 1. Taps Gift Icon                |                                  |
    |--------------------------------->|                                  |
    |                                  |                                  |
    | 2. Bottom Sheet Opens            |                                  |
    |<---------------------------------|                                  |
    |                                  |                                  |
    | 3. Selects Gift                  |                                  |
    |--------------------------------->|                                  |
    |                                  |                                  |
    |                            4. Check Coins                           |
    |                                  |                                  |
    |                            5. If Sufficient:                        |
    |                                  |                                  |
    |                            6. Deduct Coins                          |
    |                                  |                                  |
    |                            7. Send API Call                         |
    |                                  |                                  |
    | 8. Success Response              |                                  |
    |<---------------------------------|                                  |
    |                                  |                                  |
    | 9. Play Animation + Sound        |                                  |
    |                                  |                                  |
    |                            10. Send FCM Notification                |
    |                                  |--------------------------------->|
    |                                  |                                  |
    |                                  | 11. Receive Notification         |
    |                                  |                                  |
    |                                  |      12. Play Animation + Sound  |
    |                                  |                                  |
    |                                  |      13. Show Toast              |
    |                                  |                                  |
    | 14. Update Remaining Time        |                                  |
    |<---------------------------------|                                  |
    |                                  |                                  |
    |                            15. Notify Female of Time Update         |
    |                                  |--------------------------------->|
    |                                  |                                  |
    |                                  |      16. Update Countdown        |
```

---

## UI Elements

### Gift Bottom Sheet Layout:
**File**: `bottom_sheet_gifts_layout.xml`

**Components**:
- RecyclerView with GridLayoutManager (4 columns)
- Rounded background (drawable: `rounded_giftbottom_sheet.xml`)
- Custom BottomSheetDialogTheme

### Individual Gift Item Layout:
**File**: `adapter_gifts.xml`

**Components**:
- `iv_gift` - ImageView for gift icon
- `tv_coinsAmount` - TextView for coin cost

### Gift Animation Elements:
**In Calling Layouts**:
- `ivGiftImage` - ImageView for animated gift
- `ivFemaleUser` / `ivMaleUser` - Avatar images (animation destinations)

---

## Sound Effects

**Gift Sound File**: `res/raw/gift_tune.mp3`

**Playback Function**:
```kotlin
BaseApplication.getInstance()?.playSendGiftSound()
```

**When Played**:
- ✅ When male user sends gift
- ✅ When female user receives gift
- ✅ Synchronized with animation start

---

## Best Practices

### For Developers:

1. **Always validate coins** before allowing gift selection
2. **Handle network errors** gracefully (no network callback)
3. **Update remaining time** immediately after gift send
4. **Clean up animations** to prevent memory leaks
5. **Use LiveData observers** for real-time updates
6. **Dismiss bottom sheet** after successful send
7. **Show appropriate error messages** for all failure cases
8. **Test with different coin balances** (sufficient/insufficient)
9. **Verify FCM notifications** are properly configured
10. **Ensure animations work** on different screen sizes

### For Users:

1. **Check remaining time** before sending expensive gifts
2. **Add coins** if balance is low during important calls
3. **Send gifts strategically** to maximize call time
4. **Understand coin rates** (audio: 10/min, video: 60/min)

---

## Troubleshooting

### Common Issues:

#### 1. "Not enough coins" error
**Cause**: Remaining time insufficient for gift cost  
**Solution**: Add more coins or select cheaper gift

#### 2. Gift animation not playing
**Cause**: FCM notification not received  
**Solution**: Check network connection and FCM configuration

#### 3. Bottom sheet not opening
**Cause**: Fragment manager issue or call not active  
**Solution**: Ensure call is connected before tapping gift icon

#### 4. Countdown timer not updating
**Cause**: API call failed or network issue  
**Solution**: Check API response and retry

#### 5. Gift images not loading
**Cause**: Invalid image URL or network issue  
**Solution**: Verify image URLs in API response

---

## Configuration Files

### Drawable Resources:
- `ic_gift_sent.xml` - Gift sent icon
- `ic_gift_box.xml` - Gift box icon  
- `rounded_giftbottom_sheet.xml` - Bottom sheet background
- `gift_png.gif` - Gift animation GIF

### Layout Files:
- `bottom_sheet_gifts_layout.xml` - Main gift selection UI
- `adapter_gifts.xml` - Individual gift item layout
- `gift_send_dilog_layout.xml` - Gift confirmation dialog (currently commented out)

### Dependency Injection:
- `@HiltViewModel` - GiftViewModel  
- `@Inject` - GiftRepository
- `@AndroidEntryPoint` - GiftBottomSheetFragment

---

## Future Enhancements

### Potential Features:
1. **Gift History** - Track all gifts sent/received
2. **Gift Reactions** - Allow female users to react to gifts
3. **Gift Combos** - Send multiple gifts at once with discount
4. **Custom Gifts** - Upload personalized gift images
5. **Gift Leaderboard** - Top gift senders/receivers
6. **Gift Categories** - Flowers, chocolates, jewelry, etc.
7. **Seasonal Gifts** - Special gifts for holidays
8. **Gift Preview** - Preview animation before sending
9. **Gift Sound Options** - Different sounds for different gifts
10. **Gift Analytics** - Track gift popularity and usage

---

## Summary

The Gifts feature provides a seamless way for male users to show appreciation to female users during calls. The system handles:

- ✅ **Real-time validation** of coin balance
- ✅ **Automatic coin deduction** from call time
- ✅ **Beautiful animations** on both sides
- ✅ **Audio feedback** for better UX
- ✅ **FCM notifications** for instant delivery
- ✅ **Grid-based gift selection** for easy browsing
- ✅ **Multiple gift options** at different price points
- ✅ **Seamless integration** with calling features

The feature enhances user engagement and provides monetization opportunities through the coin/call time system.

---

**Document Version**: 1.0  
**Last Updated**: December 2024  
**Author**: Development Team  
**Status**: Production Ready ✅



