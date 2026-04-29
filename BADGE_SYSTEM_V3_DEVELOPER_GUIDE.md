# Hima Star-Level Badge System — v3 UI Changes & Backend Integration Guide

> **Audience:** Backend team and any new developer joining the project.
> **Source spec:** [Hima_Badge_System_Epic_Stories_v3.pdf](../) (PM-approved 2026-04-29)
> **Status:** UI complete. Backend endpoint not yet shipped — currently using dummy data.

---

## 1. What changed in v3 vs v2

The previous (v2) UI had a creator-facing progress bar, score breakdown, and weekly cliff. v3 walks all of that back. Key changes:

| Area | v2 | v3 (current) |
|------|----|--------------|
| Evaluation cycle | Score recalc every 6h for all | Per-creator **rolling 7-day cycle** anchored to her join date. Daily cron at 02:00 IST picks up creators whose `next_star_eval_at <= NOW()` |
| Creator-facing UI | Progress bar + 5-metric breakdown + score | **Minimal** — level + rate + 1-line motivational message |
| Progress bar | Visible to creators (BL-13 v2) | **Admin-only** (BL-13b v3) — never shown to creators |
| Per-level message | Did not exist | **NEW** — admin-editable per language (BL-13 v3) via `badge_level_messages` table |
| Pause feature | Open question | **Confirmed: no pause feature** for planned leave |
| Story count | 22 | 23 (added BL-13 admin messages + BL-13b internal score view) |

The 5 levels and rates are **unchanged**: L1 ₹1.20 · L2 ₹1.25 · L3 ₹1.32 · L4 ₹1.45 · L5 ₹1.65.

---

## 2. Three pages affected

| Page | File | What's shown |
|------|------|--------------|
| **Female Home** | `fragment_female_home.xml` + `FemaleHomeFragment.kt` | Small "MY CREATOR LEVEL" card (level + stars + rate chip), 7-DAY EARNINGS / 7-DAY CALLS card |
| **Profile (female)** | `fragment_profile_female.xml` + `ProfileFemaleFragment.kt` | Single-row "Creator Level" card with star icon + subtitle + chevron → opens Creator Level page |
| **Creator Level page** | `activity_creator_level.xml` + `CreatorLevelActivity.kt` | The full info screen (hero, last-7-day stats, all 5 levels, anti-gaming protections, quick summary, 4 FAQs, support link) |

---

## 3. Files modified (v3 work)

### Layouts
- `app/src/main/res/layout/activity_creator_level.xml` — heavy rewrite: removed progress bar, "How is my level calculated?" section, "Will I lose my level?" section, "Meet Devi" example, FAQs 2/4/7/8, user-rating + drop-rate stat rows. Added motivational message TextView, "Something wrong?" link, plain-language qualification text on each level card. Updated 30 day → 7 day labels everywhere.
- `app/src/main/res/layout/fragment_female_home.xml` — only label changes: 30-DAY EARNINGS → 7-DAY EARNINGS, 30-DAY CALLS → 7-DAY CALLS.
- `app/src/main/res/layout/fragment_profile_female.xml` — no functional changes in v3.

### Kotlin
- `app/src/main/java/com/gmwapp/hima/activities/CreatorLevelActivity.kt` — wires the hero card, motivational message, last-7-day stats, FAQ accordions, and "Something wrong?" support intent.
- `app/src/main/java/com/gmwapp/hima/models/CreatorLevelData.kt` — data class mirrors the future API response. Includes `CreatorLevelDummy` singleton with hardcoded L2 data + helper methods.
- `app/src/main/java/com/gmwapp/hima/fragments/ProfileFemaleFragment.kt` — wires the Creator Level card click → opens `CreatorLevelActivity`. No badge data binding (subtitle is hardcoded XML).
- `app/src/main/java/com/gmwapp/hima/fragments/FemaleHomeFragment.kt` — Creator Level card click → opens `CreatorLevelActivity`. The "MY CREATOR LEVEL" card content is hardcoded XML.

