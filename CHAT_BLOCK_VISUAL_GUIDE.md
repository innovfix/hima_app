# 🚫 Chat Block/Unblock Feature - Visual Guide

## User Interface Flows

### **Flow 1: Blocking a User**

```
┌──────────────────────────────┐
│     Chat Screen              │
│                              │
│  ✋ Sarah Johnson      [⋮]   │  ← Click three-dot menu
│     Online                   │
│                              │
│  ┌────────────────────────┐ │
│  │ Hi Sarah, how are you? │ │
│  └────────────────────────┘ │
│                              │
│    ┌──────────────────────┐ │
│    │ I'm good, thanks!    │ │
│    └──────────────────────┘ │
└──────────────────────────────┘
           ↓
┌──────────────────────────┐
│      PopupMenu           │
│  ┌────────────────────┐  │
│  │ • Block User       │  │ ← Select
│  │ • Unblock User ✗   │  │   (unavailable)
│  └────────────────────┘  │
└──────────────────────────┘
           ↓
┌──────────────────────────────┐
│   Confirmation Dialog        │
│                              │
│  Block User                  │
│  ──────────────────────      │
│  Are you sure you want to    │
│  block this user? You won't  │
│  be able to send messages    │
│  to them.                    │
│                              │
│  [Cancel]      [Block]       │ ← Click Block
└──────────────────────────────┘
           ↓
┌──────────────────────────────┐
│     Chat Screen (Updated)    │
│                              │
│  ✋ Sarah Johnson      [⋮]   │  ← Menu now shows Unblock
│     Online                   │
│                              │
│  📧 User blocked             │
│     successfully ✅           │
│                              │
│  Old messages visible ✅      │
│  New messages hidden ❌       │
└──────────────────────────────┘
```

---

### **Flow 2: Trying to Send Message When Blocked**

```
┌──────────────────────────────┐
│     Chat Screen              │
│                              │
│  ✋ Sarah Johnson      [⋮]   │
│     Online                   │
│                              │
│  ┌────────────────────────┐ │
│  │ Hi Sarah, how are you? │ │
│  └────────────────────────┘ │
│                              │
│  ┌─────────────────────────┐ │
│  │ Input: "Hey are you..."  │ ← Type message
│  │ [Send Button]            │
│  └─────────────────────────┘ │
└──────────────────────────────┘
           ↓ Click Send
┌──────────────────────────────┐
│     Toast Notification       │
│                              │
│  ⚠️  Please unblock to send  │
│      message                 │
│                              │
└──────────────────────────────┘
           ↓
Message NOT sent to Firestore ❌
Input text cleared 🔄
```

---

### **Flow 3: Someone Blocks You**

```
Account 1 (Sarah)          Account 2 (You)
───────────────────────   ──────────────────

Open Chat
Block User ✅
               →          Try to send message
                          ↓
                     Message SENT ✅
                     (appears in YOUR chat)
                          ↓
                     Sarah won't see it ❌
                     (filtered on her end)
                          ↓
                     No notification ⚠️
                     Sarah doesn't know ✨
```

---

### **Flow 4: Message Timeline When Blocking**

```
TIME    SENDER  MESSAGE                    VISIBLE TO BLOCKER
────────────────────────────────────────────────────────────
10:00   Sarah   "Hello"                    ✅ YES
10:05   Sarah   "How are you?"             ✅ YES
10:10   You     🚫 BLOCKED SARAH           
10:15   Sarah   "Are you there?"           ❌ HIDDEN
10:20   Sarah   "Please reply"             ❌ HIDDEN
10:25   You     "I'm busy"                 ✅ YES
10:30   Sarah   "Call me later"            ❌ HIDDEN

Result:
You see:        Old messages (10:00, 10:05) ✅
                New messages (10:15+)      ❌
                Your own messages         ✅

Notifications:  None for hidden messages  ⚠️
```

---

## Code Flow Diagram

