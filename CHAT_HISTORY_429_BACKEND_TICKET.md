# Backend ticket: HTTP 429 on `chat_history` during normal chat browsing

**Audience:** Backend / API team  
**Endpoint:** `GET .../api/auth/chat_history` (example base: `https://demolivedb.himaapp.in/api/auth/chat_history`)  
**Priority:** Medium–High (affects any user who opens several chats in a short time)

---

## 1. Evidence from client logs

Captured from Android app `com.gmwapp.hima.dev`, Logcat tag `ChatReopenTrace`, session `2026-04-21` ~17:08:

- **Six consecutive `chat_history` requests** from a single authenticated user; client-to-server elapsed times **~75–145 ms** per successful call.
- The **first five** calls returned **HTTP 200**.
- The **sixth and seventh** calls (peers `97`, `923310`) returned **HTTP 429**.
- The **429 response body is HTML** (Laravel default throttle page), not JSON — snippet from logs:

```text
HISTORY HTTP_ERROR req=1 code=429 peer=97 elapsedMs=49
  errorBody=<!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=...
```

- **`Retry-After` header was absent** on 429 responses (`retryAfterHeader=null` in client logs).
- Rough window that triggered throttling: **~11 seconds for 6 successful history loads** from the same user browsing the chat list.

---

## 2. Why this is primarily a backend configuration issue

The Android client already:

- Sends **exactly one** history request per chat open (no duplicate loads on first resume).
- **Cancels** in-flight Retrofit calls when the user leaves the screen.
- Applies a **per-peer ~3 s cooldown** after a 429 so the same peer is not hammered immediately.
- Applies a **~250 ms global spacing** between history fetches to smooth bursts.
- Uses an **in-memory cache** so previously opened chats can render without blocking on the network.

Opening **6 different chats in ~11 seconds** is normal user behaviour (scanning the inbox). The current throttle is too aggressive for this use case.

---

## 3. Requests for the backend team

### 3a. Raise or exempt the throttle on `chat_history`

If using Laravel’s rate limiter, the route may be behind something like:

```php
Route::middleware('throttle:60,1')->group(...)
Route::middleware('throttle:api')->group(...)  // often 60 req/min per user/IP
```

**Preferred approach — dedicated limiter** in `app/Providers/RouteServiceProvider.php` (or `bootstrap/app.php` in Laravel 11+):

```php
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;

RateLimiter::for('chat-history', function (Request $request) {
    return Limit::perMinute(240)->by(optional($request->user())->id ?: $request->ip());
});
```

Then in `routes/api.php` (adjust controller import to match your app):

```php
Route::middleware(['auth:sanctum', 'throttle:chat-history'])
    ->get('chat_history', [ChatController::class, 'history']);
```

**Target:** at least **~240 requests/minute per authenticated user** for this endpoint (or equivalent burst tolerance), so rapid chat switching does not hit 429 under normal use.

**Alternative — exempt from generic API throttle** and attach a lighter custom limiter only if needed:

```php
Route::middleware(['auth:sanctum'])
    ->get('chat_history', [ChatController::class, 'history'])
    ->withoutMiddleware(['throttle:api']);
```

(Only do this if you still protect the route with another limiter or abuse protection.)

---

### 3b. Return JSON on 429, not HTML

Mobile clients expect JSON when `Accept: application/json` is sent. Today the body is the default HTML error page.

Add handling so API requests get a JSON body, e.g. in `app/Exceptions/Handler.php` (adjust imports and exception class to your Laravel version):

```php
use Symfony\Component\HttpKernel\Exception\TooManyRequestsHttpException;
use Illuminate\Http\Exceptions\ThrottleRequestsException;

public function render($request, Throwable $e)
{
    if ($e instanceof ThrottleRequestsException && $request->expectsJson()) {
        $headers = method_exists($e, 'getHeaders') ? $e->getHeaders() : [];
        $retryAfter = $headers['Retry-After'][0] ?? 3;

        return response()->json([
            'success' => false,
            'message' => 'Too many requests. Please try again shortly.',
            'retry_after_seconds' => (int) $retryAfter,
        ], 429)->withHeaders($headers);
    }

    if ($e instanceof TooManyRequestsHttpException && $request->expectsJson()) {
        return response()->json([
            'success' => false,
            'message' => 'Too many requests. Please try again shortly.',
        ], 429)->withHeaders($e->getHeaders());
    }

    return parent::render($request, $e);
}
```

*(Exact exception types vary by Laravel version; the important part is: JSON body + preserve `Retry-After` in headers.)*

---

### 3c. Always send `Retry-After` on 429

Laravel’s throttle middleware usually sets `Retry-After`. Please confirm it is **not stripped** by nginx, Cloudflare, or another proxy in front of the API.

The Android client already logs `retryAfterHeader` on responses; once the header is reliable, we can optionally honour it client-side (see section 5).

---

## 4. Reproduction steps (for backend verification)

### In the app

1. Log in as a user with **at least 8** chat partners.
2. From the chat list, open **chat A** → back → **chat B** → back → repeat for **8 different peers within ~15 seconds**.
3. Observe: after ~**6** successful loads, subsequent `chat_history` calls return **429** (HTML body in raw response).

### With curl (replace placeholders)

Assumes query params match your API (adjust names to match `ApiInterface.getChatHistory`):

```bash
BASE="https://demolivedb.himaapp.in/api/auth"
TOKEN="<Bearer token>"
MY_USER_ID=1145793

# Example: loop 8 different receiver IDs
for RID in 535213 138854 991360 377189 150188 97 923310 537899; do
  echo "--- receiverId=$RID ---"
  curl -sS -o /dev/null -w "HTTP %{http_code}\n" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/json" \
    "${BASE}/chat_history?userId=${MY_USER_ID}&receiverId=${RID}&limit=10&offset=0"
  sleep 0.2
done
```

Expected **before fix:** last one or two iterations return **429**.  
Expected **after fix:** all return **200** (or a much higher threshold before 429).

To inspect headers on failure:

```bash
curl -i \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  "${BASE}/chat_history?userId=${MY_USER_ID}&receiverId=923310&limit=10&offset=0"
```

Check for `Retry-After` and `Content-Type: application/json` on 429 after the handler change.

---

## 5. Optional client follow-up (deferred until backend ships)

After the API reliably returns **`Retry-After`** (and preferably JSON on 429), the Android app can replace the fixed **3 s** per-peer cooldown in `ChatHistoryMemoryCache` with the server-supplied seconds. `ChatActivityInHouse` already logs `retryAfterHeader` on `HISTORY RESPONSE`; implementation would parse that header into `recordRateLimit(peerId, durationMs)` — roughly **~10 LOC**. **Do not implement until** the backend confirms the header is present and stable.

---

## 6. Summary checklist for backend

| # | Ask | Done when |
|---|-----|-----------|
| 1 | Increase or specialise throttle for `chat_history` | 6+ rapid chat opens from one user do not 429 under normal use |
| 2 | 429 returns **JSON** for `Accept: application/json` | App can parse error body |
| 3 | **`Retry-After`** present on 429 and not stripped by proxy | Header visible in `curl -i` |

---

*Document generated for handoff to the backend team. Client-side mitigations (cache, cooldown, empty state) remain in the app but do not remove the need for server-side throttle tuning.*