### Files NOT modified (carry-over from earlier sessions)
- `cv_female_discovery` "Creators Joining Now" section on female home — pre-existing, fed by `POST /female_discovery` API + fake "X min ago" client-side
- `cl_star_creator_banner` legacy Star Creator banner — pre-existing, gated by `users.star == 1`

---

## 4. Backend integration points — what's still missing

These are explicit hand-offs to the backend team. Each one corresponds to a v3 BL story or a wiring touch-point.

### 4.1 `GET /api/auth/creator/my-level` — primary endpoint (BL-12)

**Status:** Not shipped. Android currently uses `CreatorLevelDummy.get()`.

**Required response shape** (mirrors `CreatorLevelData` data class):

```json
{
  "success": true,
  "data": {
    "level": 2,                          // int 1..5
    "level_name": "Rising Star",         // string
    "rate_per_star": 1.25,               // float (per-heart payout)
    "next_level": 3,                     // int (or 5 if at max)
    "next_level_name": "Bright Star",
    "calls_7d": 247,                     // int (last 7 days)
    "active_days_7d": 5,                 // int
    "revenue_7d": 2100.00,               // float (last 7 days, INR)
    "custom_message": "Great start. Stay active to climb to Bright Star",
    "is_max_level": false                // boolean
  }
}
```

**What the client uses each field for:**
- `level` → highlights the right number of stars (gold) on hero card and level cards
- `level_name` → hero card title
- `rate_per_star` → hero card rate chip ("You earn ₹X.XX per 💗")
- `next_level`, `next_level_name` → "Next: Bright Star ⭐⭐⭐" pill (still rendered; remove from UI if v3 strict interpretation)
- `calls_7d`, `active_days_7d`, `revenue_7d` → Last 7 Days stats card
- `custom_message` → motivational text below stars on hero card (NEW in v3)
- `is_max_level` → drives "Max Level 🏆" pill text instead of "Next: ..."

**Fields the spec mentions but UI does NOT need** (admin-only — never expose to creator API):
- `composite_score`, `percentile_rank`, `gap_to_next_level` — these belong in the admin dashboard (BL-13b), NOT in `my-level`.

**What replaces the dummy:**
1. Build `CreatorLevelRepository` + `CreatorLevelViewModel` (pattern: same as `BadgeRepository` / `BadgeViewModel`).
2. In `CreatorLevelActivity.onCreate()` — replace `populateLevel(CreatorLevelDummy.get())` with `viewModel.fetchCreatorLevel()` + observe `myLevelLiveData`.
3. Bind the same `CreatorLevelData` shape — no UI changes needed.

### 4.2 `badge_level_messages` table + admin UI (BL-13)

**Status:** Not shipped. Default messages currently live in `CreatorLevelDummy.defaultMessageFor(level)` for fallback.

**DB schema (per spec):**
```
badge_level_messages (
  level       INT,
  language    VARCHAR,
  message     TEXT,
  updated_by  ...,
  updated_at  TIMESTAMP
)
```

**Default seed values (already hardcoded in `CreatorLevelData.kt` as fallback):**
- L1: "Welcome! Make more calls to reach Rising Star and earn higher rate"
- L2: "Great start. Stay active to climb to Bright Star"
- L3: "You're doing well. Keep going to reach Super Star"
- L4: "Almost there — top performers earn the highest rate"
- L5: "🏆 You're a top creator. Keep up the excellent work"

**Admin requirements (out of Android scope):**
- New admin page `/admin/badge-messages` — list of 5 levels × N languages, inline edit, save
- Audit log per edit (admin name + timestamp + old/new text)
- Cache messages in app for 5 min to reduce DB hits
- The text returned via `GET /api/auth/creator/my-level` must respect the creator's `language` column

**Client behavior:**
- The client just renders whatever string is in `custom_message`. No client-side switching by level once API ships — backend picks the message and returns it pre-resolved.

### 4.3 Per-language toggle (BL-17)

**Column:** `language_configs.badge_enabled BOOLEAN DEFAULT 0` (extend existing table from autopay).