```
╔═════════════════════════════════════════════════════════╗
║            ChatActivity Initialization                  ║
╚═════════════════════════════════════════════════════════╝
                         ↓
         ┌───────────────────────────────┐
         │  1. initializeViews()         │ Load UI
         │  2. setupRecyclerView()       │ Setup list
         │  3. setupUserIds()            │ Get user IDs
         └───────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │  4. checkIfUserIsBlocked()    │ ⭐ KEY STEP
         │     (ASYNC: query Firestore)  │
         │     Sets: isPeerBlocked       │
         │     Sets: blockTimestamp      │
         └───────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │  WAITS FOR RESPONSE...        │ Wait for DB
         └───────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │  5. setupFirestoreListener()  │ 🎧 NOW start
         │     FILTERS based on:         │ listening
         │     - isPeerBlocked flag      │
         │     - blockTimestamp         │
         └───────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │  6. setupClickListeners()     │ Setup clicks
         │  7. observeNotification...    │ Setup observer
         └───────────────────────────────┘
```

---

## Database Structure Visualization

```
Firestore Collections:

chats/
  ├── "123_456"/              (thread between users 123 and 456)
  │   ├── user_123_name: "You"
  │   ├── user_123_image: "..."
  │   ├── user_456_name: "Sarah"
  │   ├── user_456_image: "..."
  │   └── messages/
  │       ├── msg001: { from: 123, text: "Hi", timestamp: 10:00 }
  │       ├── msg002: { from: 456, text: "Hey", timestamp: 10:05 }
  │       └── msg003: { from: 456, text: "How are you?", timestamp: 10:15 }
  │
  └── "789_101"/
      ...

blocked_users/              ⭐ NEW COLLECTION
  ├── 123/                  (User 123's blocks)
  │   └── users/
  │       ├── 456/          (User 456 is blocked BY User 123)
  │       │   ├── blockedAt: Timestamp(10:10 AM)
  │       │   ├── userName: "Sarah"
  │       │   └── userImage: "..."
  │       └── 789/          (User 789 is blocked BY User 123)
  │           └── ...
  │
  ├── 456/                  (User 456's blocks)
  │   └── users/
  │       └── 999/          (User 999 is blocked BY User 456)
  │           └── ...
  │
  └── ...

active_chats/              (Already exists for activity tracking)
  ├── 123: { threadId: "123_456", lastUpdated: ... }
  └── ...
```

---

## State Machine Diagram

```
┌──────────────────────────────────────────────────┐
│           NOT BLOCKED STATE                      │
│         isPeerBlocked = false                    │
│  ┌────────────────────────────────────────────┐  │
│  │ ✅ Can send messages                       │  │
│  │ ✅ All messages visible                    │  │
│  │ ✅ Menu shows "Block User"                 │  │
│  │ ✅ Notifications delivered                 │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
              ↓                      ↑
         User clicks           User clicks
         "Block User"          "Unblock User"
              ↓                      ↑
┌──────────────────────────────────────────────────┐
│            BLOCKED STATE                         │
│         isPeerBlocked = true                     │
│  ┌────────────────────────────────────────────┐  │
│  │ ❌ Can't send messages                     │  │
│  │ ❌ New messages hidden                     │  │
│  │ ✅ Old messages visible                    │  │
│  │ ✅ Menu shows "Unblock User"               │  │
│  │ ❌ No notifications for blocked user       │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

---

## Menu Visibility Logic

```
popupMenu.show()
    ↓
Is isPeerBlocked = true?
    ↓                    ↓
  NO                    YES
    ↓                    ↓
Show:                Show:
├─ Block User ✅     ├─ Block User ❌ (hidden)
└─ Unblock User ❌   └─ Unblock User ✅
```

---

## Message Filtering Algorithm

```
For each message in snapshot:
    1. Get fromId, text, timestamp
    2. Is isPeerBlocked AND fromId == peerUserId AND blockTimestamp is set?
            NO → SHOW message ✅
            YES → Continue to step 3
    3. Is timestamp >= blockTimestamp?
            NO → SHOW message ✅ (sent before block)
            YES → HIDE message ❌ (sent after block)
```

**Example:**
```
blockTimestamp = 10:10:00

