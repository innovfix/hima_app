# QA Module Breakdown — HI ma App

> **Purpose:** Split the entire app into clearly-owned modules from a **user's point of view**, so the QA team can divide work, test everything a real user would see, and report findings with a stable bug-ID format.
>
> **Test cases:** Intentionally **out of scope** in this document. Step-by-step test cases will be added later, per module. For now this doc only defines *what each module is about* and *what screens & actions the user will see*.

| Field | Value |
|---|---|
| App name | HI ma |
| Total modules | **19** (M01 – M19) |
| Document owner | _______________ |
| Last updated | _______________ |

---

## 1. How to use this document

1. Pick one module (example: **M05 — Chat**) and put your name in its **Owner** field.
2. Open the app as a real user and walk through every screen and action listed for that module.
3. Log every bug you find using the format **`M##-###`**.
   - Example: `M05-001` = first bug in the Chat module.
   - Example: `M10-014` = 14th bug in Wallet / Payments.
4. Update the **Status** column in the index table as you progress.
5. Detailed step-by-step test cases will be written per module in a later pass — for now, use the "What the user can do" and "Screens the user sees" bullets as your testing scope.

### What to include in every bug report

- Bug ID — `M##-###`
- Module — example: `M05 — Chat`
- Screen where the bug happened (use the screen name from this doc)
- Steps you did to make the bug happen
- What you expected to see
- What actually happened
- Phone model + Android version
- App version (check Settings → About)
- Screenshot or short screen recording
- Severity — **Critical / High / Medium / Low**

### Severity guide

| Severity | Use when… |
|---|---|
| Critical | App crashes, money is lost, call drops completely, payment fails, user cannot sign up or log in |
| High | A main feature is broken but the user can still work around it (e.g. wrong coin deduction, call has loud noise) |
| Medium | Small feature broken or wrong on some devices only |
| Low | Text typo, icon misaligned, colour wrong, small cosmetic issue |

---

## 2. Module index

| ID | Module | Priority | Owner | Status |
|----|--------|----------|-------|--------|
| M01 | First-time Setup & Login | Critical | | `[ ]` Not started |
| M02 | My Profile & Account | High | | `[ ]` Not started |
| M03 | Home, Browse Users & View Profile | Critical | | `[ ]` Not started |
| M04 | Friends & Friend Requests | Medium | | `[ ]` Not started |
| M05 | Chat (text, photo, voice note) | Critical | | `[ ]` Not started |
| M06 | Audio Call (1-to-1) | Critical | | `[ ]` Not started |
| M07 | Video Call (1-to-1) | Critical | | `[ ]` Not started |
| M08 | Random Call | High | | `[ ]` Not started |
| M09 | IPL Voice Rooms (Group Voice) | High | | `[ ]` Not started |
| M10 | Wallet — Buy Coins & Pay | Critical | | `[ ]` Not started |
| M11 | Earnings & Withdraw (for creators) | High | | `[ ]` Not started |
| M12 | Star Creator Application | Medium | | `[ ]` Not started |
| M13 | Rating & Review after call | Medium | | `[ ]` Not started |
| M14 | Block, Report & Warnings (Safety) | High | | `[ ]` Not started |
| M15 | Help & Support Tickets | Medium | | `[ ]` Not started |
| M16 | Terms, Policies & Share App | Low | | `[ ]` Not started |
| M17 | Notifications (push & in-app) | Critical | | `[ ]` Not started |
| M18 | Links, Referral & Invite a Friend | Medium | | `[ ]` Not started |
| M19 | General app quality (all-round) | Critical | | `[ ]` Not started |

**Status legend:** `[ ]` Not started &nbsp;•&nbsp; `[~]` In progress &nbsp;•&nbsp; `[x]` Done &nbsp;•&nbsp; `[!]` Blocked

---

## 3. Modules — M01 to M19

---

### M01 — First-time Setup & Login

**What this module is about:** Everything a brand-new user sees from the moment they open the app until they reach the Home screen. Also the login flow for returning users.

**What the user can do:**
- Open the app for the very first time
- Log in with their phone number and an OTP
- Log in quickly using Truecaller (one-tap)
- Choose their gender
- Choose their preferred language
- Enter their display name
- Go through the AI onboarding chat
- Give permissions (mic, camera, notifications, storage)
- Returning user: open app and skip straight to Home

