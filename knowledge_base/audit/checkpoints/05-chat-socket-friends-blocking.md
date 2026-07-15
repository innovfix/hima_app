# Checkpoint 05 — Chat, Socket.IO, Friends, Favourites, and Blocking

Status: complete; strong-review revalidated 2026-07-15 IST

FCM delivery follow-up: the OneSignal bridge/retry/result-handling conclusions were rechecked against identical relevant `AuthController`/route source on all three live app nodes and the live app-3 Socket.IO source. No notification was sent.

Scope: end-to-end chat discovery/gating, friend requests and acceptance, conversation lists/history, realtime send/receive/ack/read state, REST fallbacks, attachments, reactions, message deletion, local caches/deletion/pinning, push notifications, blocking/unblocking, moderation/content filtering, Socket.IO authentication/authorization, Redis adapter behavior, node routing, and recovery after offline/process death.

Production, databases, services, repositories, Git, and external communications remain read-only. No socket connection, message, friend request, notification, service action, or database write will be initiated during analysis.

## Audit surface

Principal Android components identified so far:

- `ChatActivityInHouse`, `ChatListActivity`, `FriendsListActivity`
- `CreatorChatFragment`, `FriendsHubFragment`, `FriendsTabFragment`, and favourite integration
- `SocketManager`
- chat/list/friend adapters and chat/message/conversation/reaction models
- `FriendRequestRepository`, `BlockUserRepository`, `MessageNotificationRepository`
- memory/local-deletion/cleared-chat/pinned-chat/recent-friend helpers
- chat notification renderer/store/delete receiver and audio player
- Retrofit contracts for history, lists, gate status, send fallback, attachments, read markers, deletion, reactions, friend counts, and block state
- `MessageNotificationRepository` / `MessageNotificationViewModel` wrappers for the message-push API; repository-wide caller tracing confirms current chat send flows do not call them. Active push delivery instead has two server-owned paths: Socket.IO send -> public internal Laravel notification bridge -> OneSignal when Node considers the receiver offline, and REST fallback send -> direct Laravel OneSignal request regardless of the presence-probe result.

Principal backend/runtime components identified so far:

- chat/friend/block actions concentrated in `AuthController`
- `FriendRequestController`, `ChatsController`, `AdminChatController`, and block/deleted-chat controllers
- `BlockService`, `MessageContentFilter`, and `ChatCacheHelper`
- `ChMessage`, `FriendRequest`, `BlockedUsers`, and `ChatBlockedUsers`
- `socket-server/index.js`, Redis adapter, PM2 ecosystem, keep-alive/monitoring scripts, and proxy helper
- public/internal notification bridges and `cron_socket_health`
- production nginx routes `/socket.io` on app-1/app-2 to app-3 `10.122.0.9:3003`, with app-3 proxying locally

Key REST routes include `request_friends`, friend list/status/counts, chat gate/block status, chat history/list variants, attachment upload, mark read, delete message, chat block/unblock/list/check, reactions, fallback send, and message notification bridges.

## Inherited confirmed risks to reconcile

These were established during the architecture baseline and will be traced to exact message flows here:

- The Socket.IO server does not authenticate a JWT during connection and trusts client-supplied identities for important chat/presence operations.
- Laravel internal message/friend notification routes are exposed without normal client authentication.
- `cron_socket_health` is public, checks the wrong local port, and contains PM2 restart/start/save behavior; no request to it will be made.
- Active user traffic is consolidated on app-3:3003 even though listeners also exist on app-1/app-2.
- app-3 may serve stale reads through the replica while app-1/app-2 read the primary, so REST history/gate/list behavior can vary by backend node independently of the centralized socket connection.
- Call-history/favourite functions already showed body-user IDORs and live favourite duplicates. Chat-specific favourite/friend/block semantics must be reconciled with those shared social records rather than assumed consistent.

## Socket.IO server — source-confirmed findings

### Critical: no authenticated socket identity or room authorization

The server accepts every Socket.IO connection without middleware, JWT, session cookie, API key, origin restriction, or device binding. CORS allows every origin. Identity is established only when the client emits `join_user {user_id}`; the server assigns that integer directly to `socket.userId` and joins `user_<id>`.

Chat IDs are deterministic sorted pairs (`<smaller_user_id>_<larger_user_id>`). `join_chat` accepts any supplied chat ID and does not verify that `socket.userId` is either participant; a backward-compatibility branch even joins a chat room when no user identity was set. Thus an unauthenticated client can:

- join another account's personal room and receive `new_message`, delete, and read-receipt events intended for that account;
- join/enumerate predictable chat rooms and receive live message, reaction, join/leave, and presence events;
- claim multiple identities across sockets and make connection/presence checks report those accounts online;
- query room membership through the socket event without participant authorization.

This is realtime message/presence disclosure, not merely spoofable display metadata.

### Critical: arbitrary message impersonation and notification triggering

`send_message` and the legacy `chat message` event choose the sender as `fromUserIdOverride || data.from_user_id || socket.userId`. The body value is preferred over the joined identity, and neither is authenticated. For any existing pair of user IDs, an unauthenticated client can submit a message as either account.

The server then performs privileged work under the forged sender identity:

- inserts into `chat_messages`;
- creates/updates `chats` and `active_chats` unread state;
- emits sender confirmation and recipient realtime events;
- triggers an external OneSignal notification through Laravel when the recipient is considered offline;
- evaluates block, recharge, friends, autopay, account-suspension, and content-filter rules using the impersonated account;
- can auto-create a friend request and trigger its push under that identity.

The per-user limiter is keyed by the same untrusted sender ID and held only in this Node process. An attacker can distribute requests across claimed IDs, connections, or legacy nodes. No live socket/message/notification request was sent during the audit.

### Critical/high mutations through secondary events

