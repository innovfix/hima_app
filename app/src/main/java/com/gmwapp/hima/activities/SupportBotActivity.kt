package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.AiChatAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.adapters.AiChatMessage
import com.gmwapp.hima.databinding.ActivitySupportBotBinding
import com.gmwapp.hima.retrofit.responses.BotChip
import com.gmwapp.hima.retrofit.responses.BotInputMode
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.showAppToast
import com.gmwapp.hima.viewmodels.SupportBotViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zoho.salesiqembed.ZohoSalesIQ
import dagger.hilt.android.AndroidEntryPoint

/**
 * In-app AI support chat.
 *
 * The server drives everything: what to say, what options to show, and which
 * input to offer (chips / text / yesno / none). This screen renders whatever
 * it is handed and posts back a `key`. It deliberately knows NOTHING about the
 * category tree — those sub-categories will churn weekly off the Yes/No data,
 * and a client-side taxonomy would mean a Play Store release per iteration.
 * (See AiOnboardingActivity.concernTranslations for the anti-pattern: 11
 * languages x 6 strings in Kotlin, so a 12th language is a release.)
 *
 * The bot IS the support channel. A network or server hiccup keeps the user in
 * the chat with a retry — it does NOT tip them back into the old raise-ticket
 * form, which reads as a bug and lands them in the very queue this exists to
 * shrink. The one exception is the ops kill-switch (bot_disabled), which is a
 * deliberate decision to route everyone to the form and must keep working
 * without a release.
 */
@AndroidEntryPoint
class SupportBotActivity : BaseActivity() {

    private lateinit var binding: ActivitySupportBotBinding
    private val viewModel: SupportBotViewModel by viewModels()

    private lateinit var adapter: AiChatAdapter
    private var sessionId: Int = 0
    private var ticketId: Int? = null

    /** Matches the server's 5MB ceiling — checked here so we never copy 200MB to be told no. */
    private val MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024

    /** Matches the server's per-ticket cap (support_bot_attach: >= 3 -> too_many). */
    private val MAX_ATTACHMENTS = 3