Message 1: timestamp = 10:05:00
Compare: 10:05 >= 10:10? NO
Result: SHOW ✅

Message 2: timestamp = 10:15:00
Compare: 10:15 >= 10:10? YES
Result: HIDE ❌
```

---

## Send Message Flow Diagram

```
User types message & clicks send
           ↓
   sendMessage()
           ↓
   Check: isPeerBlocked?
     ├─ YES → Show toast ❌ "Please unblock to send"
     │        Return (don't send)
     └─ NO → Continue
           ↓
   checkIfPeerBlockedMeAndSendMessage()
           ↓
   Query: Does peer have ME blocked?
     ├─ YES → Send anyway ✅ (they just won't see it)
     └─ NO → Send normally ✅
           ↓
   sendMessageToFirestore()
           ↓
   Save message to Firestore ✅
   (appears in MY chat)
           ↓
   checkIfReceiverBlockedMeAndSendNotification()
           ↓
   Query: Does receiver have ME blocked?
     ├─ YES → Skip notification ⚠️ (they won't see message anyway)
     └─ NO → Send notification ✅
```

---

## Notifications Decision Tree

```
User sends message
           ↓
Did receiver block me?
     ├─ YES → ❌ Don't send notification
     │        Receiver won't know about message
     └─ NO → Continue
           ↓
Is receiver actively viewing this chat?
     ├─ YES → ⚠️  Skip notification (they see it live)
     └─ NO → Continue
           ↓
Did they recently read previous messages?
     ├─ YES → ⚠️  Skip notification (they're engaged)
     └─ NO → ✅ Send notification
```

---

## Toast Messages Reference

| Situation | Message | Duration |
|-----------|---------|----------|
| Successfully blocked user | "User blocked successfully" | SHORT |
| Successfully unblocked | "User unblocked successfully" | SHORT |
| Trying to send while blocked | "Please unblock to send message" | SHORT |
| Peer has blocked me | "Unable to send message" | SHORT |
| Block failed | "Failed to block user" | SHORT |
| Unblock failed | "Failed to unblock user" | SHORT |
| Block check error | "Error checking block status" | SHORT |

---

## Log Messages Reference

| Scenario | Log Level | Message |
|----------|-----------|---------|
| Block status loaded | DEBUG | "Block status loaded: isPeerBlocked=$flag, blockTimestamp=$ts" |
| Block successful | DEBUG | "✅ User blocked successfully" |
| Unblock successful | DEBUG | "✅ User unblocked successfully" |
| Block failed | ERROR | "❌ Failed to block user" |
| Unblock failed | ERROR | "❌ Failed to unblock user" |
| Filtering message | DEBUG | "🚫 Filtering blocked message: sent at $ts1, blocked at $ts2" |
| Peer blocked me | DEBUG | "⚠️ Peer has blocked me - Cannot send message" |
| Receiver blocked me (notification) | DEBUG | "⚠️ Receiver has blocked me - Skipping notification" |

---

## Summary Table

| Feature | Before | After |
|---------|--------|-------|
| Block User | ❌ Not available | ✅ Via three-dot menu |
| Unblock User | ❌ Not available | ✅ Via three-dot menu |
| Send to Blocked | ✅ Allowed | ❌ Shows error |
| Receive from Blocker | ✅ All visible | ❌ New hidden |
| Old Messages | ✅ Visible | ✅ Still visible |
| Notifications | ✅ All sent | ⚠️ Blocked skip |
| Confirmation Dialog | ❌ None | ✅ Before blocking |
| Block Persistence | ❌ N/A | ✅ Firestore stored |
| Menu Shows Status | ❌ N/A | ✅ Block/Unblock toggle |

---

## 🎉 Complete Feature Overview

✅ **Fully Implemented** with:
- Three-dot menu integration
- Block/unblock toggle functionality  
- Message filtering with timestamp
- Send prevention with user feedback
- Notification suppression
- Firestore persistence
- Smooth UI with confirmations and toasts

All user requirements met! Ready for production testing. 🚀