- `delete_message` trusts body `from_user_id` before falling back to `socket.userId`. It looks up the real message owner, but compares that owner to the forged requester. Anyone who knows or enumerates a message ID can claim its sender and tombstone it for both users. The original is best-effort archived before content/attachment removal, but the user-visible mutation is still unauthorized.
- `send_reaction` trusts body `user_id`, verifies only that the message exists, and never requires the reactor to be a participant in that message's chat. It permits impersonated add/update/removal of reactions on any known message ID.
- `mark_read` trusts the claimed socket identity (which itself came from `join_user`) or body identity and does not verify relationship to `last_message_id`. It can send forged read receipts that flip another sender's blue-tick UI. Persistence occurs separately through Laravel, so realtime and stored read state can disagree.
- `/api/emit-message-deleted` accepts arbitrary user/message IDs and broadcasts a delete tombstone without authenticating its Laravel caller or verifying the database row. This does not itself update MySQL, but it can make connected clients temporarily render an undeleted message as deleted.
- `/api/emit-creator-status` is also unauthenticated and broadcasts attacker-selected availability for any creator to every connected client.

### Unauthenticated diagnostic HTTP data surface

The same Node process exposes unauthenticated HTTP endpoints for full pair history, a user's entire chat list/unread counts, users present in a chat, whether a user is connected, and message verification by ID. The history/verification queries return `SELECT *` rows and do not apply participant, block, or content-filter visibility rules. Static test/chat/server-control-named pages are also served from the socket process.

Production verification confirms direct internet reachability on all three public node addresses. Each returned HTTP 200 from the unauthenticated `/api/user-connected?user_id=0` status-only probe on port 3003. Arbitrary message history—including filtered messages hidden by normal delivery—can therefore be queried without a token by supplying real IDs; the audit deliberately did not request a real user's data.

### Message-size, type, and filter bypasses

Socket.IO permits a 100 MB frame. `handleMessage` has no message-length cap, accepted message-type allowlist, attachment URL validation, or audio-duration bound. Filtering runs only when `message_type === 'text'`, so a caller can label text as another arbitrary type and bypass mobile-number/Instagram filtering while still storing and broadcasting the supplied `message`. Arbitrary attachment URLs are persisted and delivered for the Android renderer to consume.

Rate limiting does not address this because it counts messages rather than bytes and keys on forged user identity. A small number of oversized events can consume Node memory, DB connection time, storage, broadcast bandwidth, and verbose logging capacity.

### Recharge gate is currently dead in the socket path

The first combined user/block query selects names, IDs, block state, and sender account suspension, but does not select `sender_gender` or `receiver_gender`. The subsequent “male must recharge before messaging female” check reads those absent properties, receives empty strings, and never enters its gender branch. The socket message path therefore does not enforce that recharge rule.

The later friends/autopay gate is separate. It fails open on database/config errors and evaluates the forged sender identity. A paid-autopay sender can also auto-insert a pending friend request before the message transaction; a later message failure can leave the request and its push behind. The check for `any_request` counts every status, so an older rejected/otherwise terminal request can suppress creation of a new actionable request.

### Content and conversation consistency

Filtered content is intentionally saved but not broadcast, pushed, counted unread, or used as the conversation's last message. The sender receives a normal `message_sent` confirmation. This shadow state depends on every history surface filtering consistently; the Node HTTP history endpoint does not, and Laravel/Android behavior remains to be traced.

Conversation creation checks for an existing `chat_id` and then inserts inside the message transaction. Concurrent first messages can race unless the live schema uniquely constrains `chats.chat_id`. Auto friend-request creation occurs before that transaction and is non-fatal, so friend/message state is not atomic.

The server logs complete incoming event objects plus message previews and detailed delivery responses. Release logs can therefore contain private message content and notification-provider metadata.

## Android realtime client — source-confirmed findings

### The client supplies identity but no authentication proof

`SocketManager` opens the Socket.IO connection with URL/path/transport/reconnect options only. It sends no JWT, cookie, API key, signed nonce, or authenticated handshake field. After transport connect it emits `join_user {user_id}`, while every send/read/reaction operation repeats user IDs supplied by the app. This exactly matches the server's trust-based identity model above; there is no hidden Android authentication layer that reduces the exposure.

The client publishes `isConnected=true` immediately on transport connection, then waits 100 ms before emitting `join_user`. `ChatActivityInHouse` reacts to that state by joining the deterministic pair room immediately. There is no joined-room acknowledgement in the readiness state. In the current permissive server this works, but it creates a window where the UI reports realtime readiness before its personal room is joined and would break if the server were correctly changed to require an authenticated/joined identity before `join_chat` or `send_message`.

### Delivery acknowledgement is not idempotent or correlation-safe

Android creates a unique local `temp_<time>_<uuid>` ID, but does not send that value to Socket.IO or the REST fallback. The server response has no `client_message_id` and Socket.IO acknowledgement callback is not used. Reconciliation instead matches the first pending item with the same message type and payload: exact text for text messages or exact remote attachment URL for media.

Consequences:

- a generic `message_error` can be mapped only when exactly one socket send is pending; with multiple sends, the app deliberately leaves every optimistic bubble unresolved;
- identical or repeated text/media sends cannot be correlated to a particular server row, so out-of-order responses can associate confirmation/timestamp/order with the wrong local attempt;
- if the database commit succeeds but the confirmation is lost, the app has no durable proof of success and no idempotency key to make a later REST retry/re-send safe; duplicate stored messages are possible;
- a history refresh can usually absorb an unresolved temp by payload, but payload matching is not proof that a particular attempt is the row being reconciled;
- pending state is activity memory, not a process-durable outbound queue, so process death loses the relationship between optimistic attempts and server rows.

The normal reconnect path is better than the send path: the activity re-joins the chat and refreshes REST history after a real disconnect, which recovers missed server messages. That recovery still relies on payload matching for pending outgoing rows.

### Event loss and connection timing risks

Every realtime event family uses a `MutableSharedFlow` with a 256-item extra buffer and `DROP_OLDEST`. Back-to-back events are no longer conflated, but a large reconnect burst or a paused/slow collector silently discards the oldest message, reaction, delete, read, or creator-status events. The history refresh repairs message rows only; it does not necessarily reconstruct every transient presence/reaction/delete/read UI transition immediately.

The custom connection watchdog tears down a still-connecting socket at 15 seconds while the Socket.IO client's own timeout is 20 seconds. A slow but valid connection can therefore be aborted five seconds before the configured client timeout. Reconnection is capped at 30 attempts and then rebuilt, but readiness remains transport-level rather than room-level.

