# Checkpoint 06 — Wallet, Payments, Autopay, and Withdrawals

Status: complete

Targeted creator withdrawal/bank/KYC/payout/webhook/refund/cron/admin-settlement strong review: 2026-07-15 IST. Current Android and repository backend implementations were re-read; the review confirmed the documented lifecycle and added the log-only payout-retry distinction below. Production was not mutated or called through any payout/notification endpoint.

Scope: Android coin wallet/recharge/history, payment initiation and result handling, gateway callbacks/webhooks, ledger and balance mutation, subscription/autopay mandate lifecycle, cancellation/failure/recovery, creator earnings, bank/UPI verification, withdrawal request/approval/rejection/payout, notifications, reconciliation, scheduler behavior, live-vs-Git drift, and performance-safe production evidence.

Production, databases, services, repositories, Git, payments, notifications, and external communications remain read-only. No payment, mandate, callback, withdrawal, payout, database write, service action, or test request to a live mutating endpoint will be initiated during analysis.

## Required audit questions

- Is each apparent payment/withdrawal path actually called and runtime-wired, or is it legacy, fallback, dormant, manual, or dead? Names, comments, commands, and Git history are leads only; conclusions require caller and execution tracing.
- Which authenticated identity owns every wallet, order, payment, mandate, subscription, bank/UPI, earning, and withdrawal operation?
- Which gateway-originated requests prove authenticity through signatures, source constraints, replay protection, and server-side amount/order lookup?
- Which keys make credits, debits, callbacks, mandate renewals, withdrawals, payouts, refunds, and retries idempotent?
- Are balance and ledger changes committed atomically, and can parallel workers spend or credit from stale balances?
- How do Android and Laravel interpret HTTP transport success versus business success, unknown outcomes, process death, and delayed callbacks?
- Which schedulers/reconcilers repair missing or out-of-order gateway events, and can multiple production nodes execute them concurrently?
- Do withdrawal state transitions prevent double approval/payout, post-rejection mutation, or account-detail substitution?
- Which large-table queries are index-supported at the live scale, and where do UI/search/pagination/cache semantics conceal incomplete results?

## Audit order

1. Inventory Android screens, models, persistence, workers, Retrofit calls, deep links, and result handlers.
2. Inventory Laravel routes/controllers/services/jobs/commands/models and payment-provider integrations.
3. Build canonical wallet/payment/autopay/withdrawal state machines and identity/idempotency/transaction matrices.
4. Reconcile Android behavior with backend response and asynchronous callback semantics.
5. Verify live source/config shape without opening secrets; inspect schema/index/query plans before aggregate evidence.
6. Produce a symptom-to-root-cause matrix and roll confirmed high-severity findings into the master.

## Initial system inventory

### Android surfaces

- Wallet and purchase: `WalletActivity`, `PaymentActivity`, `PaymentInitiatedActivity`, `PaymentWebViewActivity`, `AutopayCheckoutActivity`, `BottomSheetSelectPayment`, `BottomSheetInsufficientCoinsPaywall`, and `YoutubeRechargeActivity`.
- Balances/history: `TransactionsActivity`, `FemaleTransactionsActivity`, `EarningsActivity`, `EarningsHonourActivity`, wallet/transaction/earnings repositories and view models, plus coin/transaction adapters.
- Subscription: `AutopayRepository`/view model, `AutopayEventTracker`, `SubscriptionStateCache`, `CancelSubscriptionActivity`, and subscription/free-coin response contracts.
- Creator payout setup: `BankUpdateActivity`, `AddUpiActivity`, `WithdrawActivity`, bank/UPI/withdraw repositories and view models.
- Gateway-specific clients/contracts: PhonePe/direct add-coins Retrofit calls, Cashfree PG order repository/view model, UPI gateway/payment repository, and Cashfree SDK verification client.

### Backend surfaces

- Provider controllers: `PhonePeController`, `CashfreeController`, `RazorpayController`, `AutopayController`, `CashfreeSubscriptionWebhookController`, and legacy/payment functions in `AuthController`.
- Subscription client/state: `CashfreeSubscriptionsClient`, `UserSubscription`, `SubscriptionEvent`, `ReconcileAutopayStatuses`, and the daily autopay report/events controllers.
- Money data: `users.coins`, `transactions`, `payments`, `phonepe_payments`, `cashfree_payments`, `withdrawals`, `withdrawal_bank_details`, `upis`, coin/coupon/offer/trial tables, and transaction-charge configuration.
- Scheduled/recovery paths: every-minute PhonePe pending-order auto-check, every-minute Cashfree withdrawal-status check, disabled PhonePe transaction reconciliation, and autopay reconciliation commands/webhook/poll-on-status behavior.

### Route-boundary map to verify

Several money routes are registered at top level without route middleware: PhonePe order creation/status/webhook/auto-check/reconcile, Cashfree order creation/status/webhook, payout webhook, transaction list, withdrawal list, UPI gateway creation, Razorpay link/coin credit, and HDFC session/status. Some may perform authentication or provider-signature checks inside the controller; each must be audited before classifying exposure.

The large `/api/auth` group has only `api` middleware, not `auth:api`; protection depends on `AuthController` constructor rules and/or per-method checks. It contains transaction/history, bank/UPI updates, withdrawal creation/listing, direct/PhonePe/Cashfree coin-credit calls, public-style cron methods, UPI creation, and trial coins.

The newer autopay group separately and explicitly applies `api`, `auth:api`, and per-user throttling, and its comments state that controller identity comes from the JWT subject. The Cashfree subscription webhook is public by design and claims in-controller HMAC verification. These stronger boundaries still require implementation and live-source verification.

## Critical live coin-credit findings

### Evidence standard and actual production classification

Gateway names and deployed files do not establish use. The classification below follows the Android caller and selection condition through the exact backend mutation path, then checks the live user configuration, package catalog, source hashes, and recent aggregate table activity. No payment or callback was generated.

| Path | Executable caller and completion path | Live evidence on 2026-07-14 | Classification |
|---|---|---|---|
| PhonePe PG | `WalletActivity`, `PaymentActivity`, and `MainActivity` create a PhonePe order, launch the SDK, poll status, and call the compatibility endpoint; actual credit is performed by `PhonePeController` webhook or its pending-order auto-check | 972,632 users have `payment_type=phonepe`; PhonePe has a populated coin catalog; recent `phonepe_payments` rows and completed/checked rows were present | **Active production — normal user path** |
| Cashfree PG | The same three Android surfaces create an order, launch Cashfree PG, poll status, and call the compatibility endpoint; actual credit is performed by `CashfreeController::webhook` | 590,123 users have `payment_type=cashfree`; Cashfree has a populated coin catalog; recent pending and completed `cashfree_payments` rows were present; webhook verification mode is live `enforce` | **Active production — normal user path** |
| Google Play Billing (`gpay`) | Android has a complete switch branch: create `try_coins` order, launch Play Billing, then `BillingManager` calls public `add_coins` after purchase consumption | No user currently has `payment_type=gpay`, and no `coins.pg=gpay` catalog exists. Only PhonePe and Cashfree occur in the live user column | **Executable but currently unassigned/dormant in normal production UI; public server mint chain remains exploitable** |
| Razorpay payment link | Android's Razorpay branch calls `/api/create-upi-payment-link`; the controller creates a link, but no matching HIMA callback/credit path was found | No user has `payment_type=razorpay` and no Razorpay package catalog exists. The deployed PHP webhook expects a different reference format and cannot consume the active controller's timestamp-only reference correctly | **Executable but currently unassigned/dormant; charge-to-credit path appears broken or externally unverifiable** |
| EKQR/`upigateway` | Android computes amount, creates a client transaction ID, calls public `createUpigateway`, and opens the returned URL; standalone PHP webhook attempts to call `add_coins` | No user has `payment_type=upigateway` and no matching package catalog exists. Deployed webhook calls `add_coins` without its required `order_id` and `status`; static success page never credits | **Executable but currently unassigned/dormant; in-repo credit path is broken** |
| Direct `razorpay_add_coins` | No active Android Activity caller was found; public Laravel method immediately credits from body IDs | Deployed on all nodes, but no normal app selection/caller or assigned users | **Server-exposed dormant/legacy capability; independently exploitable** |
| DWPAY/old Instamojo-style PHP | Current Android calls are commented out; old PHP webhook calls current `add_coins` with an obsolete incomplete payload | Deployed legacy files/log artifacts; no current Android caller | **Dead Android legacy / server files still exposed** |

