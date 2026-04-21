package com.gmwapp.hima.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ViewFlipper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.AiChatAdapter
import com.gmwapp.hima.adapters.AiChatMessage
import com.gmwapp.hima.adapters.MatchedCreatorAdapter
import com.gmwapp.hima.adapters.ProgressMatchAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAiOnboardingBinding
import com.gmwapp.hima.retrofit.responses.MatchedCreator
import com.gmwapp.hima.viewmodels.AiOnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AiOnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiOnboardingBinding
    private val viewModel: AiOnboardingViewModel by viewModels()
    private lateinit var chatAdapter: AiChatAdapter

    private var sessionId: Int? = null
    private var userId: Int = 0
    private var userLanguage: String = "Tamil"
    private var currentStep: Int = 0
    private var selectedConcern: String = "loneliness"

    // Delivery animation (Screen 3): avatar glides down the list as each
    // matched creator's message is "delivered" one-by-one.
    private val deliveryHandler = Handler(Looper.getMainLooper())
    private var isDeliveryRunning = false
    private val deliveryStaggerMs = 550L
    private val deliveryTailMs = 800L

    // Tanglish translations — regional language words in English/Roman script
    private val concernTranslations = mapOf(
        "Tamil" to mapOf("breakup" to "Breakup", "loneliness" to "Thanimai", "stress" to "Stress", "boredom" to "Boring",
            "title" to "Inniki eppadi feel pannureenga?", "subtitle" to "Sollunga, unga ku correct aanavanga connect pannrom"),
        "Hindi" to mapOf("breakup" to "Breakup", "loneliness" to "Akela", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Aaj kaisa feel kar rahe ho?", "subtitle" to "Batao, sahi logon se connect karenge"),
        "Telugu" to mapOf("breakup" to "Breakup", "loneliness" to "Ontaritanam", "stress" to "Stress", "boredom" to "Boring",
            "title" to "Ee roju ela feel avthunnaru?", "subtitle" to "Cheppandi, correct people connect chestam"),
        "Kannada" to mapOf("breakup" to "Breakup", "loneliness" to "Onti", "stress" to "Stress", "boredom" to "Boring",
            "title" to "Ivattu heg feel agthidira?", "subtitle" to "Heli, right janara connect madthivi"),
        "Malayalam" to mapOf("breakup" to "Breakup", "loneliness" to "Ekanatha", "stress" to "Stress", "boredom" to "Boring",
            "title" to "Innu engane feel cheyyunnu?", "subtitle" to "Parayo, correct aalukale connect cheyyam"),
        "Marathi" to mapOf("breakup" to "Breakup", "loneliness" to "Ekta", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Aaj kasa feel hotay?", "subtitle" to "Sanga, yogya lokanshe jodto"),
        "Bengali" to mapOf("breakup" to "Breakup", "loneliness" to "Ekla", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Aaj kemon feel korcho?", "subtitle" to "Bolo, thik manusher sathe connect korbo"),
        "Assamese" to mapOf("breakup" to "Breakup", "loneliness" to "Okola", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Aji kenekuwa feel korisaa?", "subtitle" to "Kowa, correct manuh logot connect korim"),
        "Odia" to mapOf("breakup" to "Breakup", "loneliness" to "Ekalaa", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Aaji kemiti feel karuchha?", "subtitle" to "Kahanta, thik loka saha connect kariba"),
        "Gujarati" to mapOf("breakup" to "Breakup", "loneliness" to "Ekla", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Aaje kem feel thay chhe?", "subtitle" to "Kahejo, yogya loko sathe connect karishu"),
        "Punjabi" to mapOf("breakup" to "Breakup", "loneliness" to "Ikalla", "stress" to "Tension", "boredom" to "Boring",
            "title" to "Ajj kiven feel kar rahe ho?", "subtitle" to "Dasso, sahi logaan naal connect karange")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("USER_ID", 0)
        userLanguage = intent.getStringExtra(DConstants.LANGUAGE) ?: "Tamil"

        if (userId == 0) {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            userId = userData?.id ?: 0
        }

        setupConcernCards()
        setupChat()
        observeViewModel()
    }

    private fun setupConcernCards() {
        val translations = concernTranslations[userLanguage] ?: concernTranslations["Tamil"]!!

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userName = userData?.name?.replace(Regex("[0-9]"), "")?.takeIf { it.isNotBlank() } ?: "friend"

        binding.tvTitle.text = "Hi $userName! ${translations["title"] ?: "How are you feeling today?"}"
        binding.tvSubtitle.text = translations["subtitle"] ?: "Let us know, and we'll connect you with the right people"
        binding.tvBreakup.text = translations["breakup"] ?: "Breakup"
        binding.tvLoneliness.text = translations["loneliness"] ?: "Loneliness"
        binding.tvStress.text = translations["stress"] ?: "Stress"
        binding.tvBoredom.text = translations["boredom"] ?: "Boredom"

        binding.cardBreakup.setOnClickListener { startAiChat("breakup") }
        binding.cardLoneliness.setOnClickListener { startAiChat("loneliness") }
        binding.cardStress.setOnClickListener { startAiChat("stress") }
        binding.cardBoredom.setOnClickListener { startAiChat("boredom") }
    }

    private fun setupChat() {
        chatAdapter = AiChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@AiOnboardingActivity)
            adapter = chatAdapter
        }

        binding.btnSend.setOnClickListener { sendUserMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendUserMessage()
                true
            } else false
        }

        binding.btnLetsGo.setOnClickListener { completeOnboarding() }
        binding.btnStartChatting.setOnClickListener { navigateToMain() }
    }

    private fun observeViewModel() {
        viewModel.startLiveData.observe(this, Observer { response ->
            if (response.success && response.sessionId != null) {
                sessionId = response.sessionId
                currentStep = response.step ?: 0
                chatAdapter.hideTyping()
                response.aiMessage?.let {
                    chatAdapter.addMessage(AiChatMessage(it, isUser = false))
                    scrollToBottom()
                }
                binding.tvTypingStatus.text = "Online"
                binding.etMessage.isEnabled = true
                binding.btnSend.isEnabled = true
            }
        })

        viewModel.replyLiveData.observe(this, Observer { response ->
            if (response.success) {
                currentStep = response.step ?: currentStep
                chatAdapter.hideTyping()
                response.aiMessage?.let {
                    chatAdapter.addMessage(AiChatMessage(it, isUser = false))
                    scrollToBottom()
                }
                binding.tvTypingStatus.text = "Online"

                // Single exchange enforced: after first reply, hide input bar
                binding.inputBar.visibility = View.GONE
                binding.btnLetsGo.visibility = View.VISIBLE
            }
        })

        viewModel.completeLiveData.observe(this, Observer { response ->
            val creators = response?.matchedCreators ?: emptyList()
            if (creators.isEmpty()) {
                // No matches → skip the personalised animation and jump straight home.
                binding.loadingOverlay.visibility = View.GONE
                navigateToMain()
                return@Observer
            }
            startDeliveryAnimation(creators)
        })

        viewModel.errorLiveData.observe(this, Observer { error ->
            android.util.Log.w("AiOnboarding", "Error: $error")
            binding.tvTypingStatus.text = "Online"
            binding.etMessage.isEnabled = true
            binding.btnSend.isEnabled = true
            binding.loadingOverlay.visibility = View.GONE
            chatAdapter.hideTyping()

            // On error during start, use language-aware Tanglish fallback
            if (sessionId == null) {
                binding.inputBar.visibility = View.GONE
                binding.btnLetsGo.visibility = View.VISIBLE
                chatAdapter.addMessage(AiChatMessage(
                    localisedFallback(selectedConcern, userLanguage),
                    isUser = false
                ))
                scrollToBottom()
            }
        })

        viewModel.loadingLiveData.observe(this, Observer { loading ->
            if (loading) {
                binding.tvTypingStatus.text = "typing..."
            }
        })
    }

    private fun startAiChat(concern: String) {
        selectedConcern = concern
        binding.viewFlipper.displayedChild = 1
        binding.etMessage.isEnabled = false
        binding.btnSend.isEnabled = false
        binding.tvTypingStatus.text = "typing..."
        chatAdapter.showTyping()
        scrollToBottom()
        viewModel.startOnboarding(userId, concern)
    }

    private fun localisedFallback(concern: String, language: String): String {
        val byLang = mapOf(
            "Tamil" to mapOf(
                "breakup" to "Ayyo da, breakup-a? \uD83D\uDC94 Romba kashtama irukum... Inga caring people irukanga, konjam nerathula unaku connect panren \uD83E\uDEC2",
                "loneliness" to "Hey da, thanimai-a feel panreengala? \uD83D\uDE14 Naan inga irukken... caring people kitta konnect panren \uD83E\uDD17",
                "stress" to "Stress-la irukeengala da? \uD83D\uDE30 Deep breath edunga... super people kitta connect panren \uD83D\uDCAD",
                "boredom" to "Boring-a irukka da? \uD83D\uDE34 Don't worry, fun people kitta connect panren \uD83C\uDF1F"
            ),
            "Hindi" to mapOf(
                "breakup" to "Arey yaar, breakup hua? \uD83D\uDC94 Bahut mushkil hota hai... Don't worry, accha log hai yahan \uD83E\uDEC2",
                "loneliness" to "Arey yaar, akela feel ho raha? \uD83D\uDE14 Main hoon yahan... caring log se connect karenge \uD83E\uDD17",
                "stress" to "Stress mein ho? \uD83D\uDE30 Deep breath lo... super log se baat karenge \uD83D\uDCAD",
                "boredom" to "Bore ho rahe? \uD83D\uDE34 Fun log ready hai, connect karte hain \uD83C\uDF1F"
            ),
            "Telugu" to mapOf(
                "breakup" to "Ayyo breakup-a? \uD83D\uDC94 Chala kashtamga untundi... Caring people unnaru ikkada \uD83E\uDEC2",
                "loneliness" to "Ontaritanam-a feel avthunnava? \uD83D\uDE14 Nenu unnanu... caring people ki connect chestha \uD83E\uDD17",
                "stress" to "Stress lo unnava? \uD83D\uDE30 Deep breath tiskondi... super people ki connect chestha \uD83D\uDCAD",
                "boredom" to "Boring ga undha? \uD83D\uDE34 Fun people unnaru, connect chestha \uD83C\uDF1F"
            ),
            "Kannada" to mapOf(
                "breakup" to "Ayyo breakup-a? \uD83D\uDC94 Tumba kashtavaagutte... Caring people iddare illi \uD83E\uDEC2",
                "loneliness" to "Onti feel aagutte? \uD83D\uDE14 Naanu iddini... caring people ge connect maadtini \uD83E\uDD17",
                "stress" to "Stress-nalli iddira? \uD83D\uDE30 Deep breath tagoli... super people ge connect maadtini \uD83D\uDCAD",
                "boredom" to "Boring aagide? \uD83D\uDE34 Fun people idaare, connect maadtini \uD83C\uDF1F"
            ),
            "Malayalam" to mapOf(
                "breakup" to "Ayyo breakup-o? \uD83D\uDC94 Valare kashtam aanu... Caring aalukal undu ivide \uD83E\uDEC2",
                "loneliness" to "Ekanatha feel cheyyunno? \uD83D\uDE14 Njaan undu... caring aalukalkku connect cheyaam \uD83E\uDD17",
                "stress" to "Stress aano? \uD83D\uDE30 Deep breath edukku... super aalukalkku connect cheyaam \uD83D\uDCAD",
                "boredom" to "Boring aano? \uD83D\uDE34 Fun aalukal undu, connect cheyaam \uD83C\uDF1F"
            )
        )
        val map = byLang[language] ?: byLang["Tamil"]!!
        return map[concern] ?: map["loneliness"]!!
    }

    private fun sendUserMessage() {
        val message = binding.etMessage.text?.toString()?.trim() ?: return
        if (message.isEmpty()) return

        chatAdapter.addMessage(AiChatMessage(message, isUser = true))
        binding.etMessage.setText("")
        binding.etMessage.isEnabled = false
        binding.btnSend.isEnabled = false
        binding.inputBar.visibility = View.GONE // hide immediately - single exchange
        binding.tvTypingStatus.text = "typing..."
        chatAdapter.showTyping()
        scrollToBottom()

        val sid = sessionId
        if (sid != null) {
            viewModel.replyOnboarding(sid, message)
        }
    }

    private fun completeOnboarding() {
        val sid = sessionId
        if (sid != null) {
            binding.loadingOverlay.visibility = View.VISIBLE
            viewModel.completeOnboarding(sid)
        } else {
            // No session (error path) — skip to main
            navigateToMain()
        }
    }

    private fun showCreatorsList(creators: List<MatchedCreator>) {
        binding.viewFlipper.displayedChild = 2
        binding.rvCreators.layoutManager = LinearLayoutManager(this)
        binding.rvCreators.adapter = MatchedCreatorAdapter(this, creators)
    }

    /**
     * Show the personalised delivery screen: creator avatars populate in a
     * list, a Hima-branded messenger bubble glides down the list, and each
     * row ticks green one-by-one. After the last tick we navigate home.
     */
    private fun startDeliveryAnimation(creators: List<MatchedCreator>) {
        isDeliveryRunning = true
        binding.loadingOverlay.visibility = View.GONE
        binding.viewFlipper.displayedChild = 2

        val adapter = ProgressMatchAdapter(this, creators)
        binding.rvCreators.layoutManager = LinearLayoutManager(this)
        binding.rvCreators.adapter = adapter

        binding.rvCreators.post {
            binding.ivMessengerAvatar.visibility = View.VISIBLE
            creators.indices.forEach { i ->
                deliveryHandler.postDelayed({
                    adapter.markDelivered(i)
                    slideMessengerToRow(i)
                }, i * deliveryStaggerMs)
            }
            val totalMs = creators.size * deliveryStaggerMs + deliveryTailMs
            deliveryHandler.postDelayed({
                isDeliveryRunning = false
                navigateToMain()
            }, totalMs)
        }
    }

    /**
     * Translate the messenger bubble to sit centred-vertically on the row
     * at [index]. First call also positions it horizontally. Falls back
     * silently if the row hasn't been laid out yet.
     */
    private fun slideMessengerToRow(index: Int) {
        val rv = binding.rvCreators
        val rowView = rv.layoutManager?.findViewByPosition(index) ?: return
        val rowCentre = rowView.top + (rowView.height / 2f)
        val messengerHalf = binding.ivMessengerAvatar.height / 2f
        val targetY = rowCentre - messengerHalf
        ObjectAnimator.ofFloat(binding.ivMessengerAvatar, "translationY", binding.ivMessengerAvatar.translationY, targetY)
            .setDuration(380)
            .start()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(DConstants.LANGUAGE, userLanguage)
        intent.putExtra("SHOW_MY_CHATS", true)
        intent.putExtra("FROM_ONBOARDING", true)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.onboarding_transition_in, R.anim.onboarding_transition_out)
        finish()
    }

    private fun scrollToBottom() {
        binding.rvChat.post {
            val count = chatAdapter.itemCount
            if (count > 0) {
                binding.rvChat.smoothScrollToPosition(count - 1)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.viewFlipper.displayedChild == 1) {
            // Don't allow going back to concern selection mid-chat
            // User must complete or skip
            return
        }
        if (binding.viewFlipper.displayedChild == 2 && isDeliveryRunning) {
            // Don't interrupt the delivery animation
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        deliveryHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