### Client/server event-contract drift

Android emits `typing` and `update_status` and listens for `user_typing`. The audited Node server has no `typing` or `update_status` handler, so these features silently do nothing in the Git source. Live app-3 drift still needs verification before treating this as the production result.

Message IDs were largely migrated to `Long`, including socket models, deletion, and read receipts, but `sendReaction(userId, messageId)` still accepts an `Int`. Once a message ID exceeds 2,147,483,647, the reaction path can truncate/overflow or become impossible even while display, delete, and read paths continue to work.

### Logging/privacy and thread filtering

Inbound messages are accepted only when their `chat_id` matches or their participant pair matches the open conversation, which is a useful UI containment check even though it is not a security boundary. Reconnect also refreshes history, and stale history responses are request-ID guarded.

The most detailed complete-history dump is debug-only, but several ordinary `Log.d` paths outside that guard still include message previews, user IDs, peer IDs, URLs, timestamps, and realtime metadata. Release builds can therefore retain private chat fragments and relationship metadata in logcat/device diagnostics. Socket release logging redacts the full outgoing body to its length, but the surrounding activity history diagnostics do not consistently do so.

## Laravel chat/friend REST surface — source-confirmed findings

All normal routes below pass through `auth:api`, but many controller methods merely check that *some* JWT is valid and then trust a different user ID in the request body. Authentication is therefore present without object/actor authorization.

### Endpoint authorization matrix

| Endpoint group | JWT/body binding | Consequence |
|---|---|---|
| `chat_history` | Missing | Any authenticated account can read the complete paginated conversation between any chosen pair, including reactions, read state, timestamps, attachment URLs, and block-derived metadata. |
| `my_chat`, `my_chat/friends`, `my_chat/general` | Missing | Any authenticated account can enumerate another user's relationships, last-message previews, unread counts, profile/voice data, call availability, and block flags. |
| `my_requests`, `received_requests`, `friend_list` | Missing | Cross-account friend graph, pending-request, profile, voice, and request-ID disclosure. |
| `check_friend_request` | Missing; `user_id` need not be a member of the queried pair | Cross-account friendship/request-state probing and actionable request-ID disclosure. |
| `mark_messages_read` | Missing | A token holder can claim another user, update that victim's message rows/read timestamps, reset unread state, and create/update their `active_chats` record. |
| `chat_block_user`, `chat_unblock_user` | Missing | A token holder can create or lift unified call+chat blocks on behalf of another account. |
| `chat_blocked_users_list` | Missing | Cross-account block-list disclosure; response also exposes blocked users' email addresses. |
| `check_chat_blocked` | Missing | Cross-account two-way block relationship probing. |
| `add_message_reaction` | Missing, and no participant check | Reaction impersonation on any known message ID, matching the Socket.IO flaw. |
| `upload_attachment` | Missing | Upload public chat media under any existing sender/recipient identities and evaluate compatibility/block rules as those victims. |
| `fallback_send_message` | Missing | Persist a message, change unread/chat state, and trigger a OneSignal push as any existing account. |
| `send_message_notification` | Missing | Any authenticated account can send a OneSignal message notification using another user's identity. |
| `send_message_notification_internal` | Explicitly excluded from auth middleware | Anyone who can reach the route can trigger the same outward push; there is no shared-secret or source-network check in the controller. |

The reviewed stronger exceptions are important: `friend_tabs_counts`, `chat_gate_status`, and `check_block_status` bind the acting body user to the JWT; `request_friends` now authorizes send/accept/reject/cancel/remove against the stored parties; and REST `delete_chat_message` derives ownership from the authenticated JWT rather than a body sender. These prove the application already has the intended authorization pattern, but it has not been applied consistently.

The older call-side `unblock_user` method also authenticates a token without binding its `user_id` to that token, then calls the unified `BlockService`; it is therefore an alternate cross-feature path for lifting both call and chat blocks.

### REST fallback can bypass the advertised chat gate

`fallback_send_message` never calls `chatSendDecision()` and performs no accepted-friend/autopay-language gate. It enforces a separate first-recharge-or-active-subscription rule for male-to-female messages, but that is not the same policy. Therefore, when `friends_gate_enabled=on`, an authenticated caller can use REST to bypass the friends/autopay gate; because `from_user_id` is also unbound, they can do so under another account's identity.

`chat_gate_status` calculates the canonical `$decision` and then ignores it. Its response instead sets every autopay-language viewer to `unlocked=true` without checking for an active subscription, and it ignores the master kill-switch result for non-autopay users. This creates deterministic UI/server disagreement:

- an unpaid autopay-language user sees an unlocked composer but can be rejected by Socket.IO as `AUTOPAY_REQUIRED`;
- when the master gate is disabled, a non-friend in a non-autopay language can still see a locked composer even though the send-decision helper says unrestricted;
- REST fallback may accept messages that the UI and Socket.IO reject.

The socket recharge check is separately dead because its query omitted gender columns, while REST does enforce the recharge rule. Connection health can thus change who is permitted to send.

### Chat history/list privacy, consistency, and performance

`chat_history` caps each page at 100 but does not bind the viewer, executes a total `COUNT(*)` for every page, and logs SQL bindings, returned message IDs, participant IDs, and a plaintext preview of the first message. Its filtering reads only `chat_blocked_users` directly rather than the unified `BlockService`; any legacy or failed mirror drift in `blocked_users` can make history visibility disagree with send/push/call enforcement.

The three chat-list variants first collect all distinct chat IDs, then loop through conversations with repeated user, avatar, block, last-message, unread-count, call-block, and reject-block queries before sorting and applying pagination in PHP. Pagination therefore does not bound database work. This is a pronounced N+1/unbounded-work path for accounts with many conversations and can become an incident amplifier even when indexes are present. The variants also intentionally duplicate row-building logic and already differ in blocked-history preview semantics, making drift likely.

Friend list/request methods similarly load full result sets before search filtering. `friend_requests` has no schema uniqueness visible in the repository, while pair checks use bidirectional `first()`. Duplicate/conflicting rows can make the chosen status order-dependent and cause the gate, lists, counts, re-request cleanup, and action CTA to disagree. Live uniqueness and duplicate counts still require the performance-gated schema check.