The live user distribution is not inferred from a migration or comment. It was measured on the super-read-only app-2 replica after an index inventory and query-plan check: only `phonepe` and `cashfree` exist among 1,562,755 users. Registrations in the latest 30-day window also overwhelmingly follow the actual registration code: Tamil/Malayalam to Cashfree and the other configured languages to PhonePe, with only two one-user historical exceptions. The `coins` table likewise has 20 PhonePe and 20 Cashfree rows, plus three null-gateway legacy rows, and no package rows for the other gateway names.

The registration implementation assigns Cashfree for Tamil or Malayalam and PhonePe otherwise. Login returns that stored value; Android refreshes it through the login response and persists it as `last_coin_pg`. The live PhonePe-maintenance override is currently `off`, with empty start/end values, so it is not changing this routing. An admin edit can assign another string, and the Android switch branches remain executable, but current production data does not select them normally.

### Public, payment-free coin minting

The mixed active/legacy coin endpoints below are explicitly excluded from `AuthController`'s `auth:api` middleware, while their `/api/auth` route group itself has only the ordinary `api` middleware:

- `try_coins` trusts body `user_id`, `coins_id`, attacker-chosen `order_id`, status, and message. It creates an `Orders` row without a payment-provider request, signature, receipt, amount proof, authenticated owner, or idempotency constraint visible in the function.
- `add_coins` is also public. Given a matching status-0 order, it trusts the requested status; status 1 marks the order successful, increments the chosen user's `coins` and `total_coins` from the server coin package, and inserts a credit transaction. It never verifies any gateway state, receipt, signature, paid amount, or authenticated identity.
- These compose directly: create a fresh status-0 order through `try_coins`, then submit the same chosen user/package/order to `add_coins` with status 1. Fresh attacker-chosen order IDs make the credit repeatable. No live request was made.
- The top-level public `razorpay_add_coins` path is even less constrained: it accepts only a body user and coin-package ID, immediately increments both balances, and inserts a credit transaction. There is no order ID, payment link lookup, gateway verification, signature, receipt, replay key, or authentication.

`try_coins` and `add_coins` are not merely abandoned names: static caller tracing proves that they are the actual Android Google Play Billing order and completion path. Their normal live UI branch is currently dormant because no user is assigned `gpay`, but the unauthenticated HTTP capability is live regardless of UI assignment. Conversely, `razorpay_add_coins` has no active Android caller and is classified as a deployed server-exposed legacy/dormant capability.

These are confirmed production-source findings. Function-body/range SHA-256 values for `try_coins`, `add_coins`, and `razorpay_add_coins` matched Git exactly on app-1, app-2, and app-3 on 2026-07-14 IST.

### Non-atomic credit and false-success behavior

`add_coins` updates the order, user balance, and transaction as three independent saves with no encompassing DB transaction or row lock. Failure after order status save can leave a paid/closed order with no balance credit; failure after balance save can leave coins without a transaction row. Concurrent requests can both observe status 0 and race on the same preloaded user balance, producing duplicate ledger attempts, lost increments, or order/balance disagreement.

`razorpay_add_coins` likewise performs an unlocked balance read-modify-write followed by a separate transaction insert. Parallel credits can overwrite each other, while a transaction failure after the user save leaves an unledgered balance.

The authenticated `add_coins_phonepe` and `add_coins_cashfree` compatibility methods do the opposite of their names: for a found payment whose status is not 1, they load the user/package and return `success=true` with “Coins added successfully,” but do not update the payment, balance, total, or transaction. Android still calls these methods after provider completion in multiple wallet/payment/main-activity paths, so transport/business success from this compatibility call is not evidence that coins were credited; the provider webhook/reconciler must be checked separately.

### Public UPI order creation and embedded credential

`createUpigateway` is also excluded from auth and trusts body user, client transaction ID, and amount before making a server-side request to an external UPI provider. Its provider credential is hardcoded in the controller source rather than configuration; the value is intentionally omitted from this knowledge base. The route enables unauthenticated external-order generation/cost abuse and exposes a reusable credential to anyone with source access. It does not bind the client-selected amount to a server coin package and does not persist a local order in this method.

The audited `createUpigateway` source range also hash-matched on all three production nodes. No external provider request was sent during this audit.

## Normal production gateway call graphs

### PhonePe — active, but callback/recovery races can lose or duplicate credit

1. Android sends the chosen user/package/coupon to the public PhonePe create-order route. The backend gets the package price server-side and inserts a status-0 local `phonepe_payments` row, then requests a PhonePe order and returns SDK data.
2. Android launches the PhonePe SDK. On completion it calls the public check-status route and then the authenticated `add_coins_phonepe` compatibility method.
3. The compatibility method does **not** credit; for a still-pending local payment it returns `success=true` and the existing balance. Real credit comes from `PhonePeController::webhook` or `autoCheckPendingOrders`.
4. The webhook checks a configured username/password hash, acknowledges HTTP success, and defers credit. The pending-order checker is a public route scheduled every minute.
5. `creditPhonePeOrder` changes payment status, balance, and transaction in one DB transaction, but its status check and user/balance read occur before the transaction without a row lock. Webhook, cron, multi-node scheduler, or public endpoint concurrency can therefore enter the same credit or overwrite another balance update.

Confirmed failure modes from executable branches:

- The auto-check selects unchecked orders older than one minute. Every provider state other than `COMPLETED` is marked `checked=1` permanently on that first check, even though the provider order expiry is 20 minutes. A payment completed after that first minute requires the webhook; if it is missed, the app can see provider completion and a false-success compatibility response while no coins are credited.
- The pending selection uses an unbounded `get()`. Each application node has scheduler capability, and the scheduled job is not protected with a shared `onOneServer` lock in the traced definition; Laravel's local `withoutOverlapping` is insufficient by itself across nodes. The same public endpoint can also be invoked directly.
- The check-status route is public and logs the provider bearer token. The token value is not copied into this knowledge base.
- Coupon lookup accepts a supplied coupon ID without binding the coupon to the user, package, validity window, usage limit, or a server-authorized discount percent in the traced creation path.
- The per-order auto-check catch path does not roll back an already-open DB transaction before continuing the loop.
- The reconciliation implementation is publicly routable but its scheduler call is commented out. It records candidates rather than crediting them, and its “matching transaction” heuristic can false-match any same-user/package/amount credit in a 24-hour window.

The last 1,000 PhonePe rows on the read-only replica contained 454 completed/checked rows, 542 non-success checked rows, and four still-pending rows. This is aggregate runtime evidence that the table and recovery path are active; it does not prove that every provider event was correctly credited.

### Cashfree PG — active, stronger idempotency, one fail-open authenticity branch

1. Android sends user/package/coupon to the public Cashfree create-order route. The backend gets the package price and inserts a local pending row, then creates the provider order.
2. Android launches Cashfree PG, polls the public status endpoint, and calls authenticated `add_coins_cashfree`; that compatibility method can report success without credit.
3. Actual credit occurs in `CashfreeController::webhook`. The webhook row-locks the local payment, keeps the status guard inside the transaction, atomically increments both user coin counters, inserts the ledger row, and marks the payment complete. This is materially safer against duplicate callbacks than the PhonePe path.
4. The webhook has no local signature verification. It instead calls Cashfree's order-status API and applies `gateway_config.cashfree_webhook_verify`. The live value is `enforce`.

Confirmed failure modes:

- Even in `enforce`, a provider status API exception deliberately falls through and trusts the webhook payload. An attacker able to reach the public route could submit a forged success payload during a provider verification outage/error and reach credit.
- In `log` mode, a definitive non-paid provider result also only logs and falls through. The live mode is currently `enforce`, so this branch is dormant unless configuration changes.
- The provider network call occurs while holding the payment row lock and has an up-to-eight-second timeout, increasing lock duration and contention under provider slowness.
- Order creation has the same incomplete coupon authorization checks as PhonePe and logs/returns more provider response material than the client needs.

The last 1,000 Cashfree rows contained 597 completed/checked and 403 pending rows. This and the latest-row metadata confirm current traffic without reading customer/payment payloads.

## Unassigned but executable gateway paths

### Google Play Billing — dormant selection, unsafe purchase finalization

When the Android gateway string is `gpay`, the wallet/payment/main surfaces generate a four-digit order ID, save mutable user/package/order fields in ordinary SharedPreferences, call public `try_coins`, and launch Play Billing. `BillingManager` then:

1. observes a `PURCHASED`, unacknowledged purchase;
2. puts its token in an in-memory handled set;
3. reads the latest mutable preference values;
4. consumes the Play purchase first; and
5. calls public `add_coins`, which receives no Play token, provider order ID, product ID, receipt, or signature.

Consequences proven from these branches:

- Network or backend failure after consumption loses the purchase because there is no durable retry queue or purchase re-query recovery.
- Process death loses both the in-memory handled set and completion context; the manager queries product details, not outstanding purchases.
- A delayed or overlapping purchase can credit the latest saved package/user/order rather than the purchased product.
- The four-digit client order space collides, and the backend lookup/idempotency shape is insufficient for a payment identifier.
- The same public two-call chain can mint packages without making a Play purchase at all.

No current user or package catalog selects `gpay`, so this is not classified as a normal live checkout path today. It remains a live unauthenticated backend vulnerability and a high-risk dormant feature that would become user-facing immediately if an admin changed `payment_type`.

### Razorpay payment links — no compatible HIMA completion receiver

The current Android Razorpay branch calls `RazorpayController::createPaymentLink`, not `razorpay_add_coins`. It trusts the body user/package IDs, gets package price server-side, creates a provider link, stores no local HIMA payment/order row, uses a timestamp-only reference, and sends users to a callback page on a different domain.

The deployed `razorpay/webhook.php` expects a `user-coin-HM-*`-style reference. It therefore cannot correctly parse the timestamp-only reference produced by the active controller. The older link generator has the expected legacy format, but its Android caller is commented out; its webhook has no signature and calls the current `add_coins` contract without required order/status fields. The deployed `success.php` is a static page with a hardcoded success state and performs no provider verification or credit.

No user/package assignment currently selects Razorpay. If enabled without another external system not visible here, the traced in-repo flow can take payment but cannot reliably credit HIMA coins. This remains “externally unverifiable” rather than absolutely dead because the callback points outside the two audited repositories.

### EKQR/UPI gateway — webhook contract is incompatible with current credit API

Android's `upigateway` branch computes the selected amount plus a rounded 2% charge, creates `user-coin-timestamp`, calls public `createUpigateway`, then opens the returned payment URL. Laravel appends `-HM`, calls the provider with a credential hardcoded in source, and persists no local order.

The deployed standalone webhook has no signature/authentication check, logs the raw payload, parses the generated reference correctly, and calls `/api/auth/add_coins` with only `user_id` and `coins_id`. Current `add_coins` also requires `order_id` and `status`, so the webhook reaches validation failure and cannot credit. The static success page always says payment succeeded but performs no credit.

The webhook, DWPAY webhook, old Razorpay webhook/link file, and `success.php` are byte-for-byte identical to the repository on all three live nodes. No `upigateway/webhook_log.txt` existed on any node at audit time. The DWPAY and Razorpay log artifacts on every node were identical in size and timestamp to each other and to repository artifacts, with no evidence of current writes; their contents were intentionally not opened because they can contain PII/payment data. Deployment alone is not treated as proof of use.

## Live-source verification ledger for payment paths

- `PhonePeController.php` and `CashfreeController.php` matched repository SHA-256 on app-1, app-2, and app-3.
- The critical `AuthController` method ranges for `createUpigateway`, `try_coins`, `add_coins`, and `razorpay_add_coins` matched on all three nodes.
- `upigateway/webhook.php`, `dwpay/webhook.php`, `razorpay/webhook.php`, `razorpay/add_coins_requests.php`, and `success.php` matched the repository on all three nodes.
- The full live `routes/api.php` hash differs from Git, so relevant route definitions were checked directly on the live file rather than inferred from repository routes. All payment routes discussed above are present live.
- Provider credentials are hardcoded in several audited source files. Their values are deliberately neither reproduced nor saved.

## Cashfree autopay — actual lifecycle and production state

### Active configuration and caller map

Autopay is an active production feature, but only for the live `language_configs` rows `Hindi` and `Punjabi`. All other nine configured languages are currently `none`. Re-subscription is enabled for every language row, although that value matters only where the feature is or was reachable.

The normal execution chain is:

1. A male user in an autopay language reaches the wallet welcome-gift dialog, wallet re-subscribe banner, or a locked chat path. The retired `BottomSheetTrialOffer` class has no executable UI caller and current never-active chat users are redirected to Wallet instead. Its supporting media path is still active, however: `MainActivity.onResume()` calls `TrialOfferConfigCache.prefetch()` for every signed-in user.
2. Android launches `AutopayCheckoutActivity` with `trial_new` for a never-active user or `direct_old` for a lapsed user. It calls the JWT-protected `autopay_initiate` endpoint. Backend identity is correctly derived from the JWT; body `user_id` is ignored.
3. Laravel creates a Cashfree subscription externally, then `updateOrCreate`s the one local `user_subscriptions` row for that user as pending. Android uses the Cashfree native SDK when session and subscription IDs are present, otherwise it opens the public Cashfree.js bridge page.
4. HMAC-verified Cashfree webhooks are the primary state/renewal-credit path. Android also polls `subscription_status` on checkout return and on several Home/Chat/settings surfaces. That endpoint asks Cashfree for current state at most once per 60 seconds while the local state is active or pending.
5. A successful payment event of at least ₹100 credits a fixed 1,300 coins and inserts a transaction. The ₹1 authorization event activates the mandate but deliberately does not credit coins. Current plan code uses ₹1 for `trial_new`, ₹299 for `direct_old`, then ₹299 recurring cycles.
6. Active subscribers can claim 10 coins once per IST calendar day through a separate JWT-bound `daily_claim` path.

### Retired sheet, active media prefetch

The targeted media strong review separated UI reachability from background work. `MainActivity.onResume()` calls the JWT `trial_offer_config` API on every signed-in resume. The API resolves the caller's language and returns only that language's latest active asset. Android immediately emits cached config to any listener, performs a background refresh, and downloads the URL-hashed file only when it is missing; process single-flight does not suppress the repeated API requests. A successful new download uses a `.part` file, writes a poster frame, and removes older cache entries.

Production currently has one active Hindi trial-offer row with a non-null uploaded video. The 655,871-byte file exists with the same SHA-256 hash on app-1, app-2, and app-3. Therefore the current sheet is UI-dormant but its per-resume config request and Hindi-device cache warm are executable production behavior. Admin replacement/deletion acts only on the handling node's public disk, while lsyncd uses `delete=false`; the new path can replicate, but old-path deletion is not a reliable three-node purge.

This is live, not just deployed code. The replica contained approximately 23.5K subscription rows and 63.4K webhook events at inspection time. Current status totals include 1,335 active subscriptions, 16,164 pending, 5,745 cancelled, and 4,665 failed. Recent event timestamps reached the audit time. The exact Autopay controller, subscription webhook, Cashfree client, reconciliation command, and web routes match Git on all three nodes. app-3's scheduler file has unrelated drift; none of the three scheduler files contains an autopay reconciliation schedule.

### Initiation is not atomic with external mandate creation

`autopayInitiate` performs the external Cashfree create call before persisting the local subscription row. There is no per-user DB/advisory lock covering the read-existing → provider-create → local-update sequence. The named limiter permits three initiations per ten minutes but does not serialize concurrent requests.