**Screens the user sees:**
- Splash screen (app logo)
- Login screen (phone number + country code)
- OTP entry screen (type the code that comes in SMS, or it auto-reads)
- Truecaller one-tap popup (if Truecaller is installed)
- Gender selection screen (Male / Female)
- Language selection screen
- Name entry screen ("What's your name?")
- AI onboarding chat screens (guided questions)
- Female users see extra steps — "About you" and "Almost done" screens
- Permissions request screens (mic, camera, notifications)
- Home (this is where onboarding ends)

**What the user expects:**
- OTP should arrive within a few seconds
- Truecaller login should skip the OTP step
- Pressing Back mid-setup should not break the app
- If the user closes the app halfway, reopening should take them to the same step
- Once logged in, opening the app again should go straight to Home

**Bug-ID prefix:** `M01-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M02 — My Profile & Account

**What this module is about:** Managing my own profile — name, photo, voice sample, privacy, and deleting the account if I want to leave.

**What the user can do:**
- View my own profile
- Edit my name, bio and interests
- Change my profile picture (pick from gallery, or choose from default avatars)
- Record a voice sample for voice verification
- Pick an IPL team badge to show on my profile
- Change account privacy settings
- Delete my account

**Screens the user sees:**
- My Profile tab (bottom navigation)
- Edit Profile screen (form fields + save button)
- Avatar picker screen
- Voice recording screen (press to record, preview, re-record, save)
- IPL team selector
- Account Privacy screen (toggles for each privacy option)
- Delete Account screen (reason dropdown + confirm button)
- Delete confirmation popup

**What the user expects:**
- Changes are saved immediately and reflected next time I open my profile
- Voice recording shows a waveform or timer while recording
- Deleting the account should ask to confirm and should actually log me out
- After deletion, the user cannot log back into the same account

**Bug-ID prefix:** `M02-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M03 — Home, Browse Users & View Profile

**What this module is about:** The main shell of the app — the bottom-navigation tabs and browsing other users.

**What the user can do:**
- See a feed of available users on Home
- Switch between Home, Recent, Favourite tabs
- Pull down to refresh
- Scroll and load more users
- Tap a user to see their full profile
- Start a chat, audio call, or video call from the other user's profile
- Mark a user as Favourite / remove from Favourite
- Try the "Random user" feature

**Screens the user sees:**
- Main app screen with bottom navigation
- Home tab (grid or list of users)
- Recent tab (users I recently interacted with)
- Favourite tab (users I have favourited)
- Other user's Profile screen (with Chat / Audio Call / Video Call buttons)
- Random user screen
- Empty-state screens ("No users found", "Check your internet")

**What the user expects:**
- Feed loads fast and keeps loading more as I scroll
- User photos load clearly without broken image icons
- Tapping a user always opens their profile
- Favourite heart icon toggles and remembers the choice
- Bottom nav stays visible and switches tabs instantly

**Bug-ID prefix:** `M03-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M04 — Friends & Friend Requests

**What this module is about:** Sending friend requests, accepting / rejecting them, and managing the friends list.

**What the user can do:**
- Send a friend request from another user's profile
- Cancel a friend request I sent
- See requests others sent to me
- Accept or reject an incoming request
- See my full friends list
- Start a chat or call with a friend
- Remove (unfriend) someone

**Screens the user sees:**
- Friends tab / screen with sub-tabs: Sent, Received, Friends
- Sent requests list
- Received requests list (with Accept / Reject buttons)
- My friends list
- Empty-state screens (no friends, no requests)
- Request sent / accepted toast messages

**What the user expects:**
- The count of pending requests is correct
- Accepting a request immediately moves the user to Friends list
- Rejected users are removed from the Received list
- If I block someone, they disappear from this list too

**Bug-ID prefix:** `M04-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M05 — Chat (text, photo, voice note)

**What this module is about:** One-to-one chatting with another user — text messages, photos, voice notes, reactions, typing indicator, block / unblock.

**What the user can do:**
- See all my conversations in a list with last message preview
- Open a chat and send text messages
- See the other person typing
- Send a photo (from gallery or camera)
- Record and send a voice note
- Play a voice note I received
- React to a message with an emoji
- Long-press to copy / delete a message
- Block the other user from the chat screen
- Unblock a previously blocked user
- See read / delivered status on messages

