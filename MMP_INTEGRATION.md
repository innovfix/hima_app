# MMP Integration — Cross-Check Guide

**Date:** June 26, 2026  
**App:** `com.gmwapp.hima`  
**MMP Server:** https://mmp.himaofficial.in  
**Replaces:** AppsFlyer (`af-android-sdk:6.15.0`)

---

## Files Changed

| File | What Changed |
|---|---|
| `app/build.gradle.kts` | Removed AppsFlyer dep; added GAID + OkHttp deps; added MMP BuildConfig fields |
| `app/src/main/java/com/gmwapp/hima/mmp/MmpClient.kt` | **NEW FILE** — MMP client |
| `app/src/main/java/com/gmwapp/hima/BaseApplication.kt` | Replaced AppsFlyer init with MmpClient.init |
| `app/src/main/java/com/gmwapp/hima/activities/SelectLanguageActivity.kt` | signup + identify |
| `app/src/main/java/com/gmwapp/hima/activities/WalletActivity.kt` | purchase + initiated_checkout |
| `app/src/main/java/com/gmwapp/hima/activities/MainActivity.kt` | purchase + initiated_checkout + daily_active_user |
| `app/src/main/java/com/gmwapp/hima/fragments/FemaleHomeFragment.kt` | voice_verified + two_min_duration_completed |
| `app/src/main/java/com/gmwapp/hima/agora/male/MaleAudioCallingActivity.kt` | call_started |
| `app/src/main/java/com/gmwapp/hima/agora/male/MaleVideoCallingActivity.kt` | call_started |
| `app/src/main/java/com/gmwapp/hima/utils/SubscriptionStateCache.kt` | start_trial |
| `app/src/main/AndroidManifest.xml` | Removed `hima.onelink.me` intent-filter |

---

## 1. build.gradle.kts — Dependency Changes

### Removed
```kotlin
implementation("com.appsflyer:af-android-sdk:6.15.0")
```

### Added
```kotlin
implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

### OkHttp version aligned
```kotlin
// was 4.5.0, bumped to match
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
```

### BuildConfig fields added to BOTH flavors (`development` and `production`)
```kotlin
buildConfigField("String", "MMP_BASE_URL", "\"https://mmp.himaofficial.in\"")
buildConfigField("String", "MMP_SDK_KEY",  "\"mmp_sFcODEe0e1DgS9IlAFBRxMQ9aPMG\"")
buildConfigField("String", "MMP_SDK_SECRET","\"G5V0J8qvP2aN64XXdJs4DYXzzwHd6eDi1kwwCo5UJdsMjUREUJUbkzHpN7GK4c0a\"")
```

---

## 2. MmpClient.kt — New File

**Location:** `app/src/main/java/com/gmwapp/hima/mmp/MmpClient.kt`

### Public API

| Method | When to call |
|---|---|
| `MmpClient.init(context)` | `Application.onCreate()` — once |
| `MmpClient.trackSignup(customerUserId)` | User completes registration |
| `MmpClient.identify(customerUserId, email?)` | After login or registration |
| `MmpClient.trackPurchase(revenueInr, productId?, customerUserId?)` | After successful coin/subscription payment |
| `MmpClient.trackEvent(name, revenue?, currency, params?, customerUserId?)` | Any custom event |
| `MmpClient.eraseUser()` | GDPR erasure request |

### How it works
- **GAID with UUID fallback** — read once on a background thread in `init()` and cached. When GAID is missing or the all-zero placeholder (Android 14+ "Limit ad personalization", or devices without Play Services), MMP falls back to a stable random UUID stored in `SharedPreferences("mmp")` under key `mmp_device_id`. This prevents device matching from breaking on opted-out devices.
- **Install referrer is always included** — `trackInstallInternal()` calls `readInstallReferrer()` (Play Install Referrer Library, 5s timeout via `CountDownLatch`) and puts the raw referrer string in the `install_referrer` field of the first `/api/v1/install` POST. This is what gives MMP click-to-install attribution precision (Meta `fbclid`, Google `gclid`) instead of probabilistic fingerprint matching.
- **Race-free signup/identify** — `trackSignup(userId)` internally calls `identify(userId)` first. Both go through the same single-thread executor, so the device row is guaranteed to have `customer_user_id` set before the signup event is recorded.
- First launch is detected via `SharedPreferences("mmp")` key `install_tracked`
- All HTTP calls go to `https://mmp.himaofficial.in` with HMAC-SHA256 signature headers:
  - `X-MMP-Key`
  - `X-MMP-Timestamp`
  - `X-MMP-Signature`