Two concurrent first initiations can both observe no row, create two real mandates, then race through the single `UNIQUE(user_id)` `updateOrCreate`; only one provider ID remains locally addressable. A later re-subscription also overwrites the one stored provider ID by design. The prior provider ID has no durable user-to-mandate history row outside webhook events. If cancellation of the prior mandate did not actually succeed, it can remain chargeable while its future events no longer map to the user.

Live aggregate evidence confirms a substantial orphan-provider-ID population rather than merely a theoretical race:

- Unknown webhook events with HIMA-created `HIMA_SUB...` IDs include 1,593 authorization-status failures, 1,579 payment failures, 54 payment successes, and many other lifecycle events whose provider ID no longer matches any current `user_subscriptions` row.
- Eight HMAC-verified unmatched payment-success events carried amount ₹299. Seven of those provider IDs can still be linked to a user through earlier `subscription_events`, but the current webhook never performs that historical lookup; one had no user mapping anywhere in the local event history.
- The current handler records an unknown event as processed and performs no state/balance mutation. Neither status polling nor the reconciliation command recovers a missed ₹299 payment credit. Therefore these eight events could not be credited by this code path; whether any were manually remediated is not established without a separate ledger investigation.
- An older parser version also stored 5,527 `SUBSCRIPTION_STATUS_CHANGED` events with no parsed subscription ID between 2026-06-03 and 2026-06-13. Current source extracts the live payload shape and no such null-ID events appeared in the latest seven-day window, so this is classified as historical fixed parser behavior with persistent legacy event data—not a current parser defect.

### Android cannot consume the backend's idempotent initiate responses

When Laravel sees an already-active mandate or an active/authorizing provider state, it intentionally returns `success=true` with `already_active` and **no** new session or redirect. The Android `AutopayInitiateData` model has no `already_active` field, and `AutopayCheckoutActivity` treats a successful response without session/redirect as “Couldn't start autopay.” It does not poll status in this branch because `checkoutLaunched` remains false.

Consequences:

- A duplicate tap against an already-active user displays a false failure rather than closing as active.
- A backend-protected in-progress mandate displays a retry button. Retrying can remain a false dead end; after the ten-minute fail-safe window or a terminal/null provider response it may create another mandate.
- The same false failure occurs after process/navigation races that reopen checkout while authorization is still in progress.

### Trial eligibility has two conflicting executable definitions

`subscription_status.is_new_user` applies three server conditions: account creation at/after `AUTOPAY_LAUNCH_DATE`, zero current coins, and never active. However, the actual current welcome-gift and wallet checkout surfaces gate only on male + autopay language + subscription cache populated + `ever_active=false`; they do not use `is_new_user` or the coin/launch-date conditions. `autopayInitiate` likewise accepts `trial_new` for every never-active subscriber and explicitly ignores the stricter launch-date/coin rule.

`UserSegment.isNewUser()` is now used to hide/display coins and gate a call tap, but no executable plan-selection call uses it; the Chat comment claiming otherwise is stale. Thus an old pre-launch or coin-bearing user who never activated a mandate can still be routed to and accepted for the ₹1 trial. The stricter response flag is not the authoritative plan rule in practice.

### Webhook authenticity is strong; processing idempotency is incomplete

The subscription webhook requires Cashfree's base64 HMAC-SHA256 over timestamp plus raw body and rejects missing/unconfigured/invalid signatures. That is materially stronger than the ordinary Cashfree PG webhook. Exact sequential redelivery is normally suppressed by a unique synthesized event ID.

However:

- The initial “already processed” lookup is outside the transaction and no event row is locked before business processing. Concurrent copies of the same event can both pass the lookup; `updateOrCreate`/the unique key prevents two event rows but does not provide an in-transaction processed-state claim before the switch. A concurrent `SUBSCRIPTION_PAYMENT_SUCCESS` can therefore enter the credit branch twice.
- Different legitimate payment events can still update the same user at once. The current repository implementation reads `users.coins`, adds 1,300 in PHP, and saves inside the transaction without locking the user row; it does not update `total_coins`. A concurrent renewal, daily claim, recharge, call settlement, gift, or other balance writer can therefore read the same old balance and overwrite one of the credits while both ledger/event records commit. Strong-model review on 2026-07-15 corrected the earlier audit statement that this branch used SQL `increment()`; that atomic increment exists in the ordinary Cashfree PG webhook, not in the subscription-renewal webhook.
- `cashfree_event_id` is synthesized and then truncated to 100 characters. The normal payment-ID form is short enough, but non-payment IDs concatenate provider ID, type, and event time; truncation weakens uniqueness if long values share a prefix.
- `SUBSCRIPTION_PAYMENT_FAILED`, `SUBSCRIPTION_CANCELLED`, and failed auth-status branches call OneSignal from **inside** the DB transaction despite the controller's general post-commit design comment. The push method can wait up to ten seconds, holding event/subscription/user locks and increasing retry/concurrency pressure. Only the `STATUS_CHANGED` downgrade push is actually deferred until after commit.
- Success transitions do not emit a corresponding “active” push. A device that locked immediately on a failure push can remain locked in the foreground after a later successful provider retry until some screen polls status again.

### Cancellation can say success while the mandate remains live

The authenticated cancel endpoint calls `CashfreeSubscriptionsClient::cancelMandate`, but that client converts network/non-terminal provider errors to `false`. The controller ignores the returned boolean, always marks the local row `cancelled`, and tells Android “Subscription cancelled.” Android clears its cache and shows the same success message.

After that false local cancellation:

- `subscription_status` deliberately skips all terminal local rows, so it never asks Cashfree whether the supposedly cancelled mandate is still active.
- The manual reconciliation command defaults to active/pending rows and also excludes cancelled/failed rows.
- There is no scheduled invocation of that command on app-1, app-2, or app-3.
- With re-subscription enabled, the user can create a replacement mandate while the prior provider mandate is still live. The single local row is overwritten, making future events from the prior mandate unknown; continued charges can then fail to credit or cancel locally.

The backend says pending/failed rows are cancellable, but Android's normal settings flow only displays the cancellation action when status is exactly active. Pending and failed users see a non-active card with no click handler, so the intended backend escape path is not normally reachable through the UI.

### Reconciliation is manual and repairs only current status

`autopay:reconcile-statuses` exists but is not scheduled in any live `Console/Kernel`. Its default scan covers only active and pending local rows, up to 300 oldest-updated rows per invocation, with one provider request per row. It can correct status but does not:

- recover payment success/coin credits;
- discover provider IDs no longer present in `user_subscriptions`;
- heal false local cancelled/failed rows unless an operator explicitly changes `--status`;
- cancel orphan mandates;
- emit a user push after correction.

Status polling from Android is therefore the only automatic secondary repair, and it has the same current-row plus active/pending limitations.

### Daily subscriber coins are deduplicated but not balance-safe

`daily_claim` correctly binds identity to the JWT and relies on the live unique `(user_id, claim_date)` key inside a DB transaction, preventing two successful claim rows for the same IST day. Yet it checks active subscription before the transaction, does not lock the subscription, and updates `users.coins` through an unlocked read-modify-write. A concurrent cancellation can race after eligibility, and concurrent balance mutations can lose either the 10-coin credit or the other operation.

The claim increments `users.coins` but not `users.total_coins` and creates no `transactions` ledger row. The `daily_claims` table is the only audit record, so general wallet history and any invariant based on `total_coins` or credit transactions will not reconcile with the displayed balance by design.

### Legacy welcome-gift coin claim is active and cross-account mutable

This is separate from the Cashfree subscription daily claim. `MainActivity` actively calls `free_coins_status` for eligible male users when the best-offer surface is absent/dismissed, then `FreeCoinsWelcomeDialog` calls `claim_free_coins`.

The AuthController middleware requires a JWT, but both methods trust body `user_id` instead of the JWT subject. Any authenticated account can inspect another user's offer eligibility and trigger the one-time gift for that other account. Eligibility is represented only by absence of **any** `transactions.type=add_coins` row, not a unique gift-claim key. The check, balance/total increment, and ledger insert are not transactional or locked, so concurrent requests can both pass and produce duplicate or lost balance/ledger outcomes. Button debouncing in the normal dialog does not protect the API.

