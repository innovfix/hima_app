# Feedback Form API

Spec for an admin-configurable feedback form (Google-Forms-style, 1..N questions of mixed types) that the Android client shows once on app launch to a targeted list of user IDs.

The Android client is implementation-complete against this contract — endpoint paths, field names, and the JSON shapes below are load-bearing. Please match them exactly. Backend team owns DB migration, endpoint implementation, validation, and admin auth.

---

## 1. Database schema

Five tables. Names and column names assumed by the client only via the JSON wire format; the column shapes below are recommendations.

### `feedback_forms`
| column        | type                   | notes |
|---------------|------------------------|-------|
| `id`          | INT PK AUTO_INCREMENT  | |
| `title`       | VARCHAR(255) NOT NULL  | shown as dialog title |
| `description` | TEXT NULL              | shown under title; hidden when null/blank |
| `is_active`   | TINYINT(1) DEFAULT 1   | only active forms are served |
| `allow_skip`  | TINYINT(1) DEFAULT 1   | when 0, `/feedback/skip` returns 403 |
| `created_at`  | DATETIME               | |
| `updated_at`  | DATETIME               | |

### `feedback_questions`
| column          | type                                                  | notes |
|-----------------|-------------------------------------------------------|-------|
| `id`            | INT PK AUTO_INCREMENT                                 | |
| `form_id`       | INT FK → `feedback_forms.id` (ON DELETE CASCADE)      | |
| `sort_order`    | INT NOT NULL                                          | client renders `ORDER BY sort_order` |
| `question_text` | TEXT NOT NULL                                         | the question itself |
| `help_text`     | TEXT NULL                                             | small grey subtitle under the question (Google-Forms-style) |
| `question_type` | ENUM(`rating`,`text`,`single_choice`,`multi_choice`)  | one of these four exact strings |
| `is_required`   | TINYINT(1) DEFAULT 0                                  | when 1, client shows red `*` and blocks submit if unanswered |
| `min_rating`    | INT NULL                                              | only for `rating`; default 1 if null |
| `max_rating`    | INT NULL                                              | only for `rating`; default 5 if null |
| `max_length`    | INT NULL                                              | only for `text`; client enforces counter limit |
| `options`       | JSON NULL                                             | only for `single_choice` / `multi_choice` — array of strings |
| `created_at`    | DATETIME                                              | |
| `updated_at`    | DATETIME                                              | |

### `feedback_form_targets`
| column          | type                                              | notes |
|-----------------|---------------------------------------------------|-------|
| `id`            | INT PK AUTO_INCREMENT                             | |
| `form_id`       | INT FK → `feedback_forms.id`                      | |
| `user_id`       | INT FK → `users.id`                               | |
| `status`        | ENUM(`pending`,`submitted`,`skipped`) DEFAULT `pending` | drives `should_show` |
| `shown_count`   | INT DEFAULT 0                                     | incremented on each `/feedback/check` that returns this form |
| `submitted_at`  | DATETIME NULL                                     | set on submit/skip |
| —               | UNIQUE (`form_id`,`user_id`)                      | |
| —               | INDEX (`user_id`,`status`)                        | hot path for `/feedback/check` |

### `feedback_responses`
| column          | type                                       | notes |
|-----------------|--------------------------------------------|-------|
| `id`            | INT PK AUTO_INCREMENT                      | |
| `form_id`       | INT FK → `feedback_forms.id`               | |
| `user_id`       | INT FK → `users.id`                        | |
| `skipped`       | TINYINT(1) DEFAULT 0                       | 1 for skip, 0 for submit |
| `submitted_at`  | DATETIME NOT NULL                          | |

### `feedback_answers`
| column           | type                                            | notes |
|------------------|-------------------------------------------------|-------|
| `id`             | INT PK AUTO_INCREMENT                           | |
| `response_id`    | INT FK → `feedback_responses.id` (CASCADE)      | |
| `question_id`    | INT FK → `feedback_questions.id`                | |
| `answer_text`    | TEXT NULL                                       | for `text` |
| `answer_rating`  | INT NULL                                        | for `rating` |
| `answer_options` | JSON NULL                                       | array of strings; for single_choice (length 1) and multi_choice |

---

## 2. Consumer endpoints (called by the Android app)

All endpoints are `application/x-www-form-urlencoded`. Auth via the existing user token middleware.

### 2.1 `POST /feedback/check`

Returns the next pending form for the user, or `should_show=false` if none.

**Request**
```
user_id=123
```