- Fire-and-forget — network failures are silent (no crash)

---

## 3. BaseApplication.kt — Init Changes

### Removed
- `import com.appsflyer.AppsFlyerLib`
- `import com.appsflyer.AppsFlyerConversionListener`
- The entire `appflyer()` method (was ~40 lines including conversion listener)

### Added (in `onCreate`)
```kotlin
MmpClient.init(this)
// Re-identify returning (already logged-in) users
getPrefs()?.getUserData()?.id?.let { uid ->
    MmpClient.identify(uid.toString())
}
```

---

## 4. Event Migration Map

### SelectLanguageActivity.kt
**Trigger:** User completes registration (both male and female)

| Before (AppsFlyer) | After (MMP) |
|---|---|
| `AppsFlyerLib.getInstance().logEvent(this, "af_complete_registration", ...)` | `MmpClient.trackSignup(userId)` |

> `trackSignup` internally calls `identify` first, so the device row is linked to `customer_user_id` before the signup event is recorded. No need to call `identify` separately at the registration site.

---

### WalletActivity.kt
**Trigger 1:** Successful coin purchase

| Before | After |
|---|---|
| `logEvent("af_purchase", {af_revenue, af_currency, af_coin_id})` | `MmpClient.trackPurchase(revenueInr=coinAmount, productId=coinId, customerUserId=userId)` |

**Trigger 2:** User taps buy button (checkout initiated)

| Before | After |
|---|---|
| `logEvent("af_initiated_checkout", {af_price, af_currency})` | `MmpClient.trackEvent("initiated_checkout", revenue=price, params={coin_id})` |

---

### MainActivity.kt
**Trigger 1:** Subscription/coin purchase (separate purchase path from WalletActivity)

| Before | After |
|---|---|
| `logEvent("af_purchase", {af_revenue, af_currency, af_coin_id})` | `MmpClient.trackPurchase(revenueInr=coinAmount, productId=coinId, customerUserId=userID)` |

**Trigger 2:** Checkout initiated

| Before | After |
|---|---|
| `logEvent("af_initiated_checkout", {af_price, af_currency})` | `MmpClient.trackEvent("initiated_checkout", revenue=af_price.toDouble(), params={coin_id})` |

**Trigger 3:** Daily active user (male only)

| Before | After |
|---|---|
| `logEvent("daily_active_user", {user_id})` | `MmpClient.trackEvent("daily_active_user", customerUserId=userId)` |

---

### FemaleHomeFragment.kt
**Trigger 1:** Female creator passes voice verification

| Before | After |
|---|---|
| `logEvent("voice_verified", {user_id, gender, status})` | `MmpClient.trackEvent("voice_verified", params={user_id, gender, status}, customerUserId=userId)` |

**Trigger 2:** 2-minute call duration milestone

| Before | After |
|---|---|
| `logEvent("two_min_duration_completed", {user_id, total_talk_duration_minutes, gender})` | `MmpClient.trackEvent("two_min_duration_completed", params={user_id, minutes, gender}, customerUserId=userId)` |

---

### MaleAudioCallingActivity.kt
**Trigger:** Agora audio call joins (male side)

| Before | After |
|---|---|
| `logEvent("call_started", {user_id, call_type="Audio"})` | `MmpClient.trackEvent("call_started", params={user_id, call_type="Audio"}, customerUserId=maleUserId)` |

---

### MaleVideoCallingActivity.kt
**Trigger:** Agora video call joins (male side)

| Before | After |
|---|---|
| `logEvent("call_started", {user_id, call_type="Video"})` | `MmpClient.trackEvent("call_started", params={user_id, call_type="Video"}, customerUserId=maleUserId)` |

---

### SubscriptionStateCache.kt
**Trigger:** Subscription trial activated (cache state transition)

| Before | After |
|---|---|
| `logEvent("af_start_trial", {af_price=1.0, af_currency="INR", plan_type, language})` | `MmpClient.trackEvent("start_trial", revenue=1.0, params={plan_type, language}, customerUserId=userId)` |

---

## 5. AndroidManifest.xml