**Screens the user sees:**
- Chat list (all conversations with unread counts and last message preview — "Photo", "Voice note" or text)
- Chat conversation screen (messages, input bar, emoji button, attach button, mic button)
- Attachment bottom sheet (Camera / Gallery / etc.)
- Image preview screen before sending
- Voice note recording UI (press and hold mic, slide to cancel)
- Voice note playback bar with play/pause and seek
- Emoji / reaction picker popup
- Block user confirmation popup
- "Chat is free" label (where shown)
- "Notify when online" toggle

**What the user expects:**
- Messages appear instantly for both sender and receiver
- Typing indicator appears when the other person is typing
- Photos upload with a progress indicator and show a preview once uploaded
- Voice notes record smoothly and play without cutting
- If internet drops while sending, the message should retry or show a clear error
- Reopening the chat shows the full message history
- Blocking stops new messages from coming in; unblocking restores it

**Bug-ID prefix:** `M05-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M06 — Audio Call (1-to-1)

**What this module is about:** Making and receiving one-to-one voice calls between two users, including the full lifecycle — ringing, connecting, talking, ending.

**What the user can do:**
- Call another user using voice (from their profile or chat)
- Receive a voice call (with ringtone + full-screen incoming UI)
- Accept or reject from the incoming screen or the notification
- Mute / unmute my microphone
- Switch to speaker / earpiece
- Send a gift during the call
- See the call timer ticking
- End the call
- See a missed-call notification if I don't pick up
- See an "out of coins" popup when coins run out

**Screens the user sees:**
- Outgoing call screen ("Calling…", with cancel button)
- Incoming call screen (full screen, with Accept and Reject buttons, plays ringtone, vibrates)
- Incoming call notification (when phone is locked or user is in another app)
- Connecting screen ("Connecting…", briefly)
- In-call screen (call timer, mute button, speaker button, end-call button, gift button)
- Send gift bottom sheet (during call)
- Out-of-coins popup (shown inside the call)
- Missed call notification
- Call-ended screen or toast
- Rating popup after call (see **M13**)

**What the user expects:**
- Ringtone plays until the call is answered or the 30-second timeout
- Incoming call works even when the phone is locked
- Accept / Reject from notification works the same as from the full screen
- Mute, speaker, end-call respond instantly
- Coins deduct every minute and the balance is visible
- If the user minimises the app, the call continues in the background
- If coins run out, the call ends with a clear message

**Bug-ID prefix:** `M06-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M07 — Video Call (1-to-1)

**What this module is about:** Same as audio call but with live video (two-way camera).

**What the user can do:**
- Call another user using video
- Receive a video call
- Accept or reject a video call
- Turn my camera on / off during the call
- Switch between front and back camera
- Mute / unmute microphone
- Switch to speaker
- Send a gift during the call
- End the call

**Screens the user sees:**
- Outgoing video call screen (with my preview + "Calling…")
- Incoming video call screen
- Connecting screen
- In-call video screen (large remote video + small self-preview + controls: mute, camera on/off, flip camera, speaker, end, gift)
- Face-detection safety warning popup (if it triggers)
- Out-of-coins popup
- Call-ended screen
- Rating popup after call (see **M13**)

**What the user expects:**
- Both users can see each other clearly
- Video stays smooth on a normal Wi-Fi / 4G connection
- Camera on/off and flip respond instantly
- If the network gets slow, video may freeze briefly but the call should not crash
- Minimising the app should not kill the call
- Coins deduct at the video rate every minute

**Bug-ID prefix:** `M07-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M08 — Random Call

**What this module is about:** User taps "Random Call" and the app matches them with any available user.

**What the user can do:**
- Start a random call
- See a "Searching for user…" screen
- Get matched with a random user and auto-enter a call
- Cancel the search before a match is found
- Retry if no user was found

**Screens the user sees:**
- Random call start button (on Home or a dedicated tab)
- Searching / matching screen (with cancel)
- "No users available right now" state
- Audio or video call screen once matched (same as **M06** / **M07**)

**What the user expects:**
- Matching finishes within a reasonable time (few seconds)
- If no one is available, show a clear message and let the user try again
- Cancel button stops the search immediately
- After the call ends, the user returns to Home or Random Call screen

**Bug-ID prefix:** `M08-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M09 — IPL Voice Rooms (Group Voice)