### Attachment and payload validation gaps

`upload_attachment` accepts a file when either detected MIME *or* filename extension is allowed (`reject only when both are disallowed`), instead of requiring both and validating actual image/audio structure. A file with an allowed extension but unrelated content can be stored on the public disk under a forced media extension. Upload happens before the message gate/send, so rejected, abandoned, or impersonated sends leave orphan public files.

`fallback_send_message` does not enforce a message-type allowlist, a server-side text length maximum, attachment ownership, or that an attachment URL points to HIMA storage. An arbitrary non-`text` type bypasses `MessageContentFilter`, and an arbitrary URL can be persisted as an image/audio attachment. These match the socket path's type/filter/URL gaps rather than providing a hardened fallback.

The fallback logs message previews and, on successful/failed OneSignal delivery, can log the complete notification payload and provider response. That combines private text, participant IDs, message/chat IDs, and delivery-provider metadata in production logs.

## Canonical friend/request state — and Android mapping defects

The backend's effective `friend_requests.status` vocabulary is:

| Value | Intended meaning | Included in lists/counts | Gate interpretation |
|---:|---|---|---|
| 0 | Pending | Sender's Sent + receiver's Requests | `sent` or `received` |
| 1 | Accepted friendship | Friends | Unlocked/friends |
| 2 | Rejected tombstone | Neither request list | Treated as no active request; re-request allowed |
| 3 | Cancelled tombstone | **Still included in sender's Sent list/count** | Treated as no active request; re-request allowed |
| 4 | Removed accepted friendship | Neither list | Treated as no active request; re-request cleanup supports it |

Android does not consistently use that vocabulary:

- Send uses 0 and accept uses 1 correctly.
- The Sent-tab cancel action sends 2 (the backend's rejected state), while the Requests-tab reject action sends 3 (the backend's cancelled state). The names are reversed.
- Backend rejection notification/foreground refresh is sent only for status 2. A rejection from the Requests tab therefore uses 3 and sends no rejection push to the original sender.
- `my_requests` and `friend_tabs_counts` intentionally include statuses 0 and 3. A receiver rejection made through the Requests tab remains visible and counted as “Request sent” for the sender, while `chat_gate_status` simultaneously treats that pair as eligible to re-request.
- The chat-screen lock's Decline button sends 2, so rejection behaves differently depending on whether it was performed inside the conversation or from the Requests tab.
- `FriendsAdapter` says the Sent tab should show a Cancel button, but sets both action buttons to `GONE` and attaches the cancel listener to the hidden reject button. The normal Sent-tab UI therefore provides no cancel action at all.

After any successful action, the fragment clears its list, waits 500 ms, then reloads. That hides stale rows optimistically but is timer-based rather than tied to a second authoritative response. The backend invalidates count caches before mutation branches complete; an unauthorized/failed request can evict another pair's cached counts even though it cannot create the relationship.

`friend_requests` has no live pair uniqueness. All pair state functions use a bidirectional `first()`, so duplicate rows with different status/direction make this state machine order-dependent. This is especially dangerous because the Socket.IO auto-friend side effect inserts outside the message transaction and does not share Laravel's tombstone cleanup.

## Composer gate and block state — unreachable/contradictory UI

Android has UI states for `friends`, `autopay`, `blocked`, `account_blocked`, “I blocked them,” pending request, received request, and accepted friendship. The live API does not supply all of those states:

- `chat_gate_status` never returns `mode=blocked`, `reason=blocked`, or `reason=account_blocked`; it only derives friendship/language state. Android's “peer blocked you” lock is therefore unreachable from this endpoint.
- Chat history returns only `i_have_blocked_this_user`; it computes but does not return a reciprocal peer-block flag. Chat-list models expect `peer_blocked_me`, but the live list builders do not populate it. The blocked party generally sees an open/ordinary composer until a send is rejected.
- Node/REST send rejection causes the optimistic bubble to disappear, but refreshing the same incomplete gate cannot make the intended blocked lock appear. A stale local account-suspension flag has the same problem because `chat_gate_status` does not return account suspension.
- Before the gate response arrives—or whenever the gate request fails—female users get an immediately open composer even though the current server policy friends-gates females. Non-autopay-language males can also start open via the local subscription gate. A fast send is rejected by Socket.IO, while the gate-omitting REST fallback can accept it.
- The API's ignored `$decision` bug already makes paid/unpaid/kill-switch behavior disagree after a successful response. Local subscription/language caches add a second authority, so UI state can diverge from both server paths until refresh.

Unified block semantics are directional in storage but symmetric in enforcement: `BlockService::block(A,B)` writes active A→B rows to both physical tables; either active direction prevents both parties from sending/calling/notifying; `unblock(A,B)` clears only A's direction. This is coherent only while both tables stay mirrored. Neither live pair has uniqueness, and several read paths still bypass `BlockService`, so duplicate or legacy drift can yield a visible block flag different from actual enforcement.

## Favourites end-to-end

Android exposes favourite state from profile detail, the post-call rating sheet, and the embedded male Friends-hub Favourites tab. Profile detail first checks server state and toggles add/remove authoritatively. The post-call chip marks itself selected optimistically and reverts on an explicit failure. `FavouriteFragment` refreshes on every resume, requests `calls_list` with `fav=1`, paginates ten at a time, and the adapter de-duplicates peers by user ID so duplicate DB rows are invisible in the UI.

All three mutation/query endpoints (`add_favorite`, `remove_favorite`, `check_favorite`) require a valid JWT inside the controller, but none compares the body `user_id` with the authenticated user. Any authenticated account can add, remove, or inspect favourites on behalf of another account. The `calls_list fav=1` list source inherits the already-confirmed call-history body-ID IDOR, so the full favourite list is also cross-account readable.

The server validates that both users exist but does not reject self-favourites, same-gender favourites, or enforce relationship/call history. Android hides the heart on self-profile, while the favourite list filters returned users to the opposite gender. A body-ID caller can therefore create relationship rows that normal UI cannot create and some accepted rows can be missing from the list even though `check_favorite` returns true.

