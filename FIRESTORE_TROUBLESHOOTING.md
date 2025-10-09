# Firestore Chat - Troubleshooting "Failed to Send Message"

## 🔍 Diagnosis Steps

### Step 1: Check Logcat for Detailed Error
After you get the "Failed to send message" toast, check Android Studio Logcat for these messages:

```
E/ChatActivity: ❌ Error sending message
E/ChatActivity: Error type: [ErrorType]
E/ChatActivity: Error message: [Actual error]
```

---

## 🚨 Common Errors & Solutions

### Error 1: "PERMISSION_DENIED" or "Missing or insufficient permissions"

**Cause**: Firestore security rules are blocking writes

**Solution**:
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project: `hi-ma-9664f`
3. Click **Firestore Database** in left menu
4. Click the **Rules** tab
5. Replace with this (for testing):

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

6. Click **Publish**
7. Wait 10 seconds and try again

---

### Error 2: "NOT_FOUND" or "Project not found"

**Cause**: Firestore is not enabled in your Firebase project

**Solution**:
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project: `hi-ma-9664f`
3. Click **Firestore Database** in left menu
4. If you see "Create database" button, click it
5. Choose:
   - **Start in test mode** (recommended for now)
   - **Location**: `asia-south1` (or closest to India)
6. Click **Enable**
7. Wait 1-2 minutes for setup to complete
8. Try sending message again

---

### Error 3: "UNAVAILABLE" or "Failed to get document"

**Cause**: No internet connection or Firebase services unavailable

**Solution**:
1. Check internet connection on device
2. Try toggling WiFi/Mobile data
3. Check Firebase Status: https://status.firebase.google.com
4. Restart app and try again

---

### Error 4: "Invalid user data"

**Cause**: User IDs are missing or invalid

**Check Logcat for**:
```
E/ChatActivity: Invalid user IDs - myUserId: [value], peerUserId: [value]
```

**Solution**:
1. If `myUserId` is empty:
   - User is not logged in
   - Check `BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id`
   
2. If `peerUserId` is "-1":
   - Intent extra `USER_ID` not being passed
   - Check `RecentCallsAdapter` line 116: `intent.putExtra("USER_ID", call.id)`

---

## ✅ Quick Fix Checklist

Before testing again, verify:

- [ ] **Firestore is enabled**
  - Go to Firebase Console > Firestore Database
  - Should show "Data" tab, not "Create database" button

- [ ] **Security rules allow writes**
  - Go to Firebase Console > Firestore Database > Rules
  - Set to test mode (allow all) temporarily

- [ ] **Internet connection works**
  - Open browser on device
  - Load any website to confirm

- [ ] **App has internet permission**
  - Already in AndroidManifest.xml: `<uses-permission android:name="android.permission.INTERNET"/>`

- [ ] **google-services.json is correct**
  - Project ID matches: `hi-ma-9664f`
  - Package names: `com.gmwapp.hima` and `com.gmwapp.hima.dev`

- [ ] **User is logged in**
  - Check that user data exists in app

---

## 🧪 Test in Steps

### Test 1: Verify Firebase Connection
Add this temporary code to ChatActivity `onCreate()` to test basic connection:

```kotlin
// After setupFirestoreListener()
db.collection("test").document("test").set(mapOf("test" to "working"))
    .addOnSuccessListener {
        Log.d("ChatActivity", "✅ Firebase connected successfully!")
        Toast.makeText(this, "Firebase connected", Toast.LENGTH_SHORT).show()
    }
    .addOnFailureListener { e ->
        Log.e("ChatActivity", "❌ Firebase connection failed: ${e.message}")
        Toast.makeText(this, "Firebase failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
```

If this fails, the problem is Firebase setup, not the chat code.

### Test 2: Check User IDs
Look in Logcat when opening chat screen:

```
D/ChatActivity: MyUserId: [should be a number], PeerUserId: [should be a number], ThreadId: [should be number_number]
```

If any are empty or "-1", that's your problem.

### Test 3: Manual Firestore Write
Try writing directly in Firebase Console:
1. Go to Firestore Database > Data
2. Click "Start collection"
3. Collection ID: `chats`
4. Document ID: `123_456`
5. Add sub-collection: `messages`
6. Add document with fields:
   - `from`: `"123"`
   - `to`: `"456"`
   - `text`: `"Test message"`
   - `timestamp`: (leave as timestamp)
7. If this fails, Firestore isn't properly enabled

---

## 📱 What to Send Me

If still not working, send me screenshots of:

1. **Logcat after sending message** (filter by "ChatActivity")
2. **Firebase Console > Firestore Database** (showing if database exists)
3. **Firebase Console > Firestore Database > Rules** (showing current rules)
4. **Toast message** (exact error text)

---

## 🎯 Most Likely Issues (in order)

1. **Firestore not enabled** (90% of cases)
   - Solution: Go to Firebase Console and create database

2. **Security rules blocking** (8% of cases)
   - Solution: Set rules to test mode (allow all)

3. **Wrong Firebase project** (1% of cases)
   - Solution: Download fresh google-services.json

4. **User not logged in** (1% of cases)
   - Solution: Ensure user data exists before opening chat

---

## 🔄 Quick Reset

If nothing works, try this:

1. **Re-download google-services.json**
   - Firebase Console > Project Settings > Your apps > Download
   - Replace `app/google-services.json`

2. **Clean and rebuild**
   ```
   Build > Clean Project
   Build > Rebuild Project
   ```

3. **Uninstall and reinstall app**
   - Complete fresh install

4. **Enable Firestore in test mode**
   - Firebase Console > Firestore Database > Create database > Test mode

---

## 📞 Support

Still stuck? Let me know:
1. What the Logcat error says (exact text)
2. Whether Firestore shows up in Firebase Console
3. What the toast message says

I'll help you fix it!