## Creator withdrawal and payout lifecycle

### Actual production classification

The visible Android caller is `EarningsActivity`: a balance-eligible, non-agency-blocked creator can open `BottomSheetSelectPayment`, which calls `appsettings_list` before exposing a method. The app then starts `WithdrawActivity` with exactly `bank_transfer` or `upi_transfer`; the selected type chooses the corresponding Retrofit endpoint. This is caller tracing, not classification by class or route name.

Live `appsettings` is `bank=1`, `upi=0`. The database corroborates the runtime selection:

- In the latest 30-day IST window, bank transfers have 9 pending, 93,327 paid, and 3,210 failed/refunded rows: 96,546 rows total.
- The same window has zero UPI withdrawals.
- Historical data contains 300,640 paid and 8,600 failed/refunded UPI rows, proving UPI was formerly used rather than dead code.
- Current classification: **bank withdrawal is high-volume active production**; **UPI is a deployed, historically active, currently feature-disabled/dormant path**.

The exact live `withdrawals_list` range and the complete bank/UPI verification, request, charge, cron, webhook, and notification range of `AuthController` hash-match Git on app-1, app-2, and app-3. The whole controller differs from Git elsewhere, so this range verification is intentional. Relevant live routes were also read directly because the full live route file drifts from Git.

### Identity, eligibility, and charge calculation

`withdrawals_list`, `update_bank`, `update_upi`, `withdrawals`, and `upi_withdrawals` require a JWT inside the controller and reject a body `user_id` that differs from the JWT subject. The active bank request additionally checks the live bank switch, blocked/agency status, female gender, PAN requirement, stored bank details, gross balance, maximum gross request of ₹3,000, first-success minimum ₹10 or later minimum ₹50, a one-minute gap, cancelled-request count, and ₹3,000 daily gross pending/paid limit.

The live charge slabs are non-overlapping: ₹10–₹1,000 uses a flat ₹3 fee plus 18% tax on the fee and 1% TDS; ₹1,001–₹25,000 uses flat ₹5 plus fee tax and 1% TDS; ₹25,001–₹100,000 uses flat ₹7 plus fee tax and 1% TDS. The payout path caps requests at ₹3,000, so only the first two slabs are reachable normally. Live `tds_inoperative_pan_mode=enforce`; a confirmed Aadhaar-seeding `N` or `R` changes TDS to 20%, while blank/unknown does not. If configuration ever leaves a gap, `calculateChargesAndTds` silently charges zero fee and zero TDS because it takes the first matching slab and defaults to no deductions.

The balance deducts the requested gross amount, while `withdrawals.amount` stores the net provider transfer and `actual_amount` stores gross. The exact fee, fee-tax, and TDS components are not persisted on the request row, so later accounting can infer the total difference but cannot reconstruct the individual historical components reliably after configuration changes.

### Bank verification is active but not serialized

Bank update validates IFSC through Razorpay, performs a PaySprint penny-drop, applies a PAN-name word-match, then saves the user's bank details and inserts `bank_updates` and `pay_sprint` audit rows. It enforces five daily verification attempts, three lifetime bank updates, and a 24-hour cooldown. The former duplicate-account guard is commented out, so the same bank account can be linked to multiple HIMA users.

The limits and external checks occur without a user lock or surrounding transaction. Concurrent requests can both pass the daily/lifetime/cooldown checks, both spend external verification calls, and race the final user fields. User save, `bank_updates`, and `pay_sprint` inserts can also partially succeed.

The performance gate found no `user_id` index on the live 20,451-row `bank_updates` table. Both the lifetime count and latest-update query perform full scans; the latter also filesorts. `pay_sprint` has `(user_id,type,status)`, so the active user+type lookup is narrowed before the unindexed date predicate. This is currently a moderate per-request cost, not a 63M-row emergency, but it grows linearly and sits in an externally expensive user action.

The controller logs security-sensitive provider material: the Cashfree authorization response, PaySprint authorization headers/token, bank/UPI request details, names, and provider responses. No values were opened or copied into this audit. The finding is based on deployed source statements.

### UPI setup is currently dormant and incomplete

`update_upi` calls PaySprint, but any provider failure/outage falls back to regex-only acceptance. Name matching runs only when PaySprint succeeds. It saves the UPI ID before attempting Cashfree beneficiary creation, computes a beneficiary ID but omits it from the beneficiary payload, does not require provider success, and never persists the user's `bene_id`. The UPI withdrawal path later requires that `bene_id`. A newly configured UPI can therefore receive a local “updated successfully” response without becoming payout-capable, unless an older beneficiary ID happens to exist.

Because the selector hides UPI and the server rejects UPI withdrawals while the live flag is zero, this is not a current normal user failure. It is a high-risk dormant path that becomes immediately user-visible if an admin flips the flag.

### Active bank payout has a provider/DB unknown-outcome hole

The bank request gets a Cashfree token, creates/updates a beneficiary, and checks provider balance before the local transaction. It then begins a DB transaction, locks the user, rechecks balance and same-type pending state, deducts the gross balance, and calls Cashfree's IMPS transfer API **while the DB transaction and user lock remain open**. Only an accepted provider response with a provider transfer ID creates the status-0 withdrawal row and commits.

The live bank branch also has a narrow beneficiary fallback: if Cashfree rejects the first payout attempt as `beneficiary_not_found`, the code retries with a small set of case-variant `beneId` candidates derived from the stored account + IFSC before it gives up. That fallback only helps when no transfer was created yet; it does not remove the broader timeout / lost-response / commit-failure unknown-outcome hole below.

This prevents an ordinary provider rejection from consuming balance, but it cannot atomically coordinate Cashfree with MySQL:

- Cashfree may accept and initiate the real payout while the response times out or is lost. Laravel rolls back the local balance and creates no withdrawal row.
- A later DB insert/commit failure after Cashfree acceptance has the same result.
- Retry generates a new `TXN` identifier because no durable local intent/idempotency row exists before the provider call, so a second real payout is possible.
- Holding the user row lock during the external call also blocks unrelated balance activity and magnifies provider latency.

The in-lock pending guard covers only the selected type. If UPI were enabled, concurrent bank and UPI requests could both pass the pre-transaction daily/gap checks and neither would see the other's same-type pending row. UPI is off today, so that cross-type branch is dormant.

`withdrawal_bank_details` is not a per-withdrawal historical snapshot: `updateOrInsert` is keyed only by `user_id`, and the live table has exactly 18,776 rows for 18,776 users. Each new withdrawal overwrites the prior bank snapshot. The table has no `user_id` index, so each active write searches all roughly 18.8K rows before updating/inserting. This also makes later dispute/audit reconstruction use the latest saved snapshot, not necessarily the account used for a specific payout.

### Public every-minute reconciler can race and miss most reversals

`cron_update_withdrawals` is public and deliberately excluded from controller auth. The live scheduler definition calls the load-balanced public URL every minute. `www-data` actually runs Laravel `schedule:run` every minute on both app-1 and app-2; app-3 has no HIMA schedule runner. `withoutOverlapping` is node-local here, so two nodes can invoke the route concurrently, and any external caller can add another invocation.

The live database currently has ten status-0 bank withdrawals, one without a transfer ID. Phase 1 loads all pending rows without a limit, polls Cashfree, and on failure/reversal changes status and refunds gross balance through unlocked, non-transactional read-modify-write saves. Two invocations can observe the same status and both refund, lose one another's balance changes, or duplicate notifications. Success updates are similarly unclaimed before the provider call.

Phase 1 also contains `isTransientPayoutFailure()` and a `WITHDRAWAL_RETRY_SHADOW` log branch for `FAILED` responses. It classifies only `INVALID_REQUEST`, `BANK_GATEWAY_ERROR`, and `PAYOUT_INTERNAL_ERROR` as transient and logs whether a future implementation would retry. **This is observation only:** after logging `would=RETRY`, the current executable path still marks the withdrawal status 2 and refunds it exactly like every other terminal failure. There is no automatic failed-payout reinitiation, deterministic retry transfer ID, or persist-before-provider retry intent in the current code.

