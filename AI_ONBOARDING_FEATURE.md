# Hima AI-Powered Onboarding Feature

## Overview

AI-powered onboarding for new male users. When a male user registers, instead of landing on an empty home screen, they go through an empathetic AI conversation that understands their emotional state, talks to them about it, then intelligently matches them with the best available female creators — sending ice-breaker messages on their behalf so they immediately see active conversations.

**Branch:** `ai_onboarding` (created from `IPL_Fixes`)
**Backend Branch:** `creator_warning_system` on `innovfix/hima-admin-panel`

---

## User Flow

```
Register (Male) → Select Language → AiOnboardingActivity
  → Screen 1: Pick concern (Breakup / Loneliness / Stress / Boredom)
  → Screen 2: Multi-turn AI chat (empathize → follow-up → reassurance)
  → Screen 3: Matched creators list with Audio/Video buttons
  → MainActivity (creators already messaged, chats visible)
```

---

## Three Screens

### Screen 1: Concern Selection
- 4 MaterialCardView cards in a 2x2 grid
- Each card has an emoji + Tanglish text (regional language words in English/Roman script)
- All 11 languages supported: Tamil, Hindi, Telugu, Kannada, Malayalam, Marathi, Bengali, Assamese, Odia, Gujarati, Punjabi
- Example (Tamil): "Inniki eppadi feel pannureenga?" with cards "Breakup", "Thanimai", "Stress", "Boring"

