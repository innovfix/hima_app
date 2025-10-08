# How to Access Coupon System 🎟️

## ✅ What I Fixed

The coupon system was already implemented but the navigation was **commented out**. I've now **enabled** the navigation to the Payment screen where coupons are displayed.

## 📱 How to Access Coupons (Male User)

### Step-by-Step Flow:

1. **Open Wallet Screen**
   - Navigate to Wallet section in the app
   - You'll see coin packages (e.g., 440 coins for ₹199)

2. **Select a Coin Package**
   - Click on any coin package
   - Click the "Add Coins" button at the bottom

3. **Payment Screen Opens** ✨ (NOW ENABLED)
   - Shows selected coin amount (e.g., "440 Coins ₹199.0")
   - Shows "Save 20%" discount
   - Shows "Apply Coupon" section
   - You'll see "View all coupons" button with arrow →

4. **View Coupons**
   - Click "View all coupons" button
   - Opens beautiful pink coupon selection screen

5. **Coupon Selection Screen** 🎁
   - Pink gradient header with gift box
   - "Best coupon" section
   - "More offers" section
   - Each coupon shows:
     - Discount percentage (e.g., 50% OFF)
     - Coupon code (e.g., OLD50)
     - Save amount (e.g., Save up to ₹60)
     - Validity info
     - Price breakdown
     - Yellow "APPLY" button

6. **Apply Coupon**
   - Click "APPLY" button on any coupon
   - Returns to Payment screen
   - Shows coupon code with "Applied!" indicator
   - Shows discount amount in green (e.g., -₹60)
   - Final amount is updated

7. **Proceed to Payment**
   - Click "Proceed to Pay" button
   - Complete payment via PhonePe/GPay

## 🔧 What Changed in Code

### WalletActivity.kt (Line 583-588)
```kotlin
// Navigate to PaymentActivity to show coupon options
val intent = Intent(this@WalletActivity, PaymentActivity::class.java).apply {
    putExtra("AMOUNT", amount)
    putExtra("COIN_SELECTED", selectedCoin)
    putExtra("SAVE_PERCENT", selectedSavePercent)
}
startActivity(intent)
```

**Old behavior:** Clicked "Add Coins" → Directly opened PhonePe/GPay
**New behavior:** Clicked "Add Coins" → Opens Payment screen → Can apply coupons → Then proceed to PhonePe/GPay

## 📂 Files Modified/Created

### Modified:
- `WalletActivity.kt` - Added navigation to PaymentActivity

### Created UI Files:
1. `activity_coupon.xml` - Coupon selection screen
2. `item_coupons.xml` - Individual coupon card design
3. `activity_payment.xml` - Updated with modern design
4. `bg_coupon_header_pink.xml` - Pink gradient header
5. `bg_coupon_card_modern.xml` - Modern card design
6. `bg_apply_button_yellow.xml` - Yellow apply button
7. `bg_price_badge_green.xml` - Green price badge
8. `bg_discount_badge.xml` - Discount badge
9. `ic_gift_box.xml` - Gift box illustration

### Existing Files (Already Working):
- `CouponActivity.kt` - Handles coupon display and selection
- `PaymentActivity.kt` - Handles payment with coupons
- `CouponViewModel.kt` - Manages coupon data
- Backend API integration

## 🎯 Testing Checklist

- [ ] Open Wallet screen
- [ ] Click "Add Coins" button
- [ ] Verify Payment screen opens (not PhonePe/GPay directly)
- [ ] Click "View all coupons" 
- [ ] Verify coupon screen opens with pink header
- [ ] Verify coupons are displayed with:
  - [ ] Discount percentage badge
  - [ ] Coupon code
  - [ ] Save amount
  - [ ] Validity text
  - [ ] Price breakdown
  - [ ] Yellow "APPLY" button
- [ ] Click "APPLY" on a coupon
- [ ] Verify returns to Payment screen
- [ ] Verify coupon code shows with "Applied!" text
- [ ] Verify discount amount shows in green
- [ ] Verify final amount is updated
- [ ] Click "Proceed to Pay"
- [ ] Verify payment gateway opens

## 📍 Activity Registration

All activities are properly registered in `AndroidManifest.xml`:
- Line 180: `WalletActivity`
- Line 196: `PaymentActivity`
- Line 198: `CouponActivity` ✅

## 🎨 Visual Design

The coupon UI matches your reference screenshots with:
- ✨ Pink gradient header (#FF1383)
- 🎁 Gift box illustration
- 💚 Green discount badges (#13775B)
- 💛 Yellow apply buttons (#FFD018)
- 💜 Purple "Applied!" indicator (#6D31ED)
- 🎴 Modern card designs with shadows

## 🚀 Ready to Test!

The coupon system is now **fully accessible**. Just build and run the app, go to Wallet, select coins, and you'll see the Payment screen with coupon options!

## ⚠️ Note

The old direct payment code (PhonePe/GPay) is now commented out. The payment still works but now goes through:
```
Wallet → Payment Screen (with coupons) → PhonePe/GPay
```

If you need to revert to direct payment (skip coupon screen), uncomment the old code in `WalletActivity.kt` starting at line 590.