Phase 2 intends to recheck recent paid withdrawals, but always executes `status=1`, latest `updated_at`, `LIMIT 25`. Unchanged successful rows remain the newest and are selected again on the next minute, so the scan does not advance through the window. Live evidence found **6,218** paid rows in the 48-hour window; the same newest 25 can monopolize every run while thousands are never checked for post-success reversal. The query itself is index-supported by `(status,updated_at)`; this is a cursor/progress bug, not an index bug.

The optional 30-day random sample is live `off`. If enabled in enforcement it uses row/user locks for refunds, but `ORDER BY RAND()` creates scaling cost. Phase 3 atomically flags a later Cashfree success after an urgent manual credit, but intentionally does not recover the double payment automatically.

The phase-1 query is unbounded, while the scheduler's public HTTP call permits 120 seconds and internal phase guards assume earlier work finishes around 30–50 seconds. A backlog/provider slowdown can overrun a minute. The route also causes Cashfree API calls, balance mutations, and OneSignal notifications, so its unauthenticated exposure is operationally material even when no attacker can forge provider state.

### Live-enforced payout webhook accepts missing signatures

The top-level Cashfree payout webhook is public and live `withdrawal_webhook_mode=enforce`. Its check rejects only when a signature is **present and mismatched**:

```php
if (!empty($signature) && !hash_equals($expectedSig, $signature)) { ... }
```

An omitted/empty signature therefore skips authenticity verification. A failure/reversal payload whose transfer identifier matches a status-1 withdrawal reaches the atomic refund path, credits the user's gross amount back, marks the withdrawal status 2, and sends a reversal notification. Repeated copies are protected by the row-status check, but an unsigned first copy is accepted. `withdrawals.transfer_id` has no index or uniqueness constraint; the lookup performs a full scan of roughly 620.5K withdrawal rows for every webhook and selects the first match.

The webhook records raw payload material and calls OneSignal inside the DB transaction without a defined timeout on that call. Any exception returns HTTP 200, suppressing provider retry; the cron is meant as a backstop but has the phase-2 starvation defect above. It also skips reversal/failure while the local row is still status 0 and acknowledges it, relying entirely on cron to repair later.

No forged webhook, scheduler URL, bank verification, payout request, or provider call was sent during this audit.

## Wallet and creator transaction-history behavior

### Actual callers and access control

The male Profile opens `TransactionsActivity`, which pages `/transaction_list` in groups of ten. The creator Profile opens `FemaleTransactionsActivity`, relabelled “Earnings,” which pages `/female_transaction` in groups of ten. Both callers use the cached current user's ID and the normal Retrofit client/JWT.

The server behavior differs:

- `female_transaction` binds the body ID to the JWT subject and rejects non-female accounts.
- `transaction_list` requires a valid JWT but never compares the body ID with its subject. Any authenticated account can request another user's recharge/debit/referral/gift history and the matched call partner name/type/timing. The normal Android caller is self-only, but the deployed HTTP capability is an authenticated cross-account financial-history IDOR.
- Neither endpoint bounds `limit` or `offset`. A custom client can request an arbitrarily large page; female history then expands the matching work described below. Negative and extreme values are not rejected cleanly.

The exact combined implementation range hash-matches on all three production nodes. This is live behavior, not a repository-only finding.

### Male history is intentionally not a complete balance ledger

`transaction_list` excludes every `coins_deduction` row whose `coins` value is below 10, from both the returned data and `total`. In an index-safe sample of the latest 10,000 ledger rows, 4,816 were male call deductions: 1,987 below 10 and 2,829 at least 10. Thus 41.3% of the recent call-deduction sample is deliberately invisible in the male Transactions screen even though those rows represent real balance movement. “My coins decreased but no transaction appears” can be correct under current code, not necessarily a missing ledger write.

Recent sample composition also proves the screen is live: 4,816 `call_income`, 4,815 `coins_deduction`, 336 `add_coins`, 13 `receive_gift`, 13 `send_gift`, and seven `daily_bonus` rows. All sampled `add_coins` and `daily_bonus` rows used `payment_type=Credit`; call/gift rows had null payment type. Android treats only exact `Credit` as positive on the male adapter and renders unrecognized types generically, so new/lowercase credit labels can appear as red debits unless the adapter is updated.

For call deductions, the endpoint prefers `transactions.call_id` and joins the call by primary key. Historical null-`call_id` rows instead join on user plus exact end timestamp. The live call table lacks an index on `(user_id,update_current_endedtime)`, so that legacy join narrows only by user and filters end time repeatedly. If neither join lands, the endpoint issues an additional fuzzy “closest call within roughly two hours” lookup per blank displayed row. That fallback can attach an unrelated nearby call and partner to a ledger row because it has no settlement/call key.

### Creator history ignores the exact call key and does unbounded work

For each ten-row page, `female_transaction` first fetches the selected ledger rows, then loads **every** completed `user_calls` row ever received by that creator, all columns, with no date or ID bound. It fetches the names of every distinct caller in that entire set and, for each displayed `call_income`, loops through all loaded calls to choose the closest end timestamp within 60 seconds.

This is both slower and less accurate than the data already available. In the latest-10,000-row sample, all 4,816 `call_income` rows had non-null `transactions.call_id`, but the creator endpoint never uses it for the main match. It can therefore mislabel the caller/type/time/amount when nearby calls exist, and its inline duration bonus is then keyed from that guessed call rather than the ledger's exact call ID.

Live scale is approximately 73.4M transaction rows (about 7.27 GB data plus 18.7 GB indexes) and 41.0M call rows (about 5.10 GB data plus 24.2 GB indexes). The creator all-calls query can use a `call_user_id` prefix index, but remains unbounded in returned rows and PHP memory; index cardinality implies roughly 338 call rows per distinct creator on average, with high-volume creators necessarily much larger. The full load and nested scan repeat for every ten-row page.

The transaction table has no `(user_id,datetime)` ordering index. Male and creator page queries use a user/type-capable index and then filesort by `datetime`; backend `total` is recomputed on every page even though neither Android response model exposes/uses it. Creator pages additionally filesort their three transaction types. Offset pagination becomes progressively more expensive and can skip/duplicate rows when new transactions arrive between pages.

### Android pagination and error semantics can skip or misreport pages

Both Activities advance `offset` before starting the next request and never roll it back on failure. A failed page is therefore skipped on the next scroll. Neither tracks `hasMore` or consumes the backend total; scrolling at the end can keep requesting empty later offsets. Initial `loadTransactions()` also does not set `isLoading=true`, leaving a window where an early scroll/layout event can start offset 10 while offset 0 is still running; responses append in arrival order rather than sort order.

The male Activity does not observe its error LiveData at all, so an initial network/server failure can leave the loading state with no retry/error transition. The creator Activity observes errors but displays the same no-records state used for a legitimate empty history. Both layers log response bodies; the creator path also logs user IDs, offsets, URLs, and full response objects, placing financial/history metadata in device logs unless stripped externally.

## In-call gifts are an active non-atomic cross-wallet transfer

Gift transfer is not inferred from the `send_gifts` name. Both current male audio and video call Activities expose quick-gift callers, and `GiftBottomSheetFragment` sends the same request after checking estimated remaining call coins. The latest-10,000 ledger sample contained 13 sender and 13 receiver gift rows. The exact deployed function hash-matches on all three nodes.

The endpoint requires a JWT but never binds `user_id` to it. Any authenticated account can choose another account as sender, spend that account's coins, and credit any chosen female receiver; it does not require an active call or caller/receiver relationship. Android supplies the real current male in normal use, but the API boundary is a cross-account wallet-debit IDOR.

Sender balance check/save, receiver `balance`/`total_income` save, sender ledger insert, and receiver ledger insert are four independent operations with no DB transaction, row lock, or request idempotency key. Confirmed failure/race classes include:

- parallel gifts can both pass the same sender balance and overwrite deductions, allowing more ledger/gift effects than the surviving coin debit;
- concurrent receiver income changes can overwrite one another;
- failure between saves/inserts can debit without credit, credit without one/both ledger rows, or leave only one side of the transfer recorded;
- a successful server commit followed by a lost response is indistinguishable to Android from failure; retry creates another financial gift because the request has no stable client key.

The one-second Android tap throttle reduces accidental UI bursts but is neither durable nor a server-side concurrency control. Peer animation/FCM is a separate client action after the financial response, so money can move without the recipient seeing the gift animation, and a retry can duplicate money to recover a missing animation.

The sender transaction stores `coins=-gift_coins`. `TransactionAdapter` then prefixes a minus sign for all non-credit rows and explicitly does the same again for `send_gift`, producing text such as `--20` rather than `-20` in the active male history UI. Receiver history reads the rupee amount and renders it as a positive gift correctly.

## Admin/manual withdrawal transitions

### Reachability and real use

The live drift-prone admin menu on all three nodes contains the `withdrawals.index` route and labels Withdrawals visible to all admin/support users. The mutation routes sit inside the broad verified/two-factor admin group without a withdrawal-specific role middleware. The deployed 898-line `WithdrawalsController` hash-matches Git on all three nodes.

These are used capabilities, not route-name assumptions. Production contains 2,304 `transactions.type=cancelled` rows, three withdrawals stamped `manually_credited_at`, and two stamped `double_pay_detected_at`. No `urgent_manual_credit` transaction row was found despite the three manual markers, indicating at least some historical/manual marker creation did not follow the current transaction-writing branch.

### Bulk status update permits unsafe state transitions

The page renders a checkbox on every row regardless of status and exposes global Paid and Cancel actions. `bulkUpdateStatus` wraps its loop in a DB transaction but never row-locks the withdrawal before reading status. Consequently two admins, cron, and the public payout webhook can act from the same stale state.

Specific executable transitions:

- A status-1 paid withdrawal is allowed to transition to status 2. The 30-minute/provider guard applies only when the row is status 0 with a transfer ID. Cancelling a paid row increments the creator's gross balance and marks it cancelled even though the bank payout already succeeded: direct double compensation.
- A status-0 Cashfree row can be marked paid without checking provider state. This removes it from phase-1 pending repair and sends “paid” push even if Cashfree later fails or never accepted it. The webhook may repair a signed/unsigned reversal only if an event arrives; a missing-transfer-ID row has no provider backstop.
- Re-marking status 1 as paid is allowed and sends another notification. Re-marking status 2 is rejected.
- Two simultaneous cancellations can both see non-cancelled state and each atomically increment the user's balance before both write status 2. The enclosing transaction does not make the read/check idempotent without a row lock.

Cancellation of a pending Cashfree transfer waits 30 minutes, then explicitly allows cancellation even when Cashfree still says `PENDING`, `PROCESSING`, `INITIATED`, or `APPROVED`. Provider exceptions after the hold also fail open. The design relies on cron phase 2 to flag a later success, but that phase is already proven to starve most of its 6,218-row live window. The system does not claw funds back automatically.

Paid notification is an external OneSignal call made from inside the bulk DB transaction. Provider/push latency holds the transaction open. Cancellation writes a ledger row only when a reason exists; the current UI requires one, but custom/internal callers can omit it because server validation makes it nullable.

### Urgent credit intentionally risks double pay and is itself raceable

The UI exposes Urgent Credit during the first 30 minutes and accurately warns that the original IMPS transfer cannot be cancelled. The controller refunds gross creator balance, records a transaction, marks status 2 plus manual-credit metadata, and expects cron phase 3 to flag a later provider success for human clawback.

The initial status/manual-credit checks and model loads occur before the transaction, and the transaction does not reload/lock the withdrawal. Two concurrent submissions can both pass, increment balance twice, create two ledgers, and overwrite the same marker. The three live manual markers and two detected double pays prove the underlying late-success outcome has occurred in production; they do not establish whether any creator repayment was recovered.

The double-pay badge has no acknowledgement state. It computes a placeholder unacknowledged filter, discards that value, and returns the all-time total for both fields. Alerts remain permanently counted after support resolves them.

### Spreadsheet import bypasses refund and double-pay safeguards

The separate XLS/XLSX upload route is deployed and authenticated but has no direct menu item in the current live sidebar; classify it as **manual/legacy reachable**, not normal page flow. It matches a row by current beneficiary name/account and embedded withdrawal ID, then:

- `accepted by bank` sets status 1 and sends the paid push;
- `failed` sets status 2 **without refunding the already-deducted creator balance**, without a cancellation ledger/reason, and without the live Cashfree guard;
- it rejects only rows already status 1. A previously cancelled/refunded status-2 row can be imported as accepted and moved back to paid while retaining the refund, creating an unmarked double-pay/accounting state;
- spreadsheet rows are not processed in one transaction. Earlier rows remain mutated even when later errors cause the overall result to display an error, encouraging a retry against a partially applied file.

Matching uses the user's current bank account, not immutable request details. Since the only `withdrawal_bank_details` row is overwritten per user, bank edits after export can make a legitimate report row fail to match. Conversely, editing a non-cancelled or paid withdrawal from the admin UI updates the **user's current bank account**, affecting future withdrawals; editing a cancelled row overwrites the one shared pseudo-snapshot used by all of that user's historical cancelled rows.

### Admin list performance and data semantics

The admin index accepts unbounded `per_page`, then performs one extra unindexed `withdrawal_bank_details.user_id` lookup per displayed row. A large page multiplies full scans of the roughly 18.8K-row details table. User search uses leading-wildcard name/mobile conditions, and date filtering wraps `datetime` in `DATE()`, reducing index efficiency. The live-status button makes a fresh Cashfree request, logs/returns the full provider details, and is correctly admin-facing but can expose more payout metadata than the status decision needs.

### Admin invoice/export reconciliation

The separate legacy Payments page is not the large `transactions` ledger: production metadata estimates only about 1,035 `payments` rows. Its visible invoice/export buttons are nevertheless broken because their controller target methods do not exist on any node; the orphan `PaymentsExport` class is not reachable through those actions.

The real transaction invoice and GSTR endpoints are executable. Single/bulk PDFs calculate tax and totals from nominal `transactions.amount`, while GSTR CSV substitutes positive `discount_price`; discounted purchases therefore disagree across official-looking outputs. Single invoice numbering counts all transaction types, while bulk PDF/CSV count only `add_coins` since 2025-08-18. Bulk PDF accepts an unrestricted range and loads every row/page into memory; CSV streams but records success before its callback runs. Both PDF paths make an unnecessary public HTTPS logo request during generation.

## Canonical money-state map and invariants

| Store | Meaning in executable code | Important non-invariants |
|---|---|---|
| `users.coins` | Current male spendable coin balance | Many paths use unlocked read-modify-write; concurrent credits/debits can overwrite. Some public/IDOR paths can mutate it without valid ownership/payment proof. |
| `users.total_coins` | Partial cumulative coin-credit counter | Updated by normal PG/direct welcome credits, but not subscription daily claim; it is not derivable from current balance or all credit events. |
| `users.balance` | Current creator withdrawable rupee balance, stored as integer | Call/gift/bonus credits and withdrawal/refund paths have different locking/ledger behavior. A row in `transactions` is not guaranteed for each mutation. |
| `users.total_income` | Partial cumulative creator earnings | Gifts and current call/bonus paths update it, while refunds/withdrawals have different semantics. It is not current payable balance and is not a complete immutable accounting total. |
| `transactions` | Mixed-unit event/history table | Not double-entry and has no logical unique/idempotency key. `coins`, `amount`, sign, and `payment_type` mean different things by type; daily subscription claims and ordinary withdrawal debits/refunds can be absent. |
| Provider payment/subscription tables | Local order/mandate/event status and provider identifiers | PhonePe and autopay have recovery/orphan/idempotency gaps; Android compatibility success is not provider credit success. |
| `withdrawals` | Gross creator debit plus net payout request; status 0 pending, 1 paid, 2 failed/cancelled/refunded by convention | Status 2 does not prove refund: XLS failure writes 2 without credit. Status 1 does not prove provider success: admin can set it. State can move 1→2 or 2→1 through manual paths. |

