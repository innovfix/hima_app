package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
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
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAiOnboardingBinding
import com.gmwapp.hima.retrofit.responses.MatchedCreator
import com.gmwapp.hima.utils.applySystemBarInsets
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoCompleteRunnable: Runnable? = null
    private val sendAnimationRunnables = mutableListOf<Runnable>()

    // Tanglish / Hinglish / Tenglish translations — regional language words
    // in English/Roman script (e.g. "manasu aara venum" rather than "மனசு ஆற வேணும்").
    private val concernTranslations = mapOf(
        "Tamil" to mapOf(
            "title" to "Hima app-la enna ethir paathu varenga?",
            "subtitle" to "Sollunga, ungalukku right-aana aalai connect pannrom",
            "breakup" to "Breakup",
            "loneliness" to "Thanimai",
            "stress" to "Stress",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup aachu… manasu aara venum",
            "loneliness_desc" to "Thanimai-a feel panren, pesa aalu venum",
            "stress_desc" to "Life-la stress jaasti, love-aa pesurathukku aalu venum",
            "boredom_desc" to "Boring-a iruken, fun-aa pesanum"
        ),
        "Hindi" to mapOf(
            "title" to "Hima app par kya umeed se aaye ho?",
            "subtitle" to "Batao, sahi insaan se connect kar denge",
            "breakup" to "Breakup",
            "loneliness" to "Akela",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup hua, dil ko sukoon chahiye",
            "loneliness_desc" to "Akela feel ho raha, koi baat karne wala chahiye",
            "stress_desc" to "Life stress-ful, pyaar se sunne wala chahiye",
            "boredom_desc" to "Bore ho raha, fun baatein karni hai"
        ),
        "Telugu" to mapOf(
            "title" to "Hima app ki enti expect chesi vachcharu?",
            "subtitle" to "Cheppandi, correct manishini connect chestam",
            "breakup" to "Breakup",
            "loneliness" to "Ontaritanam",
            "stress" to "Stress",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup aindi, manasuki prashantata kavali",
            "loneliness_desc" to "Ontarigaa feel avthunna, matladataniki evaro kavali",
            "stress_desc" to "Life lo stress ekkuva, prematho vinevaru kavali",
            "boredom_desc" to "Boring gaa undi, fun gaa maatladali"
        ),
        "Kannada" to mapOf(
            "title" to "Hima app alli enu expect maadi bandidira?",
            "subtitle" to "Heli, sariyada vyaktiya jote connect madtivi",
            "breakup" to "Breakup",
            "loneliness" to "Onti",
            "stress" to "Stress",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup aagide, manasige nemmadi beku",
            "loneliness_desc" to "Onti antha anstaide, matadokke yaaro beku",
            "stress_desc" to "Life-alli stress jaasti, preeti inda keluvavru beku",
            "boredom_desc" to "Boring agtaide, fun-aagi matadbeku"
        ),
        "Malayalam" to mapOf(
            "title" to "Hima app-il enthu pratheekshichaa vannathu?",
            "subtitle" to "Parayoo, sariyaya aalkalumayi connect cheyyam",
            "breakup" to "Breakup",
            "loneliness" to "Ekanatha",
            "stress" to "Stress",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup aayi, manassinu aashwasam venam",
            "loneliness_desc" to "Othakku feel cheyyunnu, samsarikkan aarelum venam",
            "stress_desc" to "Life-il stress kooduthal, snehathode kelkkan aalu venam",
            "boredom_desc" to "Boring aanu, fun-aayi samsaarikkanam"
        ),
        "Marathi" to mapOf(
            "title" to "Hima app var kay apeksha ghevun aalays?",
            "subtitle" to "Sanga, yogya manasacha jodun deu",
            "breakup" to "Breakup",
            "loneliness" to "Ekta",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup zhala, manala shantata havi",
            "loneliness_desc" to "Ekata vaatatay, boloya sathi koni pahije",
            "stress_desc" to "Life madhe stress jast, premane aiknaara pahije",
            "boredom_desc" to "Bore zhalay, fun bolaycha aahe"
        ),
        "Bengali" to mapOf(
            "title" to "Hima app-e ki asha niye esecho?",
            "subtitle" to "Bolo, thik manusher sathe connect kore debo",
            "breakup" to "Breakup",
            "loneliness" to "Ekla",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup hoyeche, mone shanti lagbe",
            "loneliness_desc" to "Ekla lagche, kotha bolar keu chai",
            "stress_desc" to "Life-e stress beshi, bhalobese shone emon keu chai",
            "boredom_desc" to "Bore lagche, fun kotha bolte chai"
        ),
        "Assamese" to mapOf(
            "title" to "Hima app-ot ki asha loi ahisa?",
            "subtitle" to "Kowa, sothik manuhor logot connect koraim",
            "breakup" to "Breakup",
            "loneliness" to "Okola",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup hol, monot shanti lage",
            "loneliness_desc" to "Okola lagi ase, katha pator babe konoba lage",
            "stress_desc" to "Life-t stress beshi, morome huna ekjon lage",
            "boredom_desc" to "Bore lagi ase, fun-koi katha patibo khuju"
        ),
        "Odia" to mapOf(
            "title" to "Hima app ku kana asha neigala aasile?",
            "subtitle" to "Kahanta, thik loka sahit connect kariba",
            "breakup" to "Breakup",
            "loneliness" to "Ekalaa",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup heigala, manaku shanti darkar",
            "loneliness_desc" to "Ekaki lagucchi, katha hebaku kehi darkar",
            "stress_desc" to "Life re stress beshi, premare shunibaaku kehi darkar",
            "boredom_desc" to "Bore lagucchi, fun katha hebaku chahe"
        ),
        "Gujarati" to mapOf(
            "title" to "Hima app par shu aasha laine aavya?",
            "subtitle" to "Kahejo, yogya vyakti saathe connect karishu",
            "breakup" to "Breakup",
            "loneliness" to "Ekla",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup thayo, man-ne shanti joiye",
            "loneliness_desc" to "Ekla feel thay, vaat karva mate koi joiye",
            "stress_desc" to "Life ma stress vadhu, premthi sambhalnar joiye",
            "boredom_desc" to "Bore thay chhe, fun vaat karvi chhe"
        ),
        "Punjabi" to mapOf(
            "title" to "Hima app te ki umeed naal aaye ho?",
            "subtitle" to "Dasso, sahi insaan naal connect karange",
            "breakup" to "Breakup",
            "loneliness" to "Ikalla",
            "stress" to "Tension",
            "boredom" to "Boring",
            "breakup_desc" to "Breakup ho gaya, man nu sukoon chaahida",
            "loneliness_desc" to "Ikalla feel ho reha, gall karan waala chaahida",
            "stress_desc" to "Life ch stress jyada, pyaar naal sunan waala chaahida",
            "boredom_desc" to "Bore ho reha, fun galan karni aa"
        )
    )

    // Strings used while we're picking creators and animating the "sent" rows.
    // Tanglish/Hinglish/etc. — matches the vibe of concernTranslations above so the
    // whole onboarding flow speaks in one voice instead of switching to formal English
    // at the final step. Use %s as the creator name placeholder.
    private val sendingTranslations = mapOf(
        "Tamil" to mapOf(
            "searching" to "Unakaha theduren…",
            "sending_title" to "Unakaha message anupuren…",
            "happy" to "Avanga pesuvanga, nee happy ya iruppa",
            "sending_to" to "%s ku message anupuren…",
            "sent_to" to "%s ku message sent",
            "connecting" to "Unna correct aalukalukku connect pannuren…"
        ),
        "Hindi" to mapOf(
            "searching" to "Tumhare liye dhundh raha hoon…",
            "sending_title" to "Tumhare liye message bhej raha hoon…",
            "happy" to "Wo baat karenge, tum khush ho jaoge",
            "sending_to" to "%s ko message bhej raha hoon…",
            "sent_to" to "%s ko message bhej diya",
            "connecting" to "Tumhe sahi logon se milwa raha hoon…"
        ),
        "Telugu" to mapOf(
            "searching" to "Nee kosam vethukutunnanu…",
            "sending_title" to "Nee kosam message pampistunnanu…",
            "happy" to "Vaallu matladataru, nuvvu happy ga untaavu",
            "sending_to" to "%s ki message pampistunnanu…",
            "sent_to" to "%s ki message pampincha",
            "connecting" to "Nee kosam sariyaina vaallani connect chestunna…"
        ),
        "Kannada" to mapOf(
            "searching" to "Ninage hudukta idini…",
            "sending_title" to "Ninage message kaltidini…",
            "happy" to "Avaru maatadtaare, ninna mood chennaag aagutte",
            "sending_to" to "%s ge message kaltidini…",
            "sent_to" to "%s ge message kalsida",
            "connecting" to "Ninage sariyaada janara jote connect madtidini…"
        ),
        "Malayalam" to mapOf(
            "searching" to "Ninakku venti thappunnu…",
            "sending_title" to "Ninakku venti message ayakkunnu…",
            "happy" to "Avar samsarikum, nee happy aakum",
            "sending_to" to "%s inu message ayakkunnu…",
            "sent_to" to "%s inu message ayachu",
            "connecting" to "Ninne correct aalukkal kude connect cheyyunnu…"
        ),
        "Marathi" to mapOf(
            "searching" to "Tuzyasaathi shodhatoy…",
            "sending_title" to "Tuzyasaathi message pathavtoy…",
            "happy" to "Te boltil, tu khush hoshi",
            "sending_to" to "%s la message pathavtoy…",
            "sent_to" to "%s la message pathavla",
            "connecting" to "Tula yogya lokanshi jodto aahe…"
        ),
        "Bengali" to mapOf(
            "searching" to "Tomar jonno khujchi…",
            "sending_title" to "Tomar jonno message pathacchi…",
            "happy" to "Tara kotha bolbe, tumi khushi hobe",
            "sending_to" to "%s ke message pathacchi…",
            "sent_to" to "%s ke message pathiye diyechi",
            "connecting" to "Tomake thik manusher sathe jor diye dichhi…"
        ),
        "Assamese" to mapOf(
            "searching" to "Tumar babe bisari asu…",
            "sending_title" to "Tumar babe message pothai asu…",
            "happy" to "Xihote kotha kobo, tumi khushi hobaa",
            "sending_to" to "%s loi message pothai asu…",
            "sent_to" to "%s loi message pothai dilu",
            "connecting" to "Tumak xothik manuh logot connect kori asu…"
        ),
        "Odia" to mapOf(
            "searching" to "Tuma paain khujuchi…",
            "sending_title" to "Tuma paain message pathaucchi…",
            "happy" to "Semane katha kahibe, tu khushi heba",
            "sending_to" to "%s ku message pathaucchi…",
            "sent_to" to "%s ku message pathai dichi",
            "connecting" to "Tuma paain thik loka sahit connect karucchi…"
        ),
        "Gujarati" to mapOf(
            "searching" to "Tamara mate khoji rahyo chhu…",
            "sending_title" to "Tamara mate message mokli rahyo chhu…",
            "happy" to "Te vaat karshe, tame khush thai jasho",
            "sending_to" to "%s ne message mokli rahyo chhu…",
            "sent_to" to "%s ne message mokli didho",
            "connecting" to "Tamne yogya loko sathe connect kari rahyo chhu…"
        ),
        "Punjabi" to mapOf(
            "searching" to "Tuhade layi labbh reha haan…",
            "sending_title" to "Tuhade layi message bhej reha haan…",
            "happy" to "Oh gall karange, tusi khush ho jaao ge",
            "sending_to" to "%s nu message bhej reha haan…",
            "sent_to" to "%s nu message bhej dita",
            "connecting" to "Tuhanu sahi logan naal connect kar reha haan…"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Screen 1 (concerns) + Screen 3 (creators) are white; only Screen 2
        // (chat) has a pink header. Use a white status bar with DARK icons so
        // time/WiFi/battery are visible on every screen in the flipper.
        applySystemBarInsets(
            binding.root,
            R.color.white,
            applyIme = true,
            darkStatusBarIcons = true,
        )

        userId = intent.getIntExtra("USER_ID", 0)
        userLanguage = intent.getStringExtra(DConstants.LANGUAGE) ?: "Tamil"

        if (userId == 0) {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            userId = userData?.id ?: 0
        }

        // Without a userId, the AI session calls will fail silently. Bail out to login
        // rather than letting the user hit unresponsive cards — and don't just finish(),
        // because this activity was launched with CLEAR_TASK and the back stack is empty,
        // so finish() alone would drop the user out of the app entirely.
        if (userId == 0) {
            Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_LONG).show()
            val loginIntent = Intent(this, NewLoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(loginIntent)
            finish()
            return
        }

        setupConcernCards()
        setupChat()
        observeViewModel()
    }

    private fun setupConcernCards() {
        val translations = concernTranslations[userLanguage] ?: concernTranslations["Tamil"]!!

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userName = userData?.name?.replace(Regex("[0-9]"), "")?.takeIf { it.isNotBlank() } ?: "friend"

        binding.tvTitle.text = "Hi $userName! ${translations["title"] ?: "What brings you to Hima?"}"
        binding.tvSubtitle.text = translations["subtitle"]
            ?: "Tell us why, and we'll connect you with the right person"
        binding.tvBreakup.text = translations["breakup"] ?: "Breakup"
        binding.tvLoneliness.text = translations["loneliness"] ?: "Loneliness"
        binding.tvStress.text = translations["stress"] ?: "Stress"
        binding.tvBoredom.text = translations["boredom"] ?: "Boredom"

        binding.tvBreakupDesc.text = translations["breakup_desc"] ?: "Breakup aachu, need healing"
        binding.tvLonelinessDesc.text = translations["loneliness_desc"] ?: "Feeling alone, need someone to talk to"
        binding.tvStressDesc.text = translations["stress_desc"] ?: "Stressed, need someone to listen with love"
        binding.tvBoredomDesc.text = translations["boredom_desc"] ?: "Bored, want a fun conversation"

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

                // Single exchange enforced: after AI's reply, auto-proceed to the
                // match + send step. No tap required. Give the user a beat to read
                // the AI's message first.
                binding.inputBar.visibility = View.GONE
                binding.btnLetsGo.visibility = View.GONE
                scheduleAutoComplete()
            }
        })

        viewModel.completeLiveData.observe(this, Observer { response ->
            // Server has picked matches and (optionally) seeded chats. Render a
            // staggered "sent" animation so the user feels the AI is messaging each
            // creator for them, then hop to the home tab.
            showSendingAnimation(response.matchedCreators.orEmpty())
        })

        viewModel.errorLiveData.observe(this, Observer { error ->
            binding.tvTypingStatus.text = "Online"
            binding.etMessage.isEnabled = true
            binding.btnSend.isEnabled = true
            binding.loadingOverlay.visibility = View.GONE

            // On error during start, use fallback and allow continue
            if (sessionId == null) {
                binding.inputBar.visibility = View.GONE
                binding.btnLetsGo.visibility = View.VISIBLE
                chatAdapter.addMessage(AiChatMessage(
                    "Don't worry, we have wonderful people here who would love to talk with you!",
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
        binding.viewFlipper.displayedChild = 1
        binding.etMessage.isEnabled = false
        binding.btnSend.isEnabled = false
        binding.tvTypingStatus.text = "typing..."
        chatAdapter.showTyping()
        scrollToBottom()
        viewModel.startOnboarding(userId, concern)
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
            // Keep the chat in view so the AI's last reply is still readable. Just
            // show a typing indicator at the bottom while the server picks matches;
            // the "sent to …" lines will roll in as AI messages once the response
            // arrives.
            binding.loadingOverlay.visibility = View.GONE
            chatAdapter.showTyping()
            scrollToBottom()
            viewModel.completeOnboarding(sid)
        } else {
            // No session (error path) — skip to main
            navigateToMain()
        }
    }

    private fun sendingT(): Map<String, String> =
        sendingTranslations[userLanguage] ?: sendingTranslations["Tamil"]!!

    private fun scheduleAutoComplete() {
        autoCompleteRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { completeOnboarding() }
        autoCompleteRunnable = r
        mainHandler.postDelayed(r, AUTO_COMPLETE_DELAY_MS)
    }

    private fun showSendingAnimation(creators: List<MatchedCreator>) {
        // Clear any previous run's pending posts so a retry doesn't double-animate.
        sendAnimationRunnables.forEach { mainHandler.removeCallbacks(it) }
        sendAnimationRunnables.clear()

        // No overlay — everything plays inline in the chat so the user can still see
        // the AI's previous replies above the "sent" updates.
        binding.loadingOverlay.visibility = View.GONE

        val t = sendingT()
        val sentFmt = t["sent_to"] ?: "Sent to %s"

        // Drop the waiting typing indicator that completeOnboarding put in place.
        chatAdapter.hideTyping()

        // Lead-in AI message framing what's about to happen.
        chatAdapter.addMessage(
            AiChatMessage(t["sending_title"] ?: "Sending your messages…", isUser = false)
        )
        scrollToBottom()

        if (creators.isEmpty()) {
            // No matches returned — consolation line, then navigate home so the
            // transition still feels deliberate.
            val connectingRunnable = Runnable {
                chatAdapter.addMessage(
                    AiChatMessage(
                        t["connecting"] ?: "Connecting you to creators…",
                        isUser = false
                    )
                )
                scrollToBottom()
            }
            sendAnimationRunnables.add(connectingRunnable)
            mainHandler.postDelayed(connectingRunnable, 600L)

            val finish = Runnable { navigateToMain() }
            sendAnimationRunnables.add(finish)
            mainHandler.postDelayed(finish, 2200L)
            return
        }

        // For each creator: show typing briefly, then post the "sent to" line.
        // Looks like the AI is dispatching messages to each person in turn.
        var timeline = PER_ITEM_DELAY_MS
        creators.forEachIndexed { index, creator ->
            val typingAt = timeline
            val typingRunnable = Runnable {
                chatAdapter.showTyping()
                scrollToBottom()
            }
            sendAnimationRunnables.add(typingRunnable)
            mainHandler.postDelayed(typingRunnable, typingAt)

            val sentAt = timeline + TYPING_DWELL_MS
            val sentRunnable = Runnable {
                chatAdapter.hideTyping()
                val name = creator.name.ifBlank { "Creator ${index + 1}" }
                chatAdapter.addMessage(AiChatMessage(sentFmt.format(name), isUser = false))
                scrollToBottom()
            }
            sendAnimationRunnables.add(sentRunnable)
            mainHandler.postDelayed(sentRunnable, sentAt)

            timeline = sentAt + PER_ITEM_DELAY_MS
        }

        // Warm sign-off line ("they'll talk to you, you'll be happy"), then home.
        val closingRunnable = Runnable {
            chatAdapter.addMessage(
                AiChatMessage(
                    t["happy"] ?: "They'll talk to you and make you happy",
                    isUser = false
                )
            )
            scrollToBottom()
        }
        sendAnimationRunnables.add(closingRunnable)
        mainHandler.postDelayed(closingRunnable, timeline)

        val finish = Runnable { navigateToMain() }
        sendAnimationRunnables.add(finish)
        mainHandler.postDelayed(finish, timeline + 1500L)
    }

    private fun showCreatorsList(creators: List<com.gmwapp.hima.retrofit.responses.MatchedCreator>) {
        binding.viewFlipper.displayedChild = 2
        binding.rvCreators.layoutManager = LinearLayoutManager(this)
        binding.rvCreators.adapter = MatchedCreatorAdapter(this, creators)
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
        super.onBackPressed()
    }

    override fun onDestroy() {
        autoCompleteRunnable?.let { mainHandler.removeCallbacks(it) }
        sendAnimationRunnables.forEach { mainHandler.removeCallbacks(it) }
        sendAnimationRunnables.clear()
        super.onDestroy()
    }

    companion object {
        private const val AUTO_COMPLETE_DELAY_MS = 2000L
        private const val PER_ITEM_DELAY_MS = 700L
        private const val TYPING_DWELL_MS = 500L
    }
}