**Server logic:**
- If creator's `language_configs.badge_enabled = 0` → API returns flat ₹1.37/heart and the client should hide the badge UI (or show level 0/disabled state).
- Initial seed: Kannada = ON, all others = OFF (per BL-22 master flag).

**Client TODO once shipped:**
- The `my-level` endpoint should return null/error for disabled languages, OR return a flag `badge_enabled: false`. Decide the contract with backend.
- Client should fall back to the legacy flat rate UI when disabled.

### 4.4 "Level Dispute" support category (BL-19)

**Status:** UI ready, backend missing.

The "Something wrong with your level?" link at the bottom of [activity_creator_level.xml](app/src/main/res/layout/activity_creator_level.xml) opens `HelpAndSupportActivity`. The creator picks a category from the list.

**Backend TODO:**
- Add a "Level Dispute" entry to the support categories list (the same list rendered by `getCategoriesList(userId, language)` API).
- Route those tickets to the team that has access to the admin dashboard's level-override modal (BL-16).
- Set SLA target: 24–48h (BL-20). Add `support_tickets.target_resolution` column.

### 4.5 Per-creator weekly evaluation cycle (BL-02)

**Backend-only, no client work needed.** Listed for awareness.

- New column `users.next_star_eval_at TIMESTAMP`
- Initialised to `(created_at + 7 days)` for existing creators
- Daily cron at 02:00 IST runs `creators:recalc-star-score` artisan
- Picks up only creators where `next_star_eval_at <= NOW()`, recomputes score, updates level (with soft decay), advances `next_star_eval_at` by +7 days

This means a creator who joined Tuesday gets evaluated every Tuesday. No fixed weekday for everyone.

### 4.6 Mid-call rate snapshot (BL-07)

**Backend-only.** New column `user_calls.level_at_start TINYINT`. At call-start, snapshot the creator's level. At call-end, compute payout using `level_at_start` (not current level). Audit trail for disputes.

### 4.7 Per-call payout (BL-06)

**Backend-only.** Add `user_calls.rate_applied DECIMAL(4,2)` storing the rate used for that specific call. Income calc in `AuthController` uses `getCreatorRate($userId, $type)` helper, falls back to flat ₹1.37 if `language_configs.badge_enabled = 0` or `users.star_level IS NULL`.

---

## 5. Dummy / mock layers — what to remove when API ships

| Mock | Where | Replace with |
|------|-------|--------------|
| `CreatorLevelDummy.get()` | `models/CreatorLevelData.kt` | API call via `CreatorLevelViewModel.fetchCreatorLevel()` |
| `CreatorLevelDummy.defaultMessageFor(level)` | `models/CreatorLevelData.kt` | Keep as **offline fallback** if backend returns null `custom_message` |
| `CreatorLevelDummy.revenueThresholdFor(level)` | `models/CreatorLevelData.kt` | Currently unused after v3 changes — safe to delete unless you want it for client-side validation |
| `// TODO: replace with real API call → viewModel.fetchCreatorLevel()` | `CreatorLevelActivity.kt:25` | Real ViewModel call |
| Hardcoded subtitle `"Rising Star · ₹1.25 / 💗"` | `fragment_profile_female.xml` (`tv_creator_level_profile_sub`) | Bind from `my-level` API: `"${data.level_name} · ₹${data.rate} / 💗"` |
| Hardcoded `"L2 · Rising Star"` + 5-star row + rate chip | `fragment_female_home.xml` (`tv_creator_level_name` etc.) | Bind from `my-level` API |
| Hardcoded `tv_approx_earnings.text = "2,100"` and `tv_total_calls.text = "247"` | `FemaleHomeFragment.kt:638-639` | Bind from new fields `last_7d_earnings` / `last_7d_calls` (TODO comment already in place at line 635) |
| `"Random.nextInt(1, 10)"` for "X min ago" on Creators Joining Now | `FemaleHomeFragment.kt:791` | Either drop the section per spec, or backend should return real `joined_minutes_ago` |