**What this module is about:** Cricket / IPL themed group voice rooms where many users talk together.

**What the user can do:**
- Browse the list of active IPL rooms
- Create my own room
- Join a room (needs a minimum of 60 coins)
- Join a room by entering a room code
- Join a random room
- Listen as a listener, or speak as a speaker
- Mute / unmute myself
- React with emojis during the room
- Leave the room anytime
- Host can close the room

**Screens the user sees:**
- IPL Rooms list screen (active rooms, team banners)
- Create Room screen
- Join by Code popup
- In-room screen (host on top, speakers, listeners, mute button, reactions, leave button)
- Coin rules info (pop-up or info icon)
- Auto-kick popup when coins run out
- Match suggestions list
- Empty state ("No rooms yet")

**What the user expects:**
- Joining a room costs at least 60 coins and deducts 10 coins per minute after a short free grace period
- Coin balance is visible during the room
- Mute / unmute responds instantly
- Reactions appear on screen for a few seconds
- Leaving the room cleanly stops coin deduction
- Host controls (mute others, close room) work only for the host

**Bug-ID prefix:** `M09-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M10 — Wallet — Buy Coins & Pay

**What this module is about:** Everything to do with adding coins to my wallet — all the different payment methods, coupons, and the different payment outcomes (success / failed / pending).

**What the user can do:**
- Check my coin balance
- Choose a coin plan to buy
- Apply a coupon code
- Pay using Cashfree
- Pay using PhonePe
- Pay using UPI apps (Google Pay, PhonePe, etc.)
- Pay using Google Play in-app purchase
- Use the YouTube recharge option
- Claim free coins (if eligible)
- Try the small "try coins" offer

**Screens the user sees:**
- Wallet screen (balance on top, buy-coins button, transactions link)
- Coin plans screen (list of plans, best offer highlighted)
- Coupon code entry screen / popup
- Payment method selection popup
- Payment method screens (Cashfree, PhonePe, UPI)
- Add UPI screen (save my UPI id for later)
- Payment web view (for bank OTP, 3D secure, etc.)
- Payment in progress / Payment initiated screen
- Payment success screen (coins added)
- Payment failed / cancelled screen
- Free Coins screen (claim button + eligibility)
- YouTube Recharge flow

**What the user expects:**
- The coin plan shown is the plan that actually gets bought
- After successful payment, the wallet balance updates right away
- A receipt / transaction entry appears in Transactions
- If the payment fails or the user cancels, no coins should be added and no money deducted (or refunded)
- Coupons either apply with a clear discount or show a clear "invalid" message
- If the payment page is closed mid-payment, the app should recover cleanly

**Bug-ID prefix:** `M10-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M11 — Earnings & Withdraw (for creators)

**What this module is about:** The creator-side money flow — how much I earned, and taking that money out to my bank / UPI.

**What the user can do:**
- See today's / this week's / this month's / total earnings
- See a list of all my transactions (calls, chats, gifts, withdrawals)
- Add or update my bank account details
- Add or update my UPI id
- Upload my PAN card (KYC)
- Request a withdrawal to bank
- Request a withdrawal to UPI
- See the status of each withdrawal (pending / success / rejected)

**Screens the user sees:**
- Earnings screen (summary + chart or numbers)
- Transactions screen (full history, scroll + filter)
- Female transactions screen (creator-specific view)
- Bank Update screen (account number, IFSC, name)
- Add UPI screen
- KYC screen (PAN upload with camera / gallery)
- Withdraw screen (amount + method + charges)
- Withdraw confirmation popup
- Withdrawal status messages

**What the user expects:**
- Earnings figures match what was earned from calls and chats
- Withdrawal cannot be requested without KYC + bank / UPI details
- Transaction charges are shown clearly before the user confirms
- Once submitted, withdrawal status updates over time (pending → success / rejected)
- Minimum withdrawal amount is clearly stated
- If KYC is rejected, the user can resubmit