| Concern | Emoji | Card Color |
|---------|-------|------------|
| Breakup | 💔 | Light Red (#FFF1F2) |
| Loneliness | 😔 | Light Blue (#EFF6FF) |
| Stress | 😰 | Light Orange (#FFF7ED) |
| Boredom | 😴 | Light Green (#F0FDF4) |

### Screen 2: AI Chat
- Chat-style UI with AI messages (gray, left-aligned) and user messages (pink accent, right-aligned)
- "Hima" header bar with typing indicator
- AI responds in **Tanglish** (e.g., "Ayyo, breakup aachaa? Romba kastama irukum da...")
- Multi-turn conversation — no fixed step limit
- After 3+ user messages, AI naturally wraps up: "Don't worry, we have wonderful people here..."
- Input bar hides and "Let's Go! Meet Your Matches" button appears

### Screen 3: Matched Creators List
- Title: "We found people for you!"
- RecyclerView of matched creators with:
  - Profile image (CircleImageView)
  - Name and language
  - Green online indicator
  - Audio/Video call buttons (conditionally shown)
- Tapping a creator opens ChatActivityInHouse
- "Start Chatting" button navigates to MainActivity

---

## Architecture

### Pattern: MVVM + Repository + Hilt DI

```
AiOnboardingActivity (View)
    ↓ observes LiveData
AiOnboardingViewModel (@HiltViewModel)
    ↓ delegates to
AiOnboardingRepository (@Inject)
    ↓ calls
ApiManager (Retrofit)
    ↓ HTTP POST
Backend API (/api/auth/ai_onboarding_*)
    ↓ calls
OpenRouterService (GPT via OpenRouter)
```

---

## Android Files

### New Files Created

| File | Purpose |
|------|---------|
| `activities/AiOnboardingActivity.kt` | Main 3-screen activity |
| `adapters/AiChatAdapter.kt` | Chat bubble adapter (AI left, user right) |
| `adapters/MatchedCreatorAdapter.kt` | Creator cards with call buttons |
| `viewmodels/AiOnboardingViewModel.kt` | LiveData for 3 API calls |
| `repositories/AiOnboardingRepository.kt` | Delegates to ApiManager |
| `responses/AiOnboardingStartResponse.kt` | `{success, session_id, ai_message, step}` |
| `responses/AiOnboardingReplyResponse.kt` | `{success, ai_message, step, is_complete}` |
| `responses/AiOnboardingCompleteResponse.kt` | `{success, matched_creators[], messages_sent}` |
| `layout/activity_ai_onboarding.xml` | ViewFlipper with 3 screens + loading overlay |
| `layout/item_ai_chat_ai.xml` | Gray bubble (AI message) |
| `layout/item_ai_chat_user.xml` | Pink bubble (user message) |
| `layout/item_matched_creator.xml` | Creator card with profile + call buttons |

### Modified Files

| File | Change |
|------|--------|
| `SelectLanguageActivity.kt` (line 80) | Routes new male users to `AiOnboardingActivity` instead of `MainActivity` |
| `ApiManager.kt` | Added 3 API interface methods + 3 wrapper methods + 3 response imports |
| `AndroidManifest.xml` | Registered `AiOnboardingActivity` |
| `FemaleUserAdapter.kt` | Added Audio/Video call buttons to creator cards |
| `adapter_female_user.xml` | Added call_buttons_row layout |

---

## Backend API Endpoints

### Base URL: `POST /api/auth/`

All endpoints require `Authorization: Bearer <JWT>` header.

### 1. `ai_onboarding_start`

**Input:**
| Field | Type | Required |
|-------|------|----------|
| `user_id` | int | Yes |
| `concern` | string | Yes (breakup/loneliness/stress/boredom) |

**Response:**
```json
{
  "success": true,
  "session_id": 1,
  "ai_message": "Ayyo, breakup aachaa? Romba kastama irukum da...",
  "step": 0
}
```

**Logic:**
- Guards: 1 onboarding per user lifetime (`ai_onboarding_completed` flag)
- Reuses existing session if one exists within 24h
- Calls `OpenRouterService::generateOnboardingEmpathy()` for AI response
- Falls back to pre-written English message if AI fails
- Creates `ai_onboarding_sessions` record

### 2. `ai_onboarding_reply`

**Input:**
| Field | Type | Required |
|-------|------|----------|
| `session_id` | int | Yes |
| `user_message` | string | Yes |

**Response:**
```json
{
  "success": true,
  "ai_message": "Naan purinjukuren da... Enna nadanthathu sollu?",
  "step": 1,
  "is_complete": false
}
```

**Logic:**
- Multi-turn: no fixed step limit (max 10 safety cap)
- After 3+ user messages, signals AI to wrap up (`$wrapUp = true`)
- AI naturally transitions to "we have people here for you"
- Returns `is_complete: true` after 3+ exchanges

### 3. `ai_onboarding_complete`

**Input:**
| Field | Type | Required |
|-------|------|----------|
| `session_id` | int | Yes |

**Response:**
```json
{
  "success": true,
  "matched_creators": [
    {"id": 123, "name": "Priya", "image": "...", "language": "Tamil", "audio_status": 1, "video_status": 1}
  ],
  "messages_sent": true
}
```

**Logic:**
1. Finds top 5 creators by: `language match` → `online (5min)` → `random_audio_score DESC` → `avg_call_percentage DESC`
2. Fallback: if <3 online, relaxes to 30min window; if still 0, picks top-scoring regardless
3. For each creator:
   - Generates ice-breaker message: "Hi {name}! I'm {concern_label}. Would love to talk with you!"
   - Inserts into `chat_messages`, `chats`, `active_chats` tables (same schema as socket-server)
4. Sets `users.ai_onboarding_completed = 1`

---

## Backend Files

### New Files

| File | Purpose |
|------|---------|
| `app/Models/AiOnboardingSession.php` | Eloquent model for sessions |
| `database/migrations/2026_04_16_*_create_ai_onboarding_sessions_table.php` | DB migration |

### Modified Files

| File | Change |
|------|--------|
| `app/Services/OpenRouterService.php` | Added `callOnboardingApi()`, `generateOnboardingEmpathy()`, `generateOnboardingFollowup()` |
| `app/Http/Controllers/AuthController.php` | Added `ai_onboarding_start()`, `ai_onboarding_reply()`, `ai_onboarding_complete()` |
| `routes/api.php` | Added 3 routes inside auth middleware group |

---

## Database

### New Table: `ai_onboarding_sessions`

| Column | Type | Description |
|--------|------|-------------|
| `id` | bigint PK | Auto-increment |
| `user_id` | int (indexed) | Foreign key to users |
| `language` | varchar(50) | User's language |
| `concern` | varchar(50) | breakup/loneliness/stress/boredom |
| `conversation_history` | JSON | Full AI conversation `[{role, content}]` |
| `step` | tinyint | Progress counter (0-10) |
| `matched_creator_ids` | JSON | Array of matched creator user IDs |
| `messages_sent` | boolean | Whether ice-breakers were sent |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

### Altered Table: `users`

| Column | Type | Description |
|--------|------|-------------|
| `ai_onboarding_completed` | tinyint (default 0) | 1 = onboarding done, prevents repeat |

---

## AI Integration

### Provider: OpenRouter API
- **Base URL:** `https://openrouter.ai/api/v1/chat/completions`
- **API Key:** Stored in `gateway_config` DB table (key: `openrouter_api_key`)
- **Model:** Configured via `gateway_config` (key: `openrouter_model`)

### Custom Method: `callOnboardingApi()`
Separate from the main `callApi()` to support custom system prompts. Direct cURL call to OpenRouter with:
- Custom system prompt (Tanglish persona)
- Temperature: 0.7 (warm, creative)
- Max tokens: 300

### System Prompt (Tanglish Style)
```
You are Hima, a warm and caring emotional companion inside a social calling app.
You MUST speak in Tanglish style - that means {language} words but written in 
ENGLISH/ROMAN script (NOT native script).
For example if language is Tamil: 'Enna achu? Romba kashtama irukka?'
Be empathetic, gentle, and brief (2-3 sentences).
Do NOT mention you are an AI. Speak like a caring friend.
IMPORTANT: Use ONLY English/Roman alphabet letters. NO native script characters.
```

### Wrap-Up Prompt (after 3+ exchanges)
```
The user has been sharing their feelings with you. Now warmly wrap up the conversation.
Tell them: you have wonderful real people on this app who are ready to listen and 
talk with them RIGHT NOW.
```

---

## Cost Control

| Control | Implementation |
|---------|---------------|
| Per-user limit | `ai_onboarding_completed` flag prevents repeat (1 per lifetime) |
| Session reuse | Existing session within 24h is returned instead of creating new |
| Max AI calls | ~4-5 per user (1 start + 2-3 replies + 0 complete) |
| Max steps | Hard cap at 10 steps |
| Cost per user | ~$0.01-0.03 at GPT-4o-mini rates |

---

## Error Handling

### Backend
- If OpenRouter fails: returns pre-written fallback messages in English
- If creator matching finds 0 online: relaxes window (30min → all-time)
- If chat message insert fails: logs error, continues with remaining creators

### Android
- If `ai_onboarding_start` fails: shows fallback message + "Let's Go" button (skips AI chat)
- If `ai_onboarding_reply` fails: re-enables input, shows error
- If `ai_onboarding_complete` fails: navigates to MainActivity directly
- No network: `onNoNetwork()` fires immediately with error message

---

## Language Support

All 11 languages with Tanglish translations:

| Language | Breakup | Loneliness | Stress | Boredom | Title |
|----------|---------|------------|--------|---------|-------|
| Tamil | Breakup | Thanimai | Stress | Boring | Inniki eppadi feel pannureenga? |
| Hindi | Breakup | Akela | Tension | Boring | Aaj kaisa feel kar rahe ho? |
| Telugu | Breakup | Ontaritanam | Stress | Boring | Ee roju ela feel avthunnaru? |
| Kannada | Breakup | Onti | Stress | Boring | Ivattu heg feel agthidira? |
| Malayalam | Breakup | Ekanatha | Stress | Boring | Innu engane feel cheyyunnu? |
| Marathi | Breakup | Ekta | Tension | Boring | Aaj kasa feel hotay? |
| Bengali | Breakup | Ekla | Tension | Boring | Aaj kemon feel korcho? |
| Assamese | Breakup | Okola | Tension | Boring | Aji kenekuwa feel korisaa? |
| Odia | Breakup | Ekalaa | Tension | Boring | Aaji kemiti feel karuchha? |
| Gujarati | Breakup | Ekla | Tension | Boring | Aaje kem feel thay chhe? |
| Punjabi | Breakup | Ikalla | Tension | Boring | Ajj kiven feel kar rahe ho? |

---

## Existing User Flow (Unchanged)

- Returning male users: `SplashScreenActivity` → `MainActivity` (skips onboarding)
- Female users: `SelectLanguageActivity` → `MainActivity` (no onboarding)
- `ai_onboarding_completed = 1` users: API returns error, activity navigates to Main