### Quick search to find all dummy references
```
grep -rn "CreatorLevelDummy\|TODO.*[Bb]adge\|TODO.*[Ll]evel\|TODO.*api\|TODO.*backend should" app/src/main
```

---

## 6. SharedPreferences keys

There are **no badge-specific SharedPreferences keys** today. All persistent badge state lives on the server. The only SP usage in badge-related code paths is the existing user-data prefs:

| Key/Method | Where | Used for |
|------------|-------|---------|
| `BaseApplication.getInstance()?.getPrefs()?.getUserData()` | `FemaleHomeFragment.kt:310`, `ProfileFemaleFragment.kt`, etc. | Reads `userData.id`, `userData.gender`, `userData.star`, `userData.language` — needed to gate the legacy Star Creator banner (`star == 1`) and to call `getFemaleDiscovery(userId)` |
| `userData.language` | implicitly through `getCategoriesList(userId, language)` | Will also drive which language's `custom_message` is fetched once `my-level` API ships |

### Recommendation when wiring the API

Add a small SP cache to avoid hitting `my-level` on every screen open:

```
Suggested SP keys (NEW — to be added):
  badge_level_cache_json     // serialised CreatorLevelData
  badge_level_cache_ts       // last-fetched timestamp (millis)
```

Cache invalidation: 5 minutes (matches admin-message cache TTL in BL-13). On Profile / Female Home / Creator Level page open, return cached data if fresh, else fire API.

This keeps three pages consistent without three round-trips.

---

## 7. Verification — v3 acceptance criteria vs current UI

| BL story | Acceptance | UI status |
|----------|-----------|-----------|
| **BL-12** Profile Level Badge + Rate + Custom Message | Star badge ⭐ to ⭐⭐⭐⭐⭐ | ✅ Hero card on Creator Level page |
| | Level name | ✅ |
| | Per-heart rate displayed | ✅ "You earn ₹X.XX per 💗" |
| | Custom motivational message below level name | ✅ `tv_hero_message` (dummy fallback in place) |
| | NEVER show composite score, percentile, progress bar | ✅ All removed |
| | NO progress percentage, NO metric breakdown | ⚠️ Last 7 Days stats card still shows Revenue/Calls/Active Days. Strict v3 says remove. **Deferred.** |
| | Tap badge → opens info screen | ✅ Profile + Female Home cards both open `CreatorLevelActivity` |
| **BL-13** Admin custom messages | Default messages exist + admin UI | ⚠️ Defaults exist client-side as fallback; admin UI is backend scope |
| **BL-13b** Internal admin score view | Composite score, percentile, gap visible to admin only | N/A on client (admin work) |
| **BL-14** No push on level change | Score-calc job does NOT push notifications | ✅ Client-side: no banner / no push handler — non-feature |
| **BL-19** Support ticket flow — Level Disputes | "Something wrong?" link → ticket with category "Level Dispute" | ⚠️ Link exists on Creator Level page → opens HelpAndSupportActivity. **Backend must add the category** for the dropdown to include it. |

---

## 8. UI sections removed per v3

These were deliberately removed and should not be added back:

| Section | Why |
|---------|-----|
| Progress bar to next level (`pb_next_level`) | v3: never show progress to creators |
| "Need ₹X more revenue" caption (`tv_progress_caption`) | v3: no progress info |
| User Rating stat row (`tv_stat_rating`) | v3: no metric breakdown |
| Drop Rate stat row (`tv_stat_drop_rate`) | v3: no metric breakdown |
| "How is my level calculated?" weights section | v3: no metric breakdown |
| "Will I lose my level?" soft-decay card | v3: simplify creator view |
| "📖 Real Example — Meet Devi" story card | v3: simplify creator view |
| FAQ 2 "I think my level is wrong" | Replaced by the "Something wrong?" link |
| FAQ 4 "Why do gift hearts and chat hearts pay differently?" | Out of scope; gift/chat hearts not in app |
| FAQ 7 "Will there be a public leaderboard?" | Out of scope |
| FAQ 8 "When does my level update?" (was a duplicate after rename) | Question already covered by FAQ 1 |
| Profile-side "Something wrong?" link | Single entry point on Creator Level page is enough |
| Per-level qualification "Top X%" jargon | Replaced with plain language: "Earn ₹500 in 7 days · ⭐ 1.1+ rating" |