**Bug-ID prefix:** `M11-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M12 — Star Creator Application

**What this module is about:** The application flow for users who want to become a "Star Creator".

**What the user can do:**
- Start the Star Creator application
- Fill the application form
- Record a self-introduction video
- Preview and re-record the video
- Submit the application
- Check my application status

**Screens the user sees:**
- Star Creator landing / info screen
- Application form screen
- Video recording screen (with record button and timer)
- Video preview / retake screen
- Submit button + loading state
- "Application submitted" screen
- Application status screen (Applied / Approved / Rejected)

**What the user expects:**
- The video records clearly with good audio
- Uploading the video shows progress
- The user gets a clear confirmation after submitting
- If rejected, the user should be able to apply again

**Bug-ID prefix:** `M12-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M13 — Rating & Review after call

**What this module is about:** The popup that appears after a call, asking the user to rate the other person.

**What the user can do:**
- Rate the other user 1 to 5 stars
- Select review tags (e.g. "fun conversation", "rude behaviour")
- Submit the rating
- Skip the rating
- Rate-limit: rating only shown when eligible

**Screens the user sees:**
- Rating popup (stars + tags + submit + skip)
- Review tags screen (selectable chips)
- Thank-you / confirmation message after submitting

**What the user expects:**
- The rating popup appears only after an actual call happened
- Once rated or skipped, the same popup should not keep re-appearing
- The selected tags and star rating are actually submitted
- Submitting works even on slow networks (retry on failure)

**Bug-ID prefix:** `M13-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M14 — Block, Report & Warnings (Safety)

**What this module is about:** All user-safety features — blocking a user, reporting bad behaviour, and viewing warnings the user has received.

**What the user can do:**
- Block a user from their profile, from a chat, or from a call
- Unblock a previously blocked user
- Report a user with a reason (inappropriate, fake, etc.)
- View warnings I have received (as a user)
- View warnings I have received (as a creator)
- Read community guidelines link

**Screens the user sees:**
- Block / Report menu from three-dot (⋮) menu on profile / chat / call screens
- Block confirmation popup
- Report User screen (reasons list, description field, submit)
- My Warnings screen (list of warnings I got)
- Creator Warnings screen (for creators, separate list)
- Community Guidelines link / screen

**What the user expects:**
- After blocking, the other user cannot message or call me
- After blocking, their profile disappears from Home / Recent
- Unblocking brings everything back
- Reporting a user shows a confirmation and removes them from my view
- Warnings show clear text, date and the reason

**Bug-ID prefix:** `M14-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M15 — Help & Support Tickets

**What this module is about:** The help centre — FAQ, raising a support ticket, and seeing my ticket history.

**What the user can do:**
- Browse help topics (categories)
- Read FAQ / sub-query answers
- Watch explanation videos
- Open WhatsApp support chat
- Submit a new support ticket (with screenshot attachment)
- View my previous tickets and their status

**Screens the user sees:**
- Help & Support home screen (category tiles, search)
- Categories list
- Sub-queries list under each category
- Sub-query detail screen (answer + related actions)
- Submit Ticket screen (subject, description, attach button, submit)
- My Tickets list (each ticket with status: Open / In progress / Resolved)
- Ticket detail screen (thread of messages, if applicable)
- WhatsApp support button (opens WhatsApp)
- Explanation video player

**What the user expects:**
- Categories and FAQs load fast
- Submitting a ticket works with or without an attachment
- Tickets I submitted show up immediately in "My tickets"
- WhatsApp button opens the right WhatsApp number
- Videos play smoothly

**Bug-ID prefix:** `M15-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M16 — Terms, Policies & Share App

**What this module is about:** Legal / policy pages and sharing the app with friends.

**What the user can do:**
- Read Terms & Conditions
- Read Refund Policy
- Read Community Guidelines
- Tap external links inside policy pages
- Share the app (WhatsApp, other apps)
- See my refer-a-friend code

**Screens the user sees:**
- Terms & Conditions page (scrollable)
- Refund Policy page (scrollable)
- Community Guidelines page
- Share / Refer screen (share link + copy code + share buttons)
- In-app browser for other external links

**What the user expects:**
- Pages load without errors even on slow networks
- Scroll works smoothly, text is readable on small screens
- Share button opens the Android share sheet with a valid link
- Refer code is unique and copyable

**Bug-ID prefix:** `M16-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M17 — Notifications (push & in-app)