**Server logic**
1. Look up the oldest `feedback_form_targets` row where `user_id=:user_id` AND `status='pending'`, joined to `feedback_forms` where `is_active=1`.
2. If none, respond with `should_show=false` and `form=null`.
3. Otherwise increment `shown_count`, fetch the question list ordered by `sort_order`, and return the form.

**Sample success (form available)**
```json
{
  "success": true,
  "should_show": true,
  "form": {
    "id": 7,
    "title": "Quick feedback",
    "description": "Help us improve. Takes under a minute.",
    "allow_skip": true,
    "questions": [
      {
        "id": 21,
        "sort_order": 1,
        "question_text": "How would you rate the call quality this week?",
        "help_text": "1 = poor, 5 = excellent",
        "question_type": "rating",
        "is_required": true,
        "min_rating": 1,
        "max_rating": 5,
        "max_length": null,
        "options": null
      },
      {
        "id": 22,
        "sort_order": 2,
        "question_text": "Which features did you use today?",
        "help_text": null,
        "question_type": "multi_choice",
        "is_required": false,
        "min_rating": null,
        "max_rating": null,
        "max_length": null,
        "options": ["Audio call", "Video call", "Chat", "Gifts"]
      },
      {
        "id": 23,
        "sort_order": 3,
        "question_text": "Anything else you want us to know?",
        "help_text": "Optional",
        "question_type": "text",
        "is_required": false,
        "min_rating": null,
        "max_rating": null,
        "max_length": 500,
        "options": null
      }
    ]
  },
  "message": "Form available"
}
```

**Sample success (no form)**
```json
{
  "success": true,
  "should_show": false,
  "form": null,
  "message": "No pending form"
}
```

**curl**
```bash
curl -X POST https://api.example.com/feedback/check \
  -H "Authorization: Bearer <token>" \
  -d "user_id=123"
```

---

### 2.2 `POST /feedback/submit`

Records answers and marks the target as `submitted`.

**Request**
```
user_id=123
form_id=7
answers=[{"question_id":21,"answer_rating":5},{"question_id":22,"answer_options":["Audio call","Chat"]},{"question_id":23,"answer_text":"Thanks!"}]
```

`answers` is a JSON-encoded string. Each entry contains `question_id` and exactly one of `answer_text`, `answer_rating`, or `answer_options` depending on the question type:

| question_type    | required field                  |
|------------------|---------------------------------|
| `rating`         | `answer_rating` (int)           |
| `text`           | `answer_text` (string)          |
| `single_choice`  | `answer_options` (array, len 1) |
| `multi_choice`   | `answer_options` (array)        |

**Validation**
- Required questions (`is_required=1`) MUST be present in the `answers` array with a non-null value of the right type.
- `rating`: `min_rating <= answer_rating <= max_rating` (use defaults 1/5 when null).
- `text`: `length(answer_text) <= max_length` when `max_length` is set.
- `single_choice` / `multi_choice`: every entry in `answer_options` must be one of the question's `options`.
- `form_id` must match a form that has a `pending` target row for `user_id`. If the target is already `submitted` or `skipped`, return 409.

On success: insert one `feedback_responses` row (`skipped=0`), insert N `feedback_answers` rows, set the target row's `status='submitted'` and `submitted_at=NOW()`.

**Sample success**
```json
{ "success": true, "message": "Feedback submitted" }
```

**Sample validation error (HTTP 400)**
```json
{
  "success": false,
  "message": "Invalid answers",
  "errors": {
    "21": "rating must be between 1 and 5",
    "22": "answer_options contains invalid values"
  }
}
```

**curl**
```bash
curl -X POST https://api.example.com/feedback/submit \
  -H "Authorization: Bearer <token>" \
  --data-urlencode "user_id=123" \
  --data-urlencode "form_id=7" \
  --data-urlencode 'answers=[{"question_id":21,"answer_rating":5}]'
```

---

### 2.3 `POST /feedback/skip`

Records a skip and marks the target as `skipped`.

**Request**
```
user_id=123
form_id=7
```

**Server logic**
- If the form's `allow_skip=0`, return HTTP 403 with `{ success: false, message: "Skip not allowed for this form" }`.
- Otherwise insert a `feedback_responses` row with `skipped=1`, set target `status='skipped'`, `submitted_at=NOW()`.
- Idempotent: a second skip on an already-skipped target should return success without writing duplicates.

**Sample success**
```json
{ "success": true, "message": "Feedback skipped" }
```

**curl**
```bash
curl -X POST https://api.example.com/feedback/skip \
  -H "Authorization: Bearer <token>" \
  -d "user_id=123&form_id=7"
```

