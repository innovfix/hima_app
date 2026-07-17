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
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.AiChatAdapter
import com.gmwapp.hima.adapters.AiChatMessage
import com.gmwapp.hima.databinding.ActivitySupportBotBinding
import com.gmwapp.hima.retrofit.responses.BotChip
import com.gmwapp.hima.retrofit.responses.BotInputMode
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.showAppToast
import com.gmwapp.hima.viewmodels.SupportBotViewModel
import com.zoho.salesiqembed.ZohoSalesIQ
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.lifecycle.lifecycleScope
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    /**
     * Spec #8. GetContent() with a wildcard so a screenshot, a screen recording
     * or a voice note all work from one picker — same contract
     * SubmitTicketActivity already uses, so no new permissions.
     */
    private val pickAttachment = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadAttachment(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportBotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        initUI()
        observe()
        viewModel.start()
    }

    /**
     * Zoho's floating launcher is turned on for female creators from the home
     * screen (FemaleHomeFragment:516) and would sit on top of the send button
     * here. Hide it for the duration and put it back on the way out.
     *
     * runCatching because Zoho is only initialised for some users; a support
     * screen must never crash because a chat SDK is not ready.
     */
    /**
     * Only restore the launcher if WE hid it. It is enabled for female
     * creators from FemaleHomeFragment:516 and off for everyone else, so
     * blindly calling showLauncher(true) on pause turned it ON for users who
     * never had it — a floating chat button appearing out of nowhere. Caught
     * by audit.
     */
    private var hidZohoLauncher = false

    override fun onResume() {
        super.onResume()
        runCatching {
            ZohoSalesIQ.showLauncher(false)
            hidZohoLauncher = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (!hidZohoLauncher) return
        runCatching { ZohoSalesIQ.showLauncher(true) }
        hidZohoLauncher = false
    }

    private fun initUI() {
        binding.includeProfileToolbar.tvFlowTitle.text = getString(R.string.support_bot_title)
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        adapter = AiChatAdapter(mutableListOf()) { chip -> onChipTapped(chip) }
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = adapter

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

        // Spec #9 — rating. Sends and closes; never blocks them.
        binding.btnRateSad.setOnSingleClickListener { sendRating(1) }
        binding.btnRateNeutral.setOnSingleClickListener { sendRating(2) }
        binding.btnRateGreat.setOnSingleClickListener { sendRating(3) }
    }

    /**
     * Spec #9 — CSAT, only ever after the user confirmed it was resolved.
     *
     * ratingSent latches: submitting a rating re-posts solved=true, and the
     * server answers ask_rating=true again — so without this the panel
     * reappears every time and the user can never finish. Caught by audit.
     */
    private var ratingSent = false

    private fun sendRating(score: Int) {
        if (ratingSent) return
        ratingSent = true
        binding.llRating.visibility = View.GONE
        viewModel.feedback(sessionId, solved = true, csat = score)
        showAppToast(getString(R.string.support_bot_rate_thanks), Toast.LENGTH_SHORT)
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
    private fun uploadAttachment(uri: Uri) {
        // Size FIRST, from the content resolver, before copying a byte.
        val size = runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && c.moveToFirst()) c.getLong(i) else -1L
            } ?: -1L
        }.getOrDefault(-1L)

        if (size > MAX_ATTACHMENT_BYTES) {
            showAppToast(getString(R.string.support_bot_attach_too_big), Toast.LENGTH_LONG)
            return
        }

        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        if (!(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))) {
            showAppToast(getString(R.string.support_bot_attach_bad_type), Toast.LENGTH_LONG)
            return
        }

        // Copy OFF the main thread; only then post.
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (file == null || file.length() > MAX_ATTACHMENT_BYTES) {
                file?.delete()
                showAppToast(getString(R.string.support_bot_attach_too_big), Toast.LENGTH_LONG)
                return@launch
            }
            pendingAttachment = file
            val part = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody(mime.toMediaTypeOrNull())
            )
            // NOTE: no chat bubble yet — it goes up only when the server
            // confirms, so a rejected file cannot look accepted.
            viewModel.attach(sessionId, part)
        }
    }

    /** Cleaned up as soon as the upload resolves, either way. */
    private var pendingAttachment: File? = null

    /** The last thing the user actually did, so "Try again" replays IT. */
    private data class BotAction(val choiceKey: String? = null, val userMessage: String? = null)
    private var lastAction: BotAction? = null

    private fun copyToCache(uri: Uri): File? = runCatching {
        val out = File(cacheDir, "support_" + System.currentTimeMillis())
        contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out
    }.getOrNull()

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
            // Spec #6 — the second, different attempt. No ticket yet; they get
            // another Yes/No.
            if (r.second_attempt) {
                botSays(r.ai_message, null)
                binding.tvFeedbackPrompt.text = r.feedback_prompt.orEmpty()
                showInput(BotInputMode.YESNO)
                return@observe
            }

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
                if (r.ask_rating) binding.llRating.visibility = View.VISIBLE
            }
        }

        viewModel.attachLiveData.observe(this) { r ->
            // Cache file is dead either way.
            pendingAttachment?.delete(); pendingAttachment = null

            if (!r.success) {
                // The server's own reason, rendered — a rejected upload used to
                // look identical to a successful one.
                showAppToast(
                    when (r.code) {
                        "too_large" -> getString(R.string.support_bot_attach_too_big)
                        "bad_type" -> getString(R.string.support_bot_attach_bad_type)
                        "too_many" -> getString(R.string.support_bot_attach_too_many)
                        else -> getString(R.string.support_bot_error)
                    },
                    Toast.LENGTH_LONG
                )
                return@observe
            }
            botSays(r.ai_message, null)
        }

        viewModel.loadingLiveData.observe(this) { loading ->
            if (loading) adapter.showTyping() else adapter.hideTyping()
            scrollToEnd()
        }

        // Network/server trouble mid-conversation: stay in the chat and offer
        // a retry. Do not swap the screen out from under them.
        viewModel.errorLiveData.observe(this) {
            showRetry()
        }
    }

    /** Spec #8 — always optional, always after the ticket exists. */
    private fun offerAttachment(prompt: String?) {
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
        when (chip.key) {
            "__attach" -> {
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
        if (text.isEmpty()) return
        binding.etMessage.setText("")
        adapter.addMessage(AiChatMessage(text, isUser = true))
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
        binding.tvViewTicket.visibility =
            if (mode == BotInputMode.NONE && ticketId != null) View.VISIBLE else View.GONE
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
}