The add path checks for an existing pair before its transaction, then allocates a manual ID with `MAX(id) ... FOR UPDATE`; there is no unique `(user_id, favorite_id)` constraint. Concurrent requests can pass the pre-check and create duplicate logical pairs, while the global tail lock serializes otherwise unrelated adds. The live evidence recorded in checkpoint 04 found 614 duplicate favourite pairs and 742 excess rows. Removal deletes every row for the pair, counts/list queries use distinct favourite IDs, and Android also de-duplicates, so this data drift is largely hidden until integrity/performance analysis.

## Message media, read, delete, and local-state recovery

### Media always fails the first Socket.IO attempt

Android uploads media, then calls `send_message` with an empty `message` and a populated attachment URL. Both Node `handleMessage` and `saveMessage` require `message` to be truthy before considering message type/attachment. Every normal image/audio socket send therefore receives “Missing required fields” and depends on the Android `message_error` REST fallback.

With exactly one pending send, REST usually recovers it. With multiple socket-pending sends, Android deliberately treats the uncorrelated error as ambiguous and leaves all temporary bubbles unresolved. Thus “media works only after fallback,” stuck media during concurrent sends, and path-dependent notification behavior are deterministic consequences—not intermittent upload failures.

### Chat attachment storage, deletion, and Android cache behavior

- Live chat attachments are stored on the handling node's local public disk under `storage/app/public/chat/images/YYYY/MM/DD` and `storage/app/public/chat/audio/YYYY/MM/DD`, then returned as `asset('storage/...')` URLs. `getAvatarImageUrl()` also resolves through `Storage::disk('public')->exists()` before emitting a public URL.
- Both delete-for-everyone paths tombstone the database row and clear `message` / `attachment_url`, but neither deletes the underlying file. The Socket.IO path first best-effort archives the original row into `deleted_chat_messages`; the JWT REST fallback does not archive it before blanking, despite its comment saying it mirrors the socket handler. A deleted attachment therefore remains on disk while record-retention behavior changes with socket connectivity.
- Because lsyncd uses `delete=false`, even a future node-local file delete would not provide cluster deletion: app-1/app-2 can restore one another's surviving copy and app-3 does not mirror deletions back. Current chat delete does not attempt even the first local deletion.
- Android writes compressed chat images and recorded audio to `cacheDir`. Normal upload/cancel/failure callbacks delete the file, but a scheduled retry that is abandoned after Activity destruction has no explicit final cleanup. Image viewers use Glide defaults; the ticket attachment pager separately uses `DiskCacheStrategy.ALL`. There is no cache-busting or explicit Glide cache purge tied to a message tombstone.
- The practical result is a layered consistency model: the live row can be tombstoned, the archive can differ by delete transport, every node can retain a different file set, and a device that already fetched the URL can retain cached bytes until Glide/OS eviction.

### Read receipt can acknowledge before persistence and then suppress retry

Android sets `lastMarkedReadMessageId` before emitting the unauthenticated socket receipt and before the REST mutation returns. A socket receipt can flip the sender's ticks immediately even if REST later fails. Failure/no-network callbacks do not clear the marker, so the same activity instance will not retry that message ID; stored unread counts can remain stale until a newer incoming message or activity recreation.

### Delete-for-everyone treats emit as success, not server acknowledgement

When connected, Android tombstones locally and treats `socket.emit` returning normally as successful deletion. Node can emit `delete_error` for missing/not-owned/DB-failed rows, but `SocketManager` has no `delete_error` listener and no acknowledgement callback. The UI shows success and never rolls back; the message can reappear on history reload. The disconnected REST path is JWT-owner-bound and does roll back most explicit failures, so deletion reliability changes with socket connectivity.

### Clear/delete-for-me are local, timestamp-sensitive layers

- Per-message “Delete for me” stores IDs forever in a per-owner/per-peer SharedPreferences set; there is no pruning or conversation/user cleanup API, so long-running use grows the set and every history rebuild consults it.
- “Clear chat” and “Delete chat” never change server history. They store device-local watermarks; reinstall/clear-data/another device can reveal the old server messages again.
- “Block + also delete” calls the asynchronous block API and clears locally immediately. If the block request fails, messages are still hidden and the user sees a clear success followed by block failure.
- Unblock removes only the list-hide watermark, intentionally retaining the message-clear watermark. The conversation returns empty even though all old rows remain on the server.
- Backend/Node timestamps are stored/emitted as IST strings without an offset, but Android explicitly parses them as the device's timezone. Outside IST, displayed time/date headers are wrong by the zone difference. The block+clear watermark uses actual device `now` against these misinterpreted epochs, so recent messages can survive or be over-cleared depending on timezone.

## Notification and cross-node delivery behavior

The normal offline flow is: Node commits → emits through the Redis adapter → checks receiver presence → calls the public Laravel internal OneSignal route only when considered offline. REST fallback commits and always attempts OneSignal even if its presence probe reports connected.

Node's `isUserConnected()` reads the local adapter room map synchronously. With the Redis adapter, broadcasts fan out cluster-wide, but that local `rooms` map is not a cluster-wide presence query. Consequences:

- a message submitted to an app-1/app-2 legacy listener can broadcast to a receiver on app-3 and also send OneSignal because the sending node sees no local receiver;
- an unauthenticated socket that claims a victim ID on the handling node makes the victim appear online and suppresses their legitimate offline push;
- the public `/api/user-connected` result is node-local and can disagree across the three public listeners.

The Node-to-Laravel notification payload omits backend message ID and message type. Android then cannot deduplicate that push against the socket event by row ID; it uses OneSignal's notification ID only inside the notification stack. Chat-list code avoids double-increment when a socket is connected, but a missed socket event plus a stale “connected” state can leave the visible unread badge unchanged until the next full REST poll.

`ActiveChatTracker` mirrors the open peer into SharedPreferences with a 60-second freshness window for a possible separate notification process. Activity pause normally clears it, but abrupt process death does not run that cleanup. A push for the same peer within the stale window can be suppressed as “chat visible,” broadcast to no live activity, and produce no heads-up.

The rolling notification store keeps the last eight plaintext message previews and peer metadata in ordinary SharedPreferences. It is cleared per peer on open/dismiss and globally on normal logout/401, but it is not encrypted. Release logs are improved in several notification paths, while other audited chat/server paths still log previews and full provider payloads.