---

## 3. Admin endpoints (Postman / curl only — no UI this round)

Behind the existing admin middleware. JSON request bodies (not form-urlencoded) for ergonomics.

### 3.1 `POST /admin/feedback/forms` — create form + questions + targets in one transaction

**Request**
```json
{
  "title": "Quick feedback",
  "description": "Help us improve.",
  "is_active": 1,
  "allow_skip": 1,
  "questions": [
    {
      "sort_order": 1,
      "question_text": "How would you rate the call quality?",
      "help_text": "1 = poor, 5 = excellent",
      "question_type": "rating",
      "is_required": 1,
      "min_rating": 1,
      "max_rating": 5
    },
    {
      "sort_order": 2,
      "question_text": "Which features did you use?",
      "question_type": "multi_choice",
      "is_required": 0,
      "options": ["Audio call", "Video call", "Chat", "Gifts"]
    }
  ],
  "target_user_ids": [123, 456, 789]
}
```

**Sample success**
```json
{
  "success": true,
  "form_id": 7,
  "questions_created": 2,
  "targets_created": 3
}
```

**curl**
```bash
curl -X POST https://api.example.com/admin/feedback/forms \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d @form.json
```

---

### 3.2 `PATCH /admin/feedback/forms/{id}` — toggle / edit metadata

**Request**
```json
{ "is_active": 0 }
```
or
```json
{ "title": "New title", "description": "Updated copy" }
```

Editing the question list (`questions`) is **blocked once any `feedback_responses` row exists for the form** — return HTTP 409:
```json
{ "success": false, "message": "Cannot edit questions after responses have been recorded" }
```

---

### 3.3 `POST /admin/feedback/forms/{id}/targets` — idempotent upsert of additional user IDs

**Request**
```json
{ "user_ids": [101, 102, 103] }
```

Insert one `feedback_form_targets` row per user that doesn't already have one for this form (UNIQUE constraint guards re-adds). Existing rows are untouched (status preserved).

**Sample success**
```json
{ "success": true, "added": 2, "already_present": 1 }
```

---

### 3.4 `GET /admin/feedback/forms/{id}/responses?limit=&offset=` — list responses for export

Returns each `feedback_responses` row joined with its `feedback_answers`, paginated.

**Sample success**
```json
{
  "success": true,
  "total": 42,
  "limit": 20,
  "offset": 0,
  "responses": [
    {
      "id": 901,
      "user_id": 123,
      "skipped": 0,
      "submitted_at": "2026-04-24 10:31:02",
      "answers": [
        { "question_id": 21, "answer_rating": 5 },
        { "question_id": 22, "answer_options": ["Audio call", "Chat"] },
        { "question_id": 23, "answer_text": "Thanks!" }
      ]
    }
  ]
}
```

---

## 4. Error contract (all endpoints)

Validation errors:
```json
{
  "success": false,
  "message": "Invalid answers",
  "errors": { "21": "rating must be between 1 and 5" }
}
```
HTTP 400 for validation, 403 for permission (e.g. skip when `allow_skip=0`), 404 for unknown form/target, 409 for state conflicts (already submitted, editing questions after responses exist).

Generic failure:
```json
{ "success": false, "message": "Something went wrong" }
```
HTTP 500.

## 5. Edge cases the client already handles (server should still be safe against)

- **User already submitted**: target row is `submitted` → `/feedback/check` returns `should_show=false`.
- **User already skipped**: target row is `skipped` → `/feedback/check` returns `should_show=false` (skipped does NOT come back).
- **Multiple pending forms for one user**: serve oldest first (smallest `feedback_form_targets.id`).
- **`allow_skip=0`**: client hides the Skip button AND the close (X) icon; backend must still reject `/feedback/skip` with 403 as a safety net.
- **User not in any target list**: `/feedback/check` returns `should_show=false`.
- **Empty form (no questions)**: client immediately calls `/feedback/skip` so the target row settles instead of being re-served on every launch — the server should accept this even though it's an admin-side mistake.
- **Re-shown form**: if the same target row is served twice (e.g. user closed the dialog before tapping Submit/Skip due to a crash), the client throttles via SharedPreferences for 24h, but the server may see `shown_count > 1` — that's expected, not an error.

## 6. Out of scope (future)

- Event-triggered forms (post-call, post-chat) — leave room for a `trigger_event` column.
- Per-question conditional logic (Q3 depends on Q1).
- Editing a previously submitted response.
- Admin web UI (this round is API-only; admin team uses Postman/curl).
