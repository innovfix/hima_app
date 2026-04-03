package com.gmwapp.hima.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.HimaAiChatAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityHimaForYouBinding
import com.gmwapp.hima.models.AiChatMessage
import com.gmwapp.hima.models.MessageType
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HimaForYouActivity : BaseActivity() {

    private lateinit var binding: ActivityHimaForYouBinding
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val walletViewModel: WalletViewModel by viewModels()

    private lateinit var chatAdapter: HimaAiChatAdapter
    private val chatMessages = mutableListOf<AiChatMessage>()

    private var cachedAutoPayCoinId: String? = null
    private var cachedAutoPayAmount: String? = null
    private var coinListLoaded = false
    private var whyHimaChoice: String = "lonely"
    private var loadedPeople: List<FemaleUsersResponseData> = emptyList()
    private var peopleLoaded = false
    private var messageIdCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHimaForYouBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        whyHimaChoice = intent.getStringExtra(DConstants.WHY_HIMA_CHOICE) ?: "lonely"

        initChat()
        initInput()
        loadPeople()
        loadCoinPackagesForAutoPay()
        startConversation()
    }

    private fun initChat() {
        chatAdapter = HimaAiChatAdapter(this, chatMessages) {
            navigateToWallet()
        }
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter
        binding.rvChat.itemAnimator?.addDuration = 300

        binding.ivClose.setOnSingleClickListener {
            navigateToMain()
        }
    }

    private fun initInput() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnSend.isEnabled = !s.isNullOrBlank()
            }
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isEmpty()) return@setOnClickListener
            onUserSendMessage(text)
        }
    }

    private fun startConversation() {
        lifecycleScope.launch {
            // Step 1: AI Introduction
            delay(500)
            showTypingIndicator()
            delay(1500)
            removeTypingAndAddMessage(getString(R.string.hima_ai_intro))

            delay(800)
            showTypingIndicator()
            delay(1500)
            removeTypingAndAddMessage(getContextualQuestion())

            // Show input field
            delay(300)
            showInputArea()
        }
    }

    private fun onUserSendMessage(text: String) {
        // Add user message
        addMessage(text, MessageType.USER_SENT)

        // Hide input and keyboard
        binding.llInput.visibility = View.GONE
        hideKeyboard()

        // Clear input
        binding.etMessage.setText("")

        // Start AI response
        lifecycleScope.launch {
            delay(500)
            showTypingIndicator()
            delay(2000)

            // Extract name and build personalized response
            val name = extractName(text)
            val response = getPersonalizedResponse(name)
            removeTypingAndAddMessage(response)

            // Step 4: Sending messages animation
            delay(800)
            showTypingIndicator()
            delay(1200)
            removeTypingAndAddMessage(getString(R.string.hima_ai_finding))

            delay(600)
            addMessage("", MessageType.SENDING_STATUS)

            // Wait for people data if not loaded yet
            delay(3000)

            chatAdapter.markSendingComplete()
            scrollToBottom()

            delay(500)
            chatAdapter.setPeopleData(loadedPeople.ifEmpty {
                // If API hasn't returned yet, show what we have
                loadedPeople
            })
            addMessage("", MessageType.PEOPLE_CARDS)

            delay(800)
            addMessage("", MessageType.PAY_CTA)
        }
    }

    // -- Helpers --

    private fun nextId(): Int = messageIdCounter++

    private fun addMessage(text: String, type: MessageType) {
        chatAdapter.addMessage(AiChatMessage(nextId(), text, type))
        scrollToBottom()
    }

    private fun showTypingIndicator() {
        addMessage("", MessageType.TYPING_INDICATOR)
    }

    private fun removeTypingAndAddMessage(text: String) {
        chatAdapter.removeLastTypingIndicator()
        addMessage(text, MessageType.AI_RECEIVED)
    }

    private fun scrollToBottom() {
        binding.rvChat.post {
            if (chatMessages.isNotEmpty()) {
                binding.rvChat.smoothScrollToPosition(chatMessages.size - 1)
            }
        }
    }

    private fun showInputArea() {
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 300 }
        binding.llInput.visibility = View.VISIBLE
        binding.llInput.startAnimation(fadeIn)
        binding.etMessage.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.etMessage.postDelayed({
            imm.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
    }

    // -- Contextual Messages --

    private fun getContextualQuestion(): String {
        return when (whyHimaChoice) {
            "lonely" -> getString(R.string.hima_ai_lonely_q)
            "breakup" -> getString(R.string.hima_ai_breakup_q)
            "friends" -> getString(R.string.hima_ai_friends_q)
            "fun" -> getString(R.string.hima_ai_fun_q)
            "understand" -> getString(R.string.hima_ai_understand_q)
            else -> getString(R.string.hima_ai_lonely_q)
        }
    }

    private fun getPersonalizedResponse(name: String?): String {
        return when (whyHimaChoice) {
            "breakup" -> {
                if (name != null) getString(R.string.hima_ai_response_breakup_name, name)
                else getString(R.string.hima_ai_response_breakup_noname)
            }
            "lonely" -> {
                if (name != null) getString(R.string.hima_ai_response_lonely_name, name)
                else getString(R.string.hima_ai_response_lonely_noname)
            }
            "friends" -> getString(R.string.hima_ai_response_friends)
            "fun" -> getString(R.string.hima_ai_response_fun)
            "understand" -> getString(R.string.hima_ai_response_understand)
            else -> getString(R.string.hima_ai_response_lonely_noname)
        }
    }

    // -- Name Extraction --

    private val commonWords = setOf(
        "i", "me", "my", "the", "a", "an", "is", "was", "am", "are", "were",
        "he", "she", "it", "we", "they", "and", "but", "so", "because",
        "really", "very", "just", "dont", "can", "will", "would", "could",
        "should", "have", "had", "been", "being", "feel", "feeling",
        "im", "ive", "its", "thats", "left", "right", "want", "need",
        "help", "please", "thanks", "thank", "sorry", "yes", "no",
        "not", "with", "for", "from", "about", "after", "before",
        "like", "love", "hate", "miss", "know", "think", "tell",
        "going", "through", "breakup", "broke", "broken", "heart",
        "lonely", "alone", "sad", "happy", "talk", "friend", "friends",
        "someone", "anyone", "everyone", "nobody", "something", "nothing",
        "here", "there", "this", "that", "what", "when", "where", "who",
        "how", "why", "much", "many", "some", "all", "any", "each",
        "every", "more", "most", "other", "new", "old", "good", "bad",
        "make", "made", "get", "got", "has", "her", "him", "his",
        "our", "your", "their", "been", "being", "do", "did", "does",
        "one", "two", "day", "days", "time", "life", "back", "now",
        "still", "never", "always", "too", "also", "only", "even",
        "long", "ago", "since", "year", "years", "month", "months",
        "last", "first", "over", "out", "up", "down", "off", "on"
    )

    private fun extractName(text: String): String? {
        val words = text.split("\\s+".toRegex())
        for (word in words) {
            val cleaned = word.replace(Regex("[^a-zA-Z]"), "")
            if (cleaned.length >= 2
                && cleaned[0].isUpperCase()
                && cleaned.substring(1).all { it.isLowerCase() }
                && cleaned.lowercase() !in commonWords
            ) {
                return cleaned
            }
        }
        return null
    }

    // -- Data Loading --

    private fun loadPeople() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return

        femaleUsersViewModel.femaleUsersResponseLiveData.observe(this, Observer { response ->
            if (response?.success == true && !response.data.isNullOrEmpty()) {
                loadedPeople = response.data.take(10)
                peopleLoaded = true
            }
        })

        femaleUsersViewModel.femaleUsersErrorLiveData.observe(this, Observer {
            // Silently handle - conversation will proceed without people cards if needed
        })

        femaleUsersViewModel.getFemaleUsers(userId)
    }

    private fun loadCoinPackagesForAutoPay() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        walletViewModel.coinsLiveData.observe(this, Observer { response ->
            if (coinListLoaded) return@Observer
            if (response?.success != true || response.data.isNullOrEmpty()) return@Observer
            val cheapest = response.data.minByOrNull { it.price } ?: response.data[0]
            cachedAutoPayCoinId = cheapest.id.toString()
            cachedAutoPayAmount = cheapest.price.toString()
            coinListLoaded = true
        })
        walletViewModel.getCoins(userId)
    }

    // -- Navigation --

    private fun navigateToWallet() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
        intent.putExtra(DConstants.LANGUAGE, getIntent().getStringExtra(DConstants.LANGUAGE))
        if (cachedAutoPayCoinId != null && cachedAutoPayAmount != null) {
            intent.putExtra(DConstants.AUTO_PAY_COIN_ID, cachedAutoPayCoinId)
            intent.putExtra(DConstants.AUTO_PAY_AMOUNT, cachedAutoPayAmount)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
        intent.putExtra(DConstants.LANGUAGE, getIntent().getStringExtra(DConstants.LANGUAGE))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