Chat-side push delivery is still non-durable. The socket path only bridges to Laravel/OneSignal when the receiver is considered offline, and Node launches that bridge without waiting for it before completing message delivery. The internal Laravel endpoint manually retries connection exceptions up to three times inside the request, but stops after the first HTTP error and always returns HTTP 200 with top-level `success=true`; Node's `result.success || result.onesignal_success` check therefore logs provider failure as success even when `onesignal_success=false`. The REST fallback always posts directly to OneSignal once with `retry(0)`. Neither path has a durable retry queue or post-commit reconciliation ledger, so exhaustion, HTTP/provider failure, or process loss can drop the push after the chat row commits.

### Broken friend-notification contracts and partial-success API responses

Two notification methods referenced by the active code do not exist in the current controller, and the absence was verified directly in the live app-1 `/var/www/himaapp` source rather than inferred from Git alone:

- `routes/api.php` exposes `/api/internal/send-friend-request-notification` and maps it to `AuthController::send_friend_request_notification_internal`, but `AuthController` has only the explanatory comment where that method should be; `notify_call_end()` begins immediately afterward. Laravel therefore returns an error when Node calls this endpoint.
- `request_friends()` calls `sendFriendRequestRejectedNotification()` when status 2 is selected, but no such helper is defined. The existing pending row is saved with status 2 before the missing-method call. The outer exception handler then returns HTTP 500 without rolling back that already-committed update. The user sees failure even though the rejection persisted, and no rejection push is sent.

This amplifies the Android status reversal: the chat-screen Decline path uses status 2, so it can change the relationship state while showing an error and failing to unlock/refresh the sender in real time. The Requests-list Reject path uses status 3, avoids the missing helper, but is recorded as cancelled and remains eligible for the Sent list/count defect described above.

Node auto-seeding has a different partial-success shape. It inserts the pending friend request before calling the broken internal endpoint; the helper resolves `{success:false}` for non-200 responses and the caller is deliberately fire-and-forget. Message delivery continues, the pending request exists, but the recipient receives no friend-request push.

Even after restoring the missing endpoint, the current notification type contract is inconsistent. Laravel's existing `sendFriendRequestNotification()` emits `type=friend_request`, which click routing understands, but `OneSignalNotificationServiceExtension.maybeHandleFriendRequest()` only broadcasts badge/list refresh for `friend_request_received`, `friend_request_accepted`, or `friend_request_rejected`. A received-request banner can therefore appear and open the correct hub when tapped while an already-running Friends surface keeps stale request counts until its next lifecycle/API refresh.

The refresh receiver coverage is also narrower than its code comment claims. `FriendsListActivity` registers `ACTION_FRIEND_STATUS_CHANGED`, but the integrated `FriendsHubFragment` and `CreatorChatFragment` do not; a repository-wide search found no intermediary receiver forwarding that action to them. Those primary bottom-navigation surfaces refresh counts on resume and after their own child actions, not from an incoming friend-status broadcast while they remain visible. The visible request child does poll its list every 30 seconds, but that poll does not refresh the parent tab/bottom-navigation counts. Thus changing the payload to `friend_request_received` alone would still not provide live integrated-hub badge refresh.

`FriendsTabFragment` also calls `startAutoRefresh()` in both `onViewCreated()` and the immediately following initial `onResume()` without first removing the same runnable. The first visible lifecycle can therefore maintain two 30-second polling chains until pause removes both callbacks, doubling friend/chat-list API work for that initial session.

## Diagnostic symptom map

| Reported symptom | First causes to verify | Confirmation target |
|---|---|---|
| Another user's messages can be viewed or events appear under the wrong sender | Unauthenticated `join_user`/`join_chat`, forged socket body IDs, or body-ID REST endpoints | Socket event payload/room plus Laravel route actor binding |
| Photo/audio send spins, disappears, or only works after fallback | Android sends media with empty `message`; Node rejects before save; concurrent socket errors are not correlated | Device socket error and fallback REST response for the same pending item |
| Delete says success, then the message returns | Connected path treats `emit` as success and ignores `delete_error` | Node delete logs/DB row and history reload |
| Blue tick appears but unread count remains | Socket receipt emitted before REST persistence and `lastMarkedReadMessageId` suppresses retry | `chat_messages.is_read`/active-chat unread state versus UI ticks |
| Reject shows “failed,” but the request is actually gone/re-requestable | Status 2 saved before missing `sendFriendRequestRejectedNotification()` throws | API 500 plus persisted status-2 row |
| Rejected request still appears as Sent | Requests tab sends status 3 and backend includes 3 in Sent/list counts | `friend_requests.status=3` and `my_requests`/count response |
| New auto-seeded friend request exists but recipient got no push | Node calls missing `send_friend_request_notification_internal()` endpoint | Pending row/message plus Node warning/Laravel missing-method error |
| Friend badge/list stays stale after a push | `friend_request` vs `friend_request_received` mismatch and no integrated-hub receiver | OneSignal payload type, visible surface, next resume/30-second poll |
| Another user's favourites changed or can be inspected | Favourite add/remove/check and `calls_list fav=1` trust body user ID rather than JWT subject | Authenticated ID, submitted `user_id`, affected `ch_favorites` rows |
| Favourite check says true but the user is absent from Favourites | Backend accepts self/same-gender rows but list filters opposite gender; cache/list cap can also contribute | Pair row, viewer/target gender, calls-list cache/version and response |
| Composer appears enabled but send is rejected as blocked/friends/autopay | Live gate omits block/account state, ignores computed decision, or client opened before gate response | `chat_gate_status`, block tables, language/autopay state, chosen send path |
| Recipient sees both realtime message and push—or no push while offline | Per-process presence check plus Redis fanout; arbitrary victim `join_user` can suppress push | Which node handled send, local room state, app-3 receiver session |
| Cleared/deleted chat returns on another phone or reinstall | Clear/delete-for-me is only a local watermark/ID set | Server history still present; local preferences absent on new install |
| Message time or clear boundary is wrong outside India | Offset-free IST string is parsed as device-local time | Raw server timestamp, device timezone, calculated epoch |