---

## 9. UI items still pending (deferred — not blocking)

- **Strict v3:** remove "Last 7 Days" stats card from Creator Level page (still shows Revenue / Calls / Active Days personal numbers — debatable per "no metric breakdown")
- **Strict v3:** remove "Next: Bright Star ⭐⭐⭐" pill (`tv_next_level_badge`)
- **Decide:** remove the legacy "Creators Joining Now" section on Female Home (`cv_female_discovery`) — fake "X min ago" + creator-vs-creator visibility conflicts with no-leaderboard policy
- **Decide:** legacy `cl_star_creator_banner` (Female Home) — gated by old `users.star == 1` flag; under v3 every female creator has a level so the gate is stale
- **Decide:** Old `BadgeResponse` (`POST badges_information_list`, per-minute audio/video badges) — coexists with the 5-tier card on Female Home. PM call.
- **i18n:** all badge strings are inline in layouts. For Kannada pilot, externalise to `strings.xml` with per-locale variants.

---

## 10. Migration checklist for the backend team

When `GET /api/auth/creator/my-level` is ready, this is the sequence to wire the client:

1. **Confirm response shape** matches the JSON above. If you change field names, update `CreatorLevelData.kt` to match.
2. **Build retrofit interface:** add `@GET("auth/creator/my-level")` method to `ApiInterface.kt`, returning `Call<CreatorLevelResponse>`.
3. **Build wrapper:** `ApiManager.getCreatorMyLevel(callback)`.
4. **Build repository + ViewModel:**
   - `CreatorLevelRepository.kt` (mirror `BadgeRepository`)
   - `CreatorLevelViewModel.kt` (annotate `@HiltViewModel`, expose `myLevelLiveData: MutableLiveData<CreatorLevelData>`)
5. **Switch CreatorLevelActivity:**
   - Replace `populateLevel(CreatorLevelDummy.get())` with:
     ```kotlin
     viewModel.fetchCreatorLevel()
     viewModel.myLevelLiveData.observe(this) { populateLevel(it) }
     ```
   - Keep `CreatorLevelDummy` as offline fallback if the API call errors.
6. **Wire Profile subtitle:**
   - Bind `tv_creator_level_profile_sub` to API response — observe in `ProfileFemaleFragment.kt`.
7. **Wire Female Home card:**
   - Bind `tv_creator_level_name`, 5-star row, rate chip from API — observe in `FemaleHomeFragment.kt`.
8. **Add SP cache layer** (optional — see section 6).
9. **Add the "Level Dispute" support category** to the categories endpoint (BL-19).
10. **Verify**:
    - L1 creator sees "Welcome! Make more calls..." message
    - L5 creator sees "🏆 You're a top creator..." message
    - Switching languages returns the correct localised message
    - Disabled-language creator falls back to flat ₹1.37 (badge UI hidden)

---

## 11. Quick reference

### Key data class
[`models/CreatorLevelData.kt`](app/src/main/java/com/gmwapp/hima/models/CreatorLevelData.kt) — mirrors API response. The `customMessage` field is the v3-introduced motivational text.

### Key activity
[`activities/CreatorLevelActivity.kt`](app/src/main/java/com/gmwapp/hima/activities/CreatorLevelActivity.kt) — entry point for the badge info screen. Look here first when wiring the API.

### Key layout
[`res/layout/activity_creator_level.xml`](app/src/main/res/layout/activity_creator_level.xml) — all the badge UI lives here (hero, stats, level cards, anti-gaming, summary, FAQs, support link).

### v3 spec (source of truth)
[Hima_Badge_System_Epic_Stories_v3.pdf](../) (in user's Downloads). PM-approved 2026-04-29.

---

*Document version: 1.0 — generated to capture v3 UI handoff state. Update when the API ships and this guide can drop the "Dummy / mock layers" section.*