    /**
     * Spec #8. GetMultipleContents() with a wildcard so a screenshot, a screen
     * recording or several files can be picked AT ONCE from one picker (QA
     * HS_002: single-select was one-file-at-a-time). Same contract family
     * SubmitTicketActivity uses, so no new permissions.
     */
    private val pickAttachment = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> -> if (uris.isNotEmpty()) uploadAttachments(uris) }

    // Uploads are serialised by the ViewModel (attachInFlight), and the server
    // appends each to the ticket, so a multi-pick is uploaded ONE AT A TIME off
    // this queue. A single "Added" confirmation is shown at the end, not one per
    // file (QA HS_003).
    private val uploadQueue: ArrayDeque<Uri> = ArrayDeque()
    private var uploadsActive = false
    private var anyUploadSucceeded = false
    private var uploadConfirmMsg: String? = null

    /** Attachments can be added only ONCE per ticket — after the first pick the
     *  "Add screenshot / proof" option is not offered again. */
    private var attachmentAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportBotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        initUI()
        observe()

        // Resume (P1-G). If a session from a previous, killed run is still on
        // disk, rebuild it so the user lands back where they left off instead
        // of re-answering four questions. A stale/expired id fails soft: the
        // resume_failed branch clears it and starts fresh.
        val saved = BaseApplication.getInstance()?.getPrefs()?.getString(KEY_ACTIVE_SESSION)?.toIntOrNull() ?: 0
        if (saved > 0) viewModel.session(saved) else viewModel.start()
    }

    private fun saveActiveSession() {
        if (sessionId > 0) {
            BaseApplication.getInstance()?.getPrefs()?.setString(KEY_ACTIVE_SESSION, sessionId.toString())
        }
    }

    /** Called once a session is terminal, so the NEXT open starts fresh. */
    private fun clearActiveSession() {
        BaseApplication.getInstance()?.getPrefs()?.setString(KEY_ACTIVE_SESSION, "")
    }

    /**
     * Zoho's floating launcher is turned on ONLY for female users, from the
     * home screen (FemaleHomeFragment:516), and would sit on top of the send
     * button here. Hide it while this screen is up, restore it on the way out.
     *
     * Gate on gender, not on "did I call hide". The first attempt latched
     * hidZohoLauncher whenever showLauncher(false) succeeded — but that call
     * succeeds for EVERYONE, so onPause then enabled a launcher for users
     * (e.g. male) who never had one: a floating chat button out of nowhere.
     * showLauncher exposes no "is it visible" query, so the population that
     * has it — female — is the correct and only safe gate. Everyone else: we
     * never touch it.
     *
     * runCatching because Zoho is only initialised for some users; a support
     * screen must never crash because a chat SDK is not ready.
     */
    private val userHasZohoLauncher: Boolean by lazy {
        BaseApplication.getInstance()?.getPrefs()?.getUserData()
            ?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true
    }

    override fun onResume() {
        super.onResume()
        if (!userHasZohoLauncher) return
        runCatching { ZohoSalesIQ.showLauncher(false) }
    }

    override fun onPause() {
        super.onPause()
        if (!userHasZohoLauncher) return
        runCatching { ZohoSalesIQ.showLauncher(true) }
    }

    private fun initUI() {
        binding.includeProfileToolbar.tvFlowTitle.text = getString(R.string.support_bot_title)
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        adapter = AiChatAdapter(mutableListOf()) { chip -> onChipTapped(chip) }
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = adapter

        // Keep the latest message visible when the keyboard opens. Tapping the
        // input shrinks the list (its bottom rises); without this the newest bot
        // message is left hidden behind the keyboard and the user has to scroll
        // by hand. On any shrink, snap to the last row.
        binding.rvChat.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                binding.rvChat.post {
                    val n = binding.rvChat.adapter?.itemCount ?: 0
                    if (n > 0) binding.rvChat.scrollToPosition(n - 1)
                }
            }
        }
        binding.etMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToEnd()
        }

        binding.ivSend.setOnSingleClickListener { sendTyped() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped(); true
            } else false
        }

        binding.btnSolved.setOnSingleClickListener {
            // The ONLY thing in the whole system that resolves a ticket.
            showInput(BotInputMode.NONE)
            viewModel.feedback(sessionId, solved = true)
        }
        binding.btnNotSolved.setOnSingleClickListener {
            showInput(BotInputMode.NONE)
            viewModel.feedback(sessionId, solved = false)
        }

        binding.tvViewTicket.setOnSingleClickListener {
            startActivity(Intent(this, TicketsListActivity::class.java))
            finish()
        }

    }

    /**
     * Spec #9 — CSAT, only ever after the user confirmed it was resolved.
     *
     * ratingSent latches: submitting a rating re-posts solved=true, and the
     * server answers ask_rating=true again — so without this the panel
     * reappears every time and the user can never finish. Caught by audit.
     *
     * awaitingRatingAck: the rating re-post ALSO returns the closing message
     * ("Ticket close panniten") a second time. Without this the feedback
     * observer rendered that closing bubble twice. Set before the rating call
     * and consumed at the top of the observer so the ack is swallowed silently
     * — the thank-you now lives in the sheet, not the chat.
     */
    private var ratingSent = false
    private var awaitingRatingAck = false
    private var csatSheet: BottomSheetDialog? = null

    /**
     * True while a request is in flight. Blocks a second send — a typed message
     * followed by a Skip tap (chips were left enabled) fired two requests at
     * once and wedged the typing dots forever. Reset by loadingLiveData.
     */
    private var busy = false

    /**
     * Polls support_bot_session while a reply is in flight on a resumed session,
     * until the model's answer lands (step advances) or the ceiling passes. 2.5s
     * cadence, capped so a genuinely dead request cannot poll forever.
     */
    private val resumePollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var resumePollAttempts = 0
    private val resumePollRunnable = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        if (sessionId > 0) viewModel.session(sessionId)
    }

    private fun scheduleResumePoll() {
        resumePollAttempts++
        // Backstop only. The server flips in_progress=false once the session goes
        // stale (~40s), which resolves the poll cleanly before this ever trips;
        // this just stops a hung network from polling forever.
        if (resumePollAttempts > 20) { // ~50s
            adapter.hideTyping()
            busy = false
            showRetry()
            return
        }
        resumePollHandler.removeCallbacks(resumePollRunnable)
        resumePollHandler.postDelayed(resumePollRunnable, 2500)
    }

    private fun cancelResumePoll() {
        resumePollAttempts = 0
        resumePollHandler.removeCallbacks(resumePollRunnable)
    }

    /**
     * CSAT as a bottom-sheet popup (not an inline chat row). Shown once, after
     * the user confirmed the issue was resolved.
     */
    private fun showCsatSheet() {
        if (ratingSent || isFinishing) return
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_support_csat, null)
        sheet.setContentView(view)

        val ask = view.findViewById<View>(R.id.ll_csat_ask)
        val thanks = view.findViewById<View>(R.id.ll_csat_thanks)

        view.findViewById<View>(R.id.btn_csat_sad).setOnSingleClickListener { onCsatPicked(1, ask, thanks, sheet) }
        view.findViewById<View>(R.id.btn_csat_neutral).setOnSingleClickListener { onCsatPicked(2, ask, thanks, sheet) }
        view.findViewById<View>(R.id.btn_csat_great).setOnSingleClickListener { onCsatPicked(3, ask, thanks, sheet) }
        view.findViewById<View>(R.id.btn_csat_done).setOnSingleClickListener { sheet.dismiss() }

        csatSheet = sheet
        sheet.show()
    }

    private fun onCsatPicked(score: Int, ask: View, thanks: View, sheet: BottomSheetDialog) {
        if (ratingSent) return
        ratingSent = true
        awaitingRatingAck = true          // swallow the duplicate closing message
        viewModel.feedback(sessionId, solved = true, csat = score)

        ask.visibility = View.GONE
        thanks.visibility = View.VISIBLE
        // Stays up long enough to read; the user can dismiss early via Done or by
        // swiping the sheet down.
        thanks.postDelayed({ if (!isFinishing) sheet.dismiss() }, 15000)
    }

    override fun onDestroy() {
        cancelResumePoll()
        csatSheet?.dismiss()
        csatSheet = null
        super.onDestroy()
    }

    /**
     * Spec #8 — optional; the ticket already exists either way.
     *
     * Audit caught three things here: the copy ran on the MAIN THREAD (an ANR
     * on a big screen recording), there was no size check before copying (5MB
     * server limit, so a 200MB video was copied in full and then rejected),
     * and the filename was echoed into the chat BEFORE the upload, with the
     * response's success/code ignored — so a rejected file looked accepted.
     */
    /**
     * The ViewModel owns the whole attachment lifecycle (copy, bound, upload,
     * cleanup) because it and the upload survive rotation while this Activity
     * does not — so the file is never deleted mid-upload. We just hand it the
     * uri and observe the outcome.
     */
    private fun uploadAttachments(uris: List<Uri>) {
        val picked = uris.take(MAX_ATTACHMENTS)
        if (picked.isEmpty()) return
        if (uris.size > MAX_ATTACHMENTS) {
            showAppToast(getString(R.string.support_bot_attach_too_many), Toast.LENGTH_SHORT)
        }
        // Show the picked files as small thumbnails on the user's side, and take
        // away the "Add" option — attachments can be added only once.
        adapter.addMessage(AiChatMessage("", isUser = true, attachmentUris = picked))
        adapter.setChipsEnabled(false)
        attachmentAdded = true
        scrollToEnd()

        uploadQueue.clear()
        picked.forEach { uploadQueue.addLast(it) }
        uploadsActive = true
        anyUploadSucceeded = false
        uploadConfirmMsg = null
        startNextUpload()
    }

    /** Kick off the next queued upload. Uploads stay serial: continueUploads()
     *  starts the next one only after the current has settled. */
    private fun startNextUpload() {
        val uri = uploadQueue.removeFirstOrNull() ?: return
        viewModel.uploadAttachment(applicationContext, uri, sessionId, MAX_ATTACHMENT_BYTES)
    }

    /** After each upload settles (success or failure): start the next queued
     *  file, or — when the whole batch is done — show ONE "Added" confirmation. */
    private fun continueUploads() {
        if (!uploadsActive) return
        if (uploadQueue.isNotEmpty()) {
            startNextUpload()
            return
        }
        uploadsActive = false
        if (anyUploadSucceeded) {
            botSays(uploadConfirmMsg, null)
            anyUploadSucceeded = false
        }
    }

    /** The last thing the user actually did, so "Try again" replays IT. */
    private data class BotAction(val choiceKey: String? = null, val userMessage: String? = null)
    private var lastAction: BotAction? = null

    private fun observe() {
        viewModel.startLiveData.observe(this) { r ->
            if (!r.success) {
                when (r.code) {
                    // Already has a ticket being worked: send them to it rather
                    // than walk them through four questions and then refuse.
                    "pending_escalated_ticket" -> {
                        showAppToast(r.message, Toast.LENGTH_LONG)
                        startActivity(Intent(this, TicketsListActivity::class.java))
                        finish()
                    }
                    // The ops kill-switch is the ONLY route back to the old
                    // form: a deliberate decision, not a failure.
                    "bot_disabled" -> fallbackToForm()
                    else -> showRetry()
                }
                return@observe
            }

            sessionId = r.session_id ?: 0
            if (sessionId == 0) { showRetry(); return@observe }
            saveActiveSession()

            // Spec #11 — during an incident this goes FIRST, before the menu.
            // Someone whose calls are down because the platform is down should
            // not have to answer four questions to be told that.
            if (!r.known_issue.isNullOrBlank()) {
                adapter.addMessage(AiChatMessage(r.known_issue, isUser = false))
            }

            botSays(r.ai_message, r.chips)
            showInput(r.input_mode)
        }

        viewModel.replyLiveData.observe(this) { r ->
            adapter.hideTyping()
            if (!r.success) {
                // The previous request is still running server-side. Tell them
                // to wait rather than offering a retry that will just collide.
                if (r.code == "in_progress") {
                    showAppToast(r.message, Toast.LENGTH_SHORT)
                    return@observe
                }
                showRetry(); return@observe
            }

            ticketId = r.ticket_id
            botSays(r.ai_message, r.chips)

            if (r.input_mode == BotInputMode.YESNO) {
                binding.tvFeedbackPrompt.text = r.feedback_prompt.orEmpty()
            }
            if (r.escalated_immediately && r.ticket_id != null) {
                binding.tvViewTicket.text = getString(R.string.support_bot_view_ticket, r.ticket_id)
                // A ticket now exists — this session is done; start fresh next
                // time (reopening would hit pending_escalated_ticket anyway).
                clearActiveSession()
            }
            showInput(r.input_mode)
            if (!r.out_of_hours.isNullOrBlank()) {
                adapter.addMessage(AiChatMessage(r.out_of_hours, isUser = false))
            }
            // The server already sent an __attach chip in r.chips on the
            // immediate-escalation path, and botSays() rendered it. Adding
            // another here put TWO identical attach buttons on screen. Only
            // offer one when the server did NOT send chips of its own.
            if (r.can_attach && r.chips.isNullOrEmpty()) offerAttachment(r.attach_prompt)
        }

        viewModel.feedbackLiveData.observe(this) { r ->
            // The rating re-post's response is an acknowledgement only — the
            // thank-you already showed in the sheet. Swallow it so the closing
            // message is not rendered into the chat a second time.
            if (awaitingRatingAck) {
                awaitingRatingAck = false
                return@observe
            }

            // Spec #6 — the second, different attempt. No ticket yet; they get
            // another Yes/No.
            if (r.second_attempt) {
                botSays(r.ai_message, null)
                binding.tvFeedbackPrompt.text = r.feedback_prompt.orEmpty()
                showInput(BotInputMode.YESNO)
                return@observe
            }

            // Past the second attempt the session is terminal either way
            // (resolved, or a ticket raised) — the next open should start
            // fresh, not resume this closed conversation.
            clearActiveSession()

            botSays(r.ai_message, null)
            ticketId = r.ticket_id

            if (r.escalated && r.ticket_id != null) {
                binding.tvViewTicket.text = getString(R.string.support_bot_view_ticket, r.ticket_id)
                showInput(BotInputMode.NONE)
                binding.tvViewTicket.visibility = View.VISIBLE
                // Spec #7 — if the team is off shift, say when they'll look.
                if (!r.out_of_hours.isNullOrBlank()) {
                    adapter.addMessage(AiChatMessage(r.out_of_hours, isUser = false))
                }
                // Spec #8 — offered the moment a human is involved, which is
                // when a screenshot stops being clutter and becomes evidence.
                if (r.can_attach) offerAttachment(r.attach_prompt)
            } else {
                showInput(BotInputMode.NONE)
                // Spec #9 — rating only after they confirmed it's resolved.
                // Now a bottom-sheet popup, not an inline chat row.
                if (r.ask_rating) showCsatSheet()
            }
        }

        // Success body only (the ViewModel owns file cleanup and the in-flight
        // flag now). A server-side reject (too_many, etc.) still comes back as
        // success=false in the body.
        viewModel.attachLiveData.observe(this) { r ->
            if (r.success) {
                // Record success + keep the confirmation text, but do NOT post it
                // per file — one "Added" is shown when the whole batch finishes
                // (QA HS_003).
                anyUploadSucceeded = true
                uploadConfirmMsg = r.ai_message
            } else {
                showAppToast(
                    when (r.code) {
                        "too_large" -> getString(R.string.support_bot_attach_too_big)
                        "bad_type" -> getString(R.string.support_bot_attach_bad_type)
                        "too_many" -> getString(R.string.support_bot_attach_too_many)
                        else -> getString(R.string.support_bot_error)
                    },
                    Toast.LENGTH_LONG
                )
            }
            // Continue the batch AFTER this frame, once the ViewModel's cleanup
            // has reset attachInFlight (it resets it right after emitting here).
            binding.rvChat.post { continueUploads() }
        }

        // Client-side validation / network failure for an attachment. Distinct
        // from the conversation errorLiveData so a failed upload never trips the
        // chat retry, and the in-flight flag has already been reset by the VM.
        viewModel.attachErrorLiveData.observe(this) { code ->
            val msg = when (code) {
                "too_big" -> getString(R.string.support_bot_attach_too_big)
                "bad_type" -> getString(R.string.support_bot_attach_bad_type)
                "in_flight" -> getString(R.string.support_bot_attach_wait)
                "no_network" -> getString(R.string.support_bot_error)
                else -> getString(R.string.support_bot_error)
            }
            showAppToast(msg, Toast.LENGTH_LONG)
            // A client-side reject (too_big/bad_type) skips the upload entirely
            // and never flips attachInFlight, so continue the batch from here too.
            binding.rvChat.post { continueUploads() }
        }

        viewModel.loadingLiveData.observe(this) { loading ->
            busy = loading
            if (loading) adapter.showTyping() else adapter.hideTyping()
            scrollToEnd()
        }

        viewModel.sessionLiveData.observe(this) { r ->
            adapter.hideTyping()
            renderResumedSession(r)
        }

        // Network/server trouble mid-conversation: stay in the chat and offer
        // a retry. Do not swap the screen out from under them.
        viewModel.errorLiveData.observe(this) { err ->
            // A failed resume is not a conversation failure — there is no chat
            // yet to retry. Drop the stale id and start a fresh session.
            if (err == "resume_failed") {
                clearActiveSession()
                viewModel.start()
                return@observe
            }
            showRetry()
        }
    }

    /** Spec #8 — always optional, always after the ticket exists. */
    private fun offerAttachment(prompt: String?) {
        // Attachments can be added only once — if they already added, don't offer
        // the option again (e.g. on a resumed session or a repeated escalation).
        if (attachmentAdded) return
        if (!prompt.isNullOrBlank()) {
            adapter.addMessage(AiChatMessage(prompt, isUser = false))
        }
        adapter.setChipsEnabled(true)
        adapter.addMessage(
            AiChatMessage(
                "",
                isUser = false,
                chips = listOf(BotChip("__attach", getString(R.string.support_bot_attach_cta)))
            )
        )
        scrollToEnd()
    }

    private fun onChipTapped(chip: BotChip) {
        // A request is already running — ignore taps (incl. Skip) until it lands,
        // so we never fire two overlapping requests and wedge the typing dots.
        if (busy) return
        when (chip.key) {
            "__attach" -> {
                // Add only once.
                if (attachmentAdded) return
                // Wildcard: image, video or audio in one picker.
                pickAttachment.launch("*/*")
                return
            }
            "__retry" -> {
                // Replay the action that FAILED, not a sentinel.
                //
                // This used to post choice_key="__noop", which falls through to
                // the answer step — so "Try again" after a network blip could
                // run the model with empty text and produce a fresh, unrelated
                // answer. Caught by audit. The server is now idempotent past
                // step 5, but the client should still resend what it meant.
                adapter.setChipsEnabled(false)
                val last = lastAction
                when {
                    sessionId <= 0 -> viewModel.start()
                    last == null -> viewModel.start()
                    else -> viewModel.reply(sessionId, last.choiceKey, last.userMessage)
                }
                return
            }
            "__view_ticket" -> {
                startActivity(Intent(this, TicketsListActivity::class.java)); finish(); return
            }
            "__skip" -> {
                adapter.addMessage(AiChatMessage(chip.label, isUser = true))
                adapter.setChipsEnabled(false)
                lastAction = BotAction(userMessage = "__skip")
                viewModel.reply(sessionId, userMessage = "__skip"); return
            }
        }

        // Echo their choice so the conversation reads back like a conversation.
        adapter.addMessage(AiChatMessage(chip.label, isUser = true))
        adapter.setChipsEnabled(false)
        scrollToEnd()
        lastAction = BotAction(choiceKey = chip.key)
        viewModel.reply(sessionId, choiceKey = chip.key)
    }

    private fun sendTyped() {
        val text = binding.etMessage.text.toString().trim()
        if (busy || text.isEmpty()) return
        busy = true
        binding.etMessage.setText("")
        adapter.addMessage(AiChatMessage(text, isUser = true))
        // Disable the chips (the Skip button) the moment they type — leaving it
        // live let a Skip tap fire a second request and freeze the typing dots.
        adapter.setChipsEnabled(false)
        scrollToEnd()
        lastAction = BotAction(userMessage = text)
        viewModel.reply(sessionId, userMessage = text)
    }

    private fun botSays(message: String?, chips: List<BotChip>?) {
        if (!message.isNullOrBlank()) {
            adapter.addMessage(AiChatMessage(message, isUser = false))
        }
        if (!chips.isNullOrEmpty()) {
            adapter.setChipsEnabled(true)
            adapter.addMessage(AiChatMessage("", isUser = false, chips = chips))
        }
        scrollToEnd()
    }

    /** Exactly one input is visible; the server decides which. */
    private fun showInput(mode: String?) {
        binding.llTextInput.visibility = if (mode == BotInputMode.TEXT) View.VISIBLE else View.GONE
        binding.llYesno.visibility = if (mode == BotInputMode.YESNO) View.VISIBLE else View.GONE
        // The "View ticket #N" button. Always set its label here so it can never
        // show as an empty pink bar: some escalation paths made it visible
        // (ticketId set, mode NONE) without the branch that sets its text having
        // run. Guaranteeing the text whenever it is shown closes that gap.
        val tid = ticketId ?: 0
        val showTicket = mode == BotInputMode.NONE && tid > 0
        binding.tvViewTicket.visibility = if (showTicket) View.VISIBLE else View.GONE
        if (showTicket) {
            binding.tvViewTicket.text = getString(R.string.support_bot_view_ticket, tid)
        }
    }

    private fun scrollToEnd() {
        binding.rvChat.post {
            val n = binding.rvChat.adapter?.itemCount ?: 0
            if (n > 0) binding.rvChat.smoothScrollToPosition(n - 1)
        }
    }

    /**
     * The bot IS the support channel — we do not tip people back into the old
     * form when something hiccups (owner decision, 2026-07-17: "no manual").
     * Silently swapping the chat for a blank form mid-conversation reads as a
     * bug, and the form is the queue this whole thing exists to shrink.
     *
     * So a transient failure stays in the chat and offers a retry. The ONE
     * exception is the ops kill-switch (bot_disabled): that is a deliberate
     * decision to route everyone to the form, not a failure, and it has to
     * keep working without a release.
     */
    private fun showRetry() {
        adapter.hideTyping()
        adapter.addMessage(
            AiChatMessage(getString(R.string.support_bot_error), isUser = false)
        )
        adapter.setChipsEnabled(true)
        adapter.addMessage(
            AiChatMessage(
                "",
                isUser = false,
                chips = listOf(BotChip("__retry", getString(R.string.support_bot_retry)))
            )
        )
        showInput(BotInputMode.CHIPS)
        scrollToEnd()
    }

    /** Only for the deliberate ops kill-switch. */
    private fun fallbackToForm() {
        startActivity(Intent(this, SubmitTicketActivity::class.java))
        finish()
    }

    /**
     * Resume (P1-G). Rebuild a session reopened after the app was killed: the
     * transcript first, then the live input exactly as the server last left it.
     * The last bot turn IS the current prompt, so it is already in history —
     * we only add the chip bubble / feedback bar on top, never the text again.
     */
    private fun renderResumedSession(r: com.gmwapp.hima.retrofit.responses.SupportBotSessionResponse) {
        sessionId = r.session_id ?: 0
        if (sessionId <= 0) { clearActiveSession(); viewModel.start(); return }
        saveActiveSession()
        ticketId = r.ticket_id

        // Rebuild from scratch — this runs on every resume AND on every in-flight
        // poll below, so appending would stack duplicate transcripts.
        adapter.clear()
        r.history?.forEach { turn ->
            adapter.addMessage(AiChatMessage(turn.text, isUser = turn.role == "user"))
        }

        // A reply is still running server-side (the user sent a message, then
        // backgrounded the app before the model answered). The pending message is
        // already spliced into history above; show the typing dots, keep input
        // locked, and poll until the real answer lands — instead of dropping the
        // message and then rejecting their next send with "one moment".
        if (r.in_progress) {
            busy = true
            showInput(BotInputMode.NONE)
            adapter.showTyping()
            scrollToEnd()
            scheduleResumePoll()
            return
        }
        cancelResumePoll()

        when (r.input_mode) {
            BotInputMode.CHIPS -> {
                if (!r.chips.isNullOrEmpty()) {
                    adapter.setChipsEnabled(true)
                    adapter.addMessage(AiChatMessage("", isUser = false, chips = r.chips))
                }
                showInput(BotInputMode.CHIPS)
            }
            BotInputMode.TEXT -> showInput(BotInputMode.TEXT)
            BotInputMode.YESNO -> {
                binding.tvFeedbackPrompt.text = r.feedback_prompt.orEmpty()
                showInput(BotInputMode.YESNO)
            }
            else -> {
                // Terminal (escalated). Any leftover action chips (attach /
                // view ticket) are still valid on a resumed session.
                if (!r.chips.isNullOrEmpty()) {
                    adapter.setChipsEnabled(true)
                    adapter.addMessage(AiChatMessage("", isUser = false, chips = r.chips))
                }
                r.ticket_id?.let {
                    binding.tvViewTicket.text = getString(R.string.support_bot_view_ticket, it)
                }
                showInput(BotInputMode.NONE)
            }
        }
        scrollToEnd()
    }

    companion object {
        private const val KEY_ACTIVE_SESSION = "support_bot_active_session"
    }
}