## Live production verification — 2026-07-14 IST

### Strong-review revalidation — 2026-07-15 IST

- Read-only SHA-256 checks reconfirmed that `/var/www/himaapp/socket-server/index.js` is identical to the audited repository file and identical across app-1, app-2, and app-3. The socket identity, gate, media, presence, and notification-bridge findings therefore remain current on all three listeners.
- `AuthController.php` is identical on app-1/app-2 but differs on app-3 and also differs from the current local repository as a whole, so controller conclusions were rechecked from live method presence/ranges rather than whole-file Git equivalence. All three live nodes contain `send_message_notification_internal`, `fallback_send_message`, and `chat_gate_status`; none defines `send_friend_request_notification_internal` or `sendFriendRequestRejectedNotification` even though active routes/callers reference them.
- Live `routes/api.php` is identical across all three nodes and still exposes the unauthenticated internal message/friend notification routes. This review made no notification request and performed no message, friend, database, process, or file mutation.

### Source and runtime

- `/var/www/himaapp/socket-server/index.js` has the same SHA-256 as the audited Git file on app-1, app-2, and app-3. The critical Socket.IO findings are therefore live-source findings, not repository-only possibilities.
- Node is actively listening on `0.0.0.0:3003` on all three app nodes. Normal HIMA nginx `/socket.io/` locations on app-1/app-2 proxy to app-3 `10.122.0.9:3003`, but the legacy app-1/app-2 listeners remain running and directly reachable.
- app-3 has a public interface address (`165.232.181.213`) in addition to its private addresses. UFW reports inactive there, and port 3003 is bound to every interface.
- Status-only requests to `/api/user-connected?user_id=0` returned HTTP 200 directly from app-1 `139.59.56.195:3003`, app-2 `168.144.68.179:3003`, and app-3 `165.232.181.213:3003`. No real user ID, chat history, message, notification, or mutating route was used.
- nginx on app-3 returned 404 for `/api/chat-history`, so the ordinary 80/443 virtual host does not proxy the Node diagnostic API. This does not mitigate the public port-3003 exposure.
- Selected live `AuthController` ranges covering friend actions/lists, all main chat history/list/mutation/fallback functions, and `chat_gate_status` hash exactly equal to the corresponding Git ranges after accounting for a known 22-line live insertion elsewhere. The REST authorization and gate findings above are present in production.
- `redis-adapter.js` has the same checksum on all three nodes. Filtered PM2 logs show a fresh adapter-enabled line after the daily restart on each node: app-1 uses local `127.0.0.1:6379`; app-2/app-3 use primary `10.122.0.2:6379`. app-3 also had an established Node TCP connection to Redis. Cross-node room emits are live; the synchronous presence helpers remain process-local by implementation.
- The production `gateway_config.friend_request_notifications_enabled` value is `on`. The missing friend-notification methods and contract mismatches are therefore on an enabled production path rather than hidden by the server-side notification kill switch.

### Live schema and performance gate

Approximate InnoDB statistics at inspection time:

| Table | Estimated rows | Data | Indexes | Relevant integrity/index result |
|---|---:|---:|---:|---|
| `chat_messages` | 10.3M (PK cardinality ~11.3M) | ~1.50 GB | ~5.23 GB | Pair/history indexes exist, with substantial duplicate overlapping indexes; no client idempotency key/unique constraint. |
| `chats` | 832K | ~118 MB | ~193 MB | `chat_id` is unique, so concurrent first-chat inserts are protected at schema level but one racing transaction can still error unless handled. |
| `active_chats` | 1.75M | ~158 MB | ~398 MB | `(user_id, chat_id)` is unique; a redundant non-unique copy of the same prefix also exists. |
| `friend_requests` | 2.10M | ~115 MB | ~153 MB | Only separate sender and receiver indexes; no pair/status composite and no uniqueness. |
| `blocked_users` | 419K | ~19 MB | ~40 MB | Composite lookup indexes exist but no `(user_id, call_user_id)` uniqueness; two near-duplicate composites add write/storage cost. |
| `chat_blocked_users` | 410K | ~25 MB | ~93 MB | Composite lookup indexes exist but no `(user_id, blocked_user_id)` uniqueness; several overlapping indexes. |
| `message_reactions` | 21K | ~2.5 MB | ~4.8 MB | `(message_id, user_id)` is unique. |

The history query is indexed by `chat_id` but still requires a filesort for its three-column ordering/filter combination. The read-update plan can use an index merge, though it creates a temporary structure. The per-user chat-ID UNION uses sender/to-user indexes, but application code still consumes the full result and performs per-chat queries before pagination. Friend pair lookups can use range access on a single-party index, but lack of pair/status composites and pair uniqueness becomes increasingly important at 2.1M rows.

No duplicate-grouping scan was run on the 2.1M-row friend table because the required normalized-pair aggregation has no supporting index and would violate the performance gate on the primary. The absence of the constraint is confirmed; data-level duplicate prevalence remains an explicitly deferred replica/off-peak check.

Follow-up integrity checks were constrained to index-supported primary queries and bounded/capped work on app-2, which confirmed `@@read_only=1` and `@@super_read_only=1`:

- `blocked_users`: 373 duplicate directional pairs, 415 extra rows, 371 pairs with multiple active rows, and one mixed active/inactive pair. The covering composite index served the aggregation.
- `chat_blocked_users`: 366 duplicate directional pairs, 408 extra rows, 362 pairs with multiple active rows, and three mixed active/inactive pairs. The covering pair index served the aggregation.
- newest 100,000 `friend_requests` rows only: 38 duplicate normalized user pairs, 69 extra rows, and 17 duplicate pairs containing conflicting statuses. This bounded sample proves live duplicates/status conflicts but is not a full-table prevalence estimate.
- a five-second-capped aggregate status scan on the replica completed in about three seconds: 2,117,691 total rows; 1,139,341 pending (0); 791,231 accepted (1); 193 rejected (2); 177,625 cancelled (3); 9,301 removed (4); no null/out-of-range status and no self-pairs.