**What this module is about:** All the alerts the user receives — incoming calls, chat messages, missed calls, promotions — and how the app handles them.

**What the user sees / experiences:**
- Incoming call notification (full-screen + ringtone + vibrate) even when the phone is locked
- Missed call notification (after a missed incoming call)
- New chat message notification (with sender name + preview)
- Message notification directly deep-links into that chat
- Friend request notification
- Promotional / offer notifications
- Notification badge / count on the app icon

**What the user expects:**
- Incoming call notification actions (Accept / Reject) work exactly like the in-app buttons
- Tapping a chat notification opens the exact chat (not Home)
- Missed call notification disappears once the user opens the app
- Notifications do not arrive when the user has turned them off in Settings
- No duplicate notifications
- Notifications show the correct app icon + colour
- On Android "Do Not Disturb", incoming call still rings (because it's a phone-style call)

**Bug-ID prefix:** `M17-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M18 — Links, Referral & Invite a Friend

**What this module is about:** Opening the app from outside (links from Google, WhatsApp messages, etc.) and earning by inviting friends.

**What the user can do:**
- Tap a shared app link and land on the right screen inside the app
- Tap a shared link when the app is not installed → go to Play Store → install → open app with the same content
- Enter a referral code during signup and get bonus coins
- Share my own refer link with friends
- Earn when a friend installs and becomes active

**Screens the user sees:**
- App opens directly to the shared content (wallet / user profile / room / chat / promotion)
- Referral code entry field (usually during signup or on a dedicated Refer screen)
- Refer & Earn screen (my code, share buttons, rules)
- "Bonus credited" message when a friend joins

**What the user expects:**
- Any link shared from the app actually opens the app (not the browser) if the app is installed
- Referral codes are validated and bonus appears in Wallet
- Share buttons (WhatsApp, more) carry the link correctly
- Tracking is honest — the right user gets credit for the invite

**Bug-ID prefix:** `M18-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

### M19 — General app quality (all-round)

**What this module is about:** Situations that are not one feature but can happen in any screen — these should be tested across the whole app, not just one module.

**Situations the user can be in:**
- No internet connection
- Slow 2G / 3G network
- Switching between Wi-Fi and mobile data mid-action
- App minimised in the background, then reopened
- Phone call comes in during an audio / video call
- Low battery / battery-saver mode turned on
- User rotates the phone or uses split-screen
- Phone locked during a call, chat, or payment
- User changes phone language
- User denies a permission (mic, camera, notifications)
- Force-stop the app and reopen
- Force-update popup appears for old app versions
- Log out and log back in with the same number
- Using the app on a small screen phone, a large phone, and a tablet
- Old Android version vs latest Android version

**What the user expects:**
- The app never crashes in any of these situations
- Clear error messages ("No internet", "Try again") instead of silent failures
- Nothing freezes or stays stuck on a loader forever
- Payments, calls, and chats recover cleanly after a network drop
- Data is not lost when the app is minimised or force-stopped

**Bug-ID prefix:** `M19-###` &nbsp;|&nbsp; **Owner:** _______________ &nbsp;|&nbsp; **Status:** `[ ]` Not started

---

## 4. Appendix

### A. Device & OS matrix (fill during planning)

| Device | Brand | Android version | Screen size | Assigned to |
|---|---|---|---|---|
| | | | | |
| | | | | |
| | | | | |
| | | | | |

### B. Reference documents already in the repo

- `App_Testing_Checklist_QA_v2.pdf` — general QA checklist → helpful for **M19**
- `Manual_App_Testing_Checklist.pdf` — manual testing checklist
- `Manual_Testing_How_to_Report_Findings.pdf` — how to report findings
- `IPL_ROOMS_FULL_QA_REPORT.html` and other IPL reports → already covers **M09** prior findings

### C. Final sign-off checklist (for QA Lead)

- [ ] All 19 modules have an Owner assigned
- [ ] All 19 modules have been fully tested on at least 2 devices
- [ ] All Critical and High bugs are fixed or deferred with approval
- [ ] Device matrix (Appendix A) is filled and signed off
- [ ] Smoke test done on the final signed release build

---

*End of document. Detailed step-by-step test cases will be added in a separate pass, per module.*
