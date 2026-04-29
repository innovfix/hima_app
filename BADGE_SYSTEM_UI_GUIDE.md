# Star Level Badge System — UI Guide

This document explains how the creator Star Level Badge system shows up in the Hima Android app, page by page.

---

## What is the Star Level System?

Every female creator sits on one of 5 levels. Higher level = higher payout per heart (per minute of call).

| Level | Name | Rate per heart | What it takes (last 30 days) |
|-------|------|----------------|------------------------------|
| L1 | Newbie | ₹1.20 | Default — everyone starts here |
| L2 | Rising Star | ₹1.25 | Earn ₹500 + rating 1.1 or higher |
| L3 | Bright Star | ₹1.32 | Earn ₹2,000 + rating 1.5 or higher |
| L4 | Super Star | ₹1.45 | Earn ₹5,000 + rating 2.0 or higher |
| L5 | Galaxy Star | ₹1.65 | Earn ₹10,000 + rating 2.6 or higher |

The system also looks at: number of calls, active days, drop rate. But revenue and rating are the main two the creator can directly control.

If a creator's performance drops, she falls **only one level at a time** (soft decay) — never crashes from L5 to L1 overnight.

---

## Where the badge shows up

The badge appears on **3 pages**:

```
┌─────────────────────────────────────────────────┐
│ 1. Female Home — small "MY CREATOR LEVEL" card  │
│    → tap to open Creator Level page             │
├─────────────────────────────────────────────────┤
│ 2. Profile (female) — "Creator Level" card      │
│    → tap card to open Creator Level page        │
├─────────────────────────────────────────────────┤
│ 3. Creator Level — full info page (the home for │
│    everything badge-related)                    │
└─────────────────────────────────────────────────┘
```

---

## Page 1 — Female Home

`fragment_female_home.xml` + `FemaleHomeFragment.kt`

### "MY CREATOR LEVEL" card
- Sits at the top of female home, always visible for female users
- Shows: pink "MY CREATOR LEVEL" label, large level name ("L2 · Rising Star"), 5-star row, rate chip
- Tap opens the **Creator Level page**
- Currently hardcoded to L2 — will become dynamic when backend ships

---

## Page 2 — Profile (female)

`fragment_profile_female.xml` + `ProfileFemaleFragment.kt`

Inside the "Account" section, one element:

### Creator Level card
- Pink star icon, label "Creator Level", subtitle "Rising Star · ₹1.25 / 💗", chevron arrow
- Tap opens the **Creator Level page**
- Subtitle is hardcoded (will go dynamic with backend)

---

## Page 3 — Creator Level (the main info page)

`activity_creator_level.xml` + `CreatorLevelActivity.kt`

This is the full info screen with everything the creator needs to know. Sections from top to bottom:

### Section 1 — Hero card "YOU ARE AT"
- Big level name (e.g. "Rising Creator")
- 5-star row — gold for active stars, grey for inactive
- Rate chip: "You earn ₹1.25 per 💗"

### Section 2 — Your Progress  *(updated)*
- "Next: Bright Star ⭐⭐⭐" pill on the right (or "Max Level 🏆" at L5)
- **Gold horizontal progress bar** — fills 0% → 100% as the creator gets closer to the next level
- **Caption below the bar** — tells the creator what she needs:
  - Below threshold: "Need ₹X more revenue to reach {next level name}"
  - At/above threshold: "Almost there — {next level name} is within reach"
  - L5: "🏆 Top tier — keep performing to maintain"
- "LAST 30 DAYS" stat block: revenue, calls, active days, rating, drop rate

### Section 3 — All Creator Levels
A list card showing all 5 levels stacked. Each level shows:
- Pink/gold L1–L5 badge
- Level name (current level highlighted with blue "YOU" pill)
- 5-star row with the right number of stars active
- **Plain-language qualification line**:
  - L1: "✨ Default — every new creator starts here"
  - L2: "💰 Earn ₹500 in 30 days · ⭐ 1.1+ rating"
  - L3: "💰 Earn ₹2,000 in 30 days · ⭐ 1.5+ rating"
  - L4: "💰 Earn ₹5,000 in 30 days · ⭐ 2.0+ rating"
  - L5: "💰 Earn ₹10,000 in 30 days · ⭐ 2.6+ rating"