There is no single query that can reconstruct authoritative current coins/balance from `transactions`. That table is a UI/audit event stream with omissions and mixed conventions; `users.coins`/`balance` are operational truth at a moment, while provider tables, subscription events, daily claims, withdrawals, calls, and gift pairs are required to explain how the value arose. Any financial investigation must reconcile all relevant stores and account for IST/UTC timestamps.

Manual admin wallet mutation is an active visible UI, not only a crafted-request capability. The live user-edit form directly exposes `balance`, `coins`, and `total_coins`; `users.update` overwrites all three without validation, a transaction row, or an audit reason. The separate `users/{id}/add-coins`, `users/{id}/add-balance`, and `/femalereports/add-bonus` helpers perform unlocked read-modify-save and only then insert their ledger row, without a DB transaction or operation/idempotency key. A ledger failure can therefore leave a wallet-only credit, concurrent wallet writers can overwrite one another, resubmission after an unknown response can duplicate the whole credit, and the selected-user bonus loop can stop after only part of the batch was credited.

`addCoins` also reuses the payment discriminator: it inserts `type=add_coins` and `payment_type=Credit` but no `amount`, provider/order ID, reason, or marker that distinguishes an admin grant from a recharge. Current live consumers treat an `add_coins` row as purchase proof in best-offer, referral-offer, free-coin, no-recharge, abandoned-recharge, and inactive-new-user logic; analytics and invoice/export paths also count or render the same type. A manual credit can therefore make a nonpayer look paid, suppress or change offer/notification eligibility, alter purchaser/transaction counts and invoice numbering, and produce a null/zero-value invoice row. This is a semantic-ledger defect in addition to the atomicity defect.

### State and recovery summary

| Flow | Normal state progression | Recovery/exception boundary |
|---|---|---|
| PhonePe recharge | local pending → provider completed → one coin credit/ledger → completed | One-minute checker can permanently stop checking too early; webhook/checker races are not row-claimed; Android compatibility endpoint can say success before credit. |
| Cashfree recharge | local pending → provider paid verification → row-locked atomic credit/ledger/completion | Provider verification exception trusts webhook payload even in enforce mode; Android compatibility response is still not credit proof. |
| Autopay | no/current mandate → pending/authorizing → active → recurring success credit; cancel → cancelled | External create precedes local identity; old IDs become orphaned, cancel false is ignored, unknown successes are marked processed without credit, and no credit reconciler is scheduled. |
| Gift | sender coin debit + receiver balance/income credit + two independent event rows | No transaction/lock/idempotency/call binding; IDOR sender; partial and repeated transfers possible. |
| Bank withdrawal | eligibility → provider call while DB lock held → local status 0 → cron/provider success status 1 or failure status 2 + refund | Provider acceptance can exist without local row; public unsigned webhook; multi-node cron races; phase-2 starvation; the transient-failure retry classifier is log-only and still refunds; manual transitions break status/refund meaning. |
| UPI withdrawal | same intended lifecycle | Live-disabled. Setup can return success without persisted beneficiary ID, so enabling the flag would expose a broken preparation path. |

## Symptom-to-likely-cause matrix

| Symptom | Highest-probability audited causes and first evidence to check |
|---|---|
| Payment completed but coins absent | PhonePe row marked checked before late completion plus missed webhook; false-success compatibility response; Cashfree local row still pending/webhook absent; autopay provider ID orphan/unknown event; Google Play purchase consumed before dormant backend completion. Check provider/local order and ledger together, never the Android success screen alone. |
| Coins credited twice or ledger/balance disagree | Concurrent PhonePe/autopay/public completion, public mint endpoints, unlocked balance overwrite, failure between balance and transaction writes, or duplicate gift. Compare provider event/order ID, transaction count, and current user balance under the correct timezone. |
| Coins decreased but Transactions shows no debit | `coins_deduction < 10` is deliberately filtered; daily/autopay and other non-ledger paths; failed history page skipped due offset handling. Recent evidence shows the under-10 filter is common. |
| Transaction shows wrong call/person/duration | Historical male fuzzy fallback or creator timestamp guess instead of exact `call_id`; legacy null call IDs; ghost/self-heal timestamps. Check `transactions.call_id` first, then exact call row. |
| Creator Earnings is slow, times out, or says no records | Creator endpoint loads all received calls per ten rows and filesorts; a server/network error is rendered as empty; failed offset page is skipped. |
| Gift appears as `--N` | Backend stores sender gift coins negative and Android adds another minus. This is rendering, not necessarily a double debit. |
| Gift debit/credit missing, repeated, or peer saw no animation | Four independent server writes; sender IDOR; unknown response/retry; concurrent requests; animation/FCM occurs only after separate client success handling. |
| Autopay checkout says it could not start although mandate exists | Android lacks `already_active` and treats a successful response without session/redirect as failure. Poll subscription status and inspect current provider ID before retrying. |
| Autopay cancelled in app but later charged | Provider cancel returned false but local row was marked cancelled; terminal status is no longer polled; replacement overwrote the old ID. Search event history for all provider IDs, not only current `user_subscriptions`. |
| Autopay charged ₹299 but no 1,300 coins | Unknown/orphan provider ID, concurrent event claiming/balance overwrite, event marked processed without mapping, or amount/event parsing. The live orphan success population makes this a confirmed diagnostic branch. |
| UPI update succeeded but withdrawal unavailable | Live `upi=0`; or regex fallback saved local UPI while Cashfree beneficiary creation failed/omitted `bene_id`. |
| Bank withdrawal missing from HIMA but bank received money | Cashfree accepted before response/DB commit failed or timed out. Search provider by request context and logs carefully; retrying can pay twice because no durable local intent exists. |
| Pending withdrawal never resolves | Missing transfer ID; phase-1 provider error/backlog; public multi-node cron overlap; provider still pending; or scheduler HTTP timeout. Ten live pending rows existed, one without transfer ID. |
| Paid withdrawal later failed but was not refunded | Unsigned/signed webhook skipped status-0 and was acknowledged; webhook exception returned 200; phase 2 repeatedly starved the row outside newest 25; 30-day mode is off. |
| Creator balance refunded twice | Two cron/admin/webhook actors passed an unlocked status check; concurrent bulk cancellations; duplicated urgent-credit submission. |
| Creator got bank payout and balance refund | Admin cancelled paid/pending provider transfer; urgent credit followed by Cashfree success; provider timeout plus retry; phase-2 detection failure. Live data already contains two detected double pays. |
| Withdrawal status says cancelled but balance was not restored | XLS `failed` import writes status 2 without refund; partial failure between ordinary cron/admin saves; historical/manual mutation. Status alone is not refund proof. |
| Withdrawal says paid but bank did not receive money | Admin bulk Paid or spreadsheet accepted set status 1 without authoritative provider success; notification follows local label. Check Cashfree status/transfer ID rather than status field alone. |
| Bank details on an old withdrawal changed | The “snapshot” is one overwriteable row per user; admin edit on non-cancelled rows changes current user bank details; cancelled history reuses the latest shared row. |

## Checkpoint completion boundary

This checkpoint completed static caller-to-storage tracing, exact deployed-source verification for critical ranges/controllers, live feature/config classification, scheduler verification, schema/index performance gates, redacted aggregate evidence, and diagnostic matrices for wallet recharges, autopay, gifts, transaction history, bank/UPI verification, withdrawals, payout callbacks/reconciliation, and manual/admin withdrawal transitions.

The broader admin application, creator management, moderation, tickets, analytics, and AI features remain for the next checkpoint. Their unrelated mutations are not implied complete here; any later finding that changes a money path must be cross-linked back into this checkpoint.

No finding in this checkpoint authorizes a production, database, service, Git, payment, notification, payout, or other outward mutation.