Because the live Sent list/count queries include every status-3 row, up to 177,625 cancelled tombstones are eligible to appear/count as “Request sent” across users, before accounting for later pair re-request cleanup. The extreme 193-vs-177,625 status-2/status-3 skew is consistent with the audited Android Requests-tab rejection mapping to 3 rather than the backend's rejection-notification status 2, though historical writers may contribute and causation is therefore an inference rather than proof.

## Provisional severity register

| Severity | Failure | Direct impact |
|---|---|---|
| Critical | No authenticated socket identity; arbitrary user/chat room joins | Live message, delete/read, reaction, and presence disclosure |
| Critical | Sender identity comes from untrusted event data | Message impersonation, DB writes, unread changes, pushes, friend-request seeding |
| Critical | Delete requester identity is forgeable | Unauthorized delete-for-everyone on enumerable message IDs |
| High | Reaction/read identities and chat membership are not authorized | Impersonated reactions/read receipts and cross-chat events |
| High | Unauthenticated Node HTTP history/list/verify endpoints | Stored message and relationship disclosure if listener is reachable |
| High | 100 MB events plus no content/type/URL bounds | Memory/storage/bandwidth abuse and filter bypass |
| High | Unauthenticated internal broadcast endpoints | Fake delete and creator-status state on connected clients |
| Medium | Socket recharge gate reads columns it never selected | Business gate bypass |
| Medium | Gate and friend-request side effects fail open/non-atomically | Message/friend state divergence |
| High | Android sends no socket authentication proof | Confirms the server's room/send impersonation boundary is fully exposed to clients |
| High | No client message ID/idempotency across socket and REST | Unresolved sends, unsafe retry, and duplicate-message risk |
| Medium | 256-event `DROP_OLDEST` buffers | Silent loss during bursts/reconnect or slow collectors |
| Medium | Android/server typing and status contract drift | Presence/typing features silently fail |
| Medium | Reaction API still narrows message ID to `Int` | Reactions fail or target the wrong row at high IDs |
| Low/Medium | Release history diagnostics log message previews | Private conversation fragments in device logs |
| Critical | Laravel chat/friend actor IDs are often not JWT-bound | Cross-account history disclosure and mutations by any valid account |
| Critical | REST fallback sender is unbound and can trigger push | Message impersonation, DB/unread changes, and outward notification as another user |
| Critical | Block/read mutations trust body actor | Force unified blocks/unblocks or falsify another user's read state |
| High | REST fallback omits friends/autopay send decision | Business-gate bypass and path-dependent permissions |
| High | Public internal notification route has no caller authentication | Arbitrary OneSignal message push if route is reachable |
| Critical | All three unauthenticated Node diagnostic listeners are directly internet-reachable | Stored chat/relationship disclosure and realtime impersonation are externally exploitable, not LAN-only |
| High | Attachment MIME-or-extension validation and arbitrary message URL | Public orphan files, content-type abuse, and untrusted media delivery |
| High | Chat lists do all/N+1 work before pagination | Latency/DB-load growth proportional to total conversation count |
| Medium | `chat_gate_status` ignores its computed decision | Composer state contradicts Socket.IO, REST, and kill-switch policy |
| Medium | Friend pair selection uses non-unique, order-dependent rows | Gate/list/count/action disagreement under duplicates |
| Critical | Favourite actor and list owner are body-ID controlled | Any valid account can alter, inspect, or list another account's favourites |
| Medium | Favourite pair has no uniqueness and pre-check is outside transaction | Concurrent duplicates and hidden integrity drift; live duplicates already exist |
| Low/Medium | Favourite write/list eligibility contracts differ | Server accepts self/same-gender rows that normal UI cannot create or display consistently |
| High | Android status 2/3 mapping is reversed across friend surfaces | Rejection push missing; rejected row remains counted as sent; chat/list state conflicts |
| Medium | Sent-tab Cancel control is hidden | Pending request cannot be cancelled through the normal list UI |
| High | Peer/account-block UI states are not returned by the live gate | Composer remains misleadingly open; repeated sends fail without actionable state |
| High | Empty-body media is rejected by Socket.IO | Every media send depends on REST fallback; concurrent pending sends can remain stuck |
| Medium/High | Read marker advances before REST success | Blue ticks and persistent unread counts diverge with no same-ID retry |
| High | Socket delete has no acknowledgement/error consumption | False delete success and message resurrection on reload |
| Medium | Local clear/delete timestamps parse IST as device-local | Wrong times/date headers and timezone-dependent hide/restore behavior |
| Medium | Block+delete clears before block succeeds | Local conversation loss even when server block fails |
| High | Presence check is node-local and identity-spoofable | Duplicate pushes across nodes or suppressed push to an offline victim |
| Medium | Stale active-chat preference survives abrupt death for 60s | First subsequent push can be silently suppressed |
| High | Rejection helper is missing after the DB save | HTTP 500/no push despite a persisted status change; Android UI and server state diverge |
| High | Node's friend-request notification route targets a missing controller method | Auto-seeded pending requests and messages succeed silently without notifying the recipient |
| Medium | Friend-request push type differs from Android's live-refresh types | Banner/click may work while open Friends badges and lists remain stale |
| Medium | Integrated Friends/Creator hubs never receive the friend-status broadcast | Even corrected push types do not refresh visible primary-tab badges in real time; child lists wait for polling |
| Low/Medium | Initial Friends-tab lifecycle schedules two poll chains | Duplicate API/DB work every 30 seconds until the tab pauses |

## Audit order

1. Map Android socket lifecycle, identity payloads, event contracts, local optimistic state, retries, and REST fallback.
2. Map Socket.IO connection/auth, room ownership, send/read/delete/reaction events, Redis adapter, Laravel callbacks, and delivery acknowledgements.
3. Trace backend REST authorization and query behavior for chat lists/history, friend requests/gates, read state, attachments, deletion, reactions, and block variants.
4. Reconcile all block tables/services and their effects on calls, chat, notifications, friends, lists, and existing conversations.
5. Verify live source/node drift, routes, socket runtime topology, relevant schema/indexes, and only aggregate/redacted data-quality evidence.
6. Produce canonical chat/friend/block state machines plus symptom-to-root-cause matrices.

No finding in this checkpoint authorizes a production, database, service, Git, notification, or other outward mutation.