- Rate chip on the right (₹1.20 / 💗 ... ₹1.65 / 💗)

### Section 4 — How is my level calculated?
5 factors with weight pills:
- 💰 Revenue earned — 50% (the biggest factor)
- 📞 Number of calls — 15%
- 📅 Active days — 15%
- ⭐ User rating — 10%
- 📵 Low drop rate — 10%
- Yellow tip strip: "💡 Revenue is the BIGGEST factor — 50%! Focus on getting more calls."

### Section 5 — Will I lose my level?
Green soft-decay card with 🌊 icon: "Gradual, never sudden. If your performance drops, you only fall ONE level at a time. You will NEVER crash from L5 to L1 overnight."

### Section 6 — What protects fair creators
4 protection cards:
- 🚫 Fake accounts don't count (less than ₹50 lifetime spend → not counted)
- 🔁 Repeat callers count less (same person 5+ times in a month → counts at 50%)
- ⏱️ Short calls don't count (under 1 minute is ignored)
- 😊 Low rating blocks promotion (if rated too low, can't reach higher levels)

### Section 7 — Real Example: Meet Devi
A yellow story card showing a 5-step example of a creator's journey from Day 1 to Month 2.

### Section 8 — Quick Summary
4 lines summarising the key takeaway (numbered 1️⃣–4️⃣).

### Section 9 — FAQs (8 questions, expandable)
1. How is my level calculated?
2. I think my level is wrong (with "Something wrong?" instructions)
3. Will my level reset every month?
4. Why do gift hearts and chat hearts pay differently?
5. What if I switch to a different language?
6. How do I withdraw my earnings?
7. Will there be a public leaderboard?
8. When does my level update?  *(added)*

Tapping any FAQ header expands its answer; tapping again collapses it.

### Section 10 — "Something wrong with your level?" link  *(new)*
At the very bottom of the page. Tap → opens Help and Support so the creator can raise a dispute.

---

## How the progress bar works

The bar lives in **Section 2 (Your Progress)** of the Creator Level page.

### What drives the bar
- The bar reads a `progressPercent` value (0–100) and fills accordingly
- Right now this value is **hardcoded to 45** in `CreatorLevelDummy.get()` because the backend endpoint doesn't exist yet
- Once `GET /api/auth/creator/my-level` ships, the backend will compute this value based on all 5 scoring factors weighted against the next level's thresholds

### What drives the caption
The caption is computed **on the client** using only revenue (the biggest, most controllable factor):

1. Look up the next level's revenue threshold:
   - L2 → ₹500
   - L3 → ₹2,000
   - L4 → ₹5,000
   - L5 → ₹10,000
2. Gap = threshold − current 30-day revenue
3. If gap > 0 → "Need ₹X more revenue to reach {next level name}"
4. If gap ≤ 0 → "Almost there — {next level name} is within reach"
5. If creator is already at L5 → "🏆 Top tier — keep performing to maintain"

### What you'll see right now (dummy data)
- Bar: 45% filled in gold
- Current level: L2 Rising Star
- Revenue (30 days): ₹2,100
- Next-level threshold (L3): ₹2,000
- Caption: "Almost there — Bright Star is within reach"
  *(because ₹2,100 already exceeds the ₹2,000 threshold)*

---

## Where data comes from today vs after backend ships

| Element | Today (dummy) | Once backend ships |
|---------|---------------|--------------------|
| Female Home — Creator Level subtitle ("L2 · Rising Star") | Hardcoded in XML | Bind to `my-level` API response |
| Profile — Creator Level subtitle ("Rising Star · ₹1.25 / 💗") | Hardcoded in XML | Bind to `my-level` API response |
| Creator Level page — hero, progress bar, stats | All from `CreatorLevelDummy.get()` (L2, ₹2,100, 247 calls, etc.) | All from `my-level` API response |
| All Creator Levels card | Static, all levels always shown | No change — list itself is static |
| Anti-gaming, soft-decay, FAQs, Devi story | Static text in layout | No change |
| Dispute "Level Dispute" category | Goes to Help & Support category list | Backend admin needs to add a "Level Dispute" category to the list |

---

## "Something wrong?" — dispute flow

The spec (BL-19) says creators who think their level is wrong should be able to raise a ticket from inside the app. The entry point is on the Creator Level page itself:

- **Creator Level page** — "Something wrong with your level?" link at the very bottom

Tapping it opens `HelpAndSupportActivity` (the same screen reached from Profile → Help and Support). The creator picks "Level Dispute" from the categories list, fills the ticket, and submits.

**What's needed on the backend side** for this to fully work:
- Add a "Level Dispute" entry in the support categories list (so it appears in the picker)
- Route those tickets to the support team that can override levels via the admin panel
- SLA: spec asks for 24–48h resolution

The UI is ready; the category just needs to exist server-side for it to be selectable.

---

## Files touched in this work

### Layouts
- `app/src/main/res/layout/activity_creator_level.xml` — added progress bar, caption, FAQ 8, "Something wrong?" link, qualification text on all 5 level cards
- `app/src/main/res/layout/fragment_profile_female.xml` — no badge-specific changes (Creator Level card already existed)

### Kotlin
- `app/src/main/java/com/gmwapp/hima/activities/CreatorLevelActivity.kt` — wired progress bar, caption logic, L5 max-tier message, FAQ 8 toggle, "Something wrong?" click; removed dead `nextRateFor()`
- `app/src/main/java/com/gmwapp/hima/fragments/ProfileFemaleFragment.kt` — no badge-specific changes (Creator Level card click already wired)
- `app/src/main/java/com/gmwapp/hima/models/CreatorLevelData.kt` — added `revenueThresholdFor(level)` helper

### Files NOT touched (still using existing implementation)
- `app/src/main/java/com/gmwapp/hima/fragments/FemaleHomeFragment.kt` — Creator Level card click already wired in earlier work
- `app/src/main/res/layout/fragment_female_home.xml` — Creator Level card already existed

---

## What's still open (NOT UI work)

These are deliberately left for backend / PM input:

1. **Backend endpoint `GET /api/auth/creator/my-level`** — needs to ship before subtitles and progress bar can show real per-creator data
2. **`CreatorLevelViewModel` + `CreatorLevelRepository`** — to be built when the endpoint is ready; will replace `CreatorLevelDummy.get()`
3. **PM call: Old `BadgeResponse` (per-minute audio/video badges)** — still rendered on Female Home alongside the new 5-tier card; PM to confirm whether to remove
4. **"Level Dispute" support category** — add to backend categories list so creators can pick it from the dispute flow
5. **Strings to `strings.xml`** — all level text is currently inline in layouts; needs externalising for the Kannada pilot translation
6. **Male user side** — no work needed per spec (badge system is creator-internal)

---

## Quick reference — key user actions

| User action | What happens |
|-------------|--------------|
| Female opens Profile | Sees Creator Level card with current level + rate |
| Female taps Creator Level card | Opens Creator Level page |
| Female taps "Something wrong?" on Creator Level page | Opens Help and Support → Categories |
| Female opens female home | Sees small "MY CREATOR LEVEL" card at the top |
| Female taps Creator Level on home | Opens Creator Level page |
| Female opens Creator Level page | Sees hero, progress bar, all 5 levels with qualifications, scoring breakdown, FAQs, dispute link |
| Female taps a FAQ header | Expands the answer |
| Female reaches L5 | Bar shows 100%, pill says "Max Level 🏆", caption says "🏆 Top tier — keep performing to maintain" |