### Removed (AppsFlyer OneLink intent-filter)
```xml
<!-- REMOVED -->
<activity android:name=".activities.DeepLinkActivity" android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:host="hima.onelink.me" android:scheme="https" />
    </intent-filter>
</activity>
```

### After
```xml
<!-- DeepLinkActivity kept (has its own routing logic), just no longer exported -->
<activity android:name=".activities.DeepLinkActivity" android:exported="false" />
```

---

## 6. What Was NOT Changed

- Firebase Analytics calls — untouched
- Meta/Facebook `AppEventsLogger` calls — untouched
- `AppEventLogger.logEvent(...)` (your own backend logging) — untouched
- `BaseApplication.getInstallReferrer()` (your own UTM/referrer backend flow) — untouched
- `com.android.installreferrer:installreferrer:2.2` dependency — kept (MMP uses it internally too)

---

## 7. Test Checklist

### curl smoke tests
```bash
# Install
curl -X POST https://mmp.himaofficial.in/api/v1/install \
  -H "Content-Type: application/json" \
  -H "X-MMP-Key: mmp_sFcODEe0e1DgS9IlAFBRxMQ9aPMG" \
  -d '{"gaid":"test-gaid-123","device_model":"Pixel 7","os_version":"13"}'

# Signup
curl -X POST https://mmp.himaofficial.in/api/v1/event \
  -H "Content-Type: application/json" \
  -H "X-MMP-Key: mmp_sFcODEe0e1DgS9IlAFBRxMQ9aPMG" \
  -d '{"gaid":"test-gaid-123","name":"signup","customer_user_id":"42"}'

# Purchase (99 INR)
curl -X POST https://mmp.himaofficial.in/api/v1/event \
  -H "Content-Type: application/json" \
  -H "X-MMP-Key: mmp_sFcODEe0e1DgS9IlAFBRxMQ9aPMG" \
  -d '{"gaid":"test-gaid-123","name":"purchase","revenue":99.0,"currency":"INR"}'
```

### On-device checklist
- [ ] Fresh install → row appears in MMP Admin > Installs
- [ ] Register new user → `signup` event appears in MMP Admin > Events
- [ ] Buy a coin pack → `purchase` event with correct INR amount
- [ ] Tap buy button without completing → `initiated_checkout` event
- [ ] Female voice verification → `voice_verified` event
- [ ] 2-minute call → `two_min_duration_completed` event
- [ ] Male starts audio call → `call_started` (call_type=Audio)
- [ ] Male starts video call → `call_started` (call_type=Video)
- [ ] Subscription trial activated → `start_trial` event (revenue=1.0)
- [ ] Male DAU → `daily_active_user` event

**Dashboard:** https://mmp.himaofficial.in/admin → Performance → Installs / Events

---

## 8. Pre-Ship Verification (3 critical items)

### 1. install_referrer in the first install POST — VERIFIED
`MmpClient.trackInstallInternal()` reads the referrer via `InstallReferrerClient` (5s `CountDownLatch` wait) and puts it in the POST body as `install_referrer`:
```kotlin
val body = JSONObject().apply {
    put("gaid", gaid)
    put("install_referrer", referrer)   // <-- present
    ...
}
post("/api/v1/install", body) { ... }
```
**Result:** Click-to-install attribution (Meta `fbclid`, Google `gclid`) works deterministically, not probabilistically.

### 2. Identify-before-signup race — FIXED
`MmpClient.trackSignup(userId)` now calls `identify(userId)` internally before posting the signup event. Both POSTs are queued on the same single-thread executor, so `/api/v1/identify` is guaranteed to land on the server before `/api/v1/event { name: "signup" }`. Call sites only need to invoke `trackSignup`.

### 3. GAID empty/zero fallback — FIXED
`resolveDeviceId()` checks for blank GAID and the all-zero placeholder. If detected (Android 14+ "Limit ad personalization", devices without Play Services), it falls back to a random UUID persisted in `SharedPreferences("mmp")` under key `mmp_device_id`:
```kotlin
val realGaid = readGaid(ctx)
if (realGaid.isNotBlank() && realGaid != "00000000-0000-0000-0000-000000000000") return realGaid
prefs.getString("mmp_device_id", null)?.let { return it }
val newId = java.util.UUID.randomUUID().toString()
prefs.edit().putString("mmp_device_id", newId).apply()
return newId
```
**Result:** Every device gets a stable identifier; matching never breaks on opted-out users.
