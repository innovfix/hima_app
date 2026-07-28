package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityChatListBinding
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import com.gmwapp.hima.utils.applyLightSystemBars

@AndroidEntryPoint
class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding

    @Inject
    lateinit var apiManager: ApiManager

    private var myUserId: Int = 0

    // Debouncing for search
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val SEARCH_DEBOUNCE_DELAY = 300L // 300ms delay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots / screen recording on the chat list (message previews).
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        initializeViews()
        setupUserId()
        setupClickListeners()
        setupSearchListener()
        setupViewPager()
        loadTabCounts()  // Load counts immediately
        onBackPressedBtn()
    }

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

                if (messageCameWhenIsAlive == 0) {
                    val intent = Intent(this@ChatListActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    finish()
                }
            }
        })
    }


    private fun initializeViews() {
        applyLightSystemBars()

        // ✅ SIMPLE: Set status bar icons to LIGHT (white) - works on all devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        // Match home-screen inset behavior so header stays below status bar on Android 15/16.
        val baseTopPadding = binding.appBarLayout.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                baseTopPadding + statusBarInset,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.appBarLayout)
    }

    private fun setupUserId() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id ?: 0

        if (myUserId == 0) {
            Log.e("ChatListActivity", "User ID is invalid!")
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

            if (messageCameWhenIsAlive == 0) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                finish()
            }
        }
    }

    private fun setupSearchListener() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Cancel previous search runnable
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                // Create new search runnable
                val searchQuery = s?.toString()?.trim() ?: ""
                searchRunnable = Runnable {
                    performSearch(searchQuery)
                }

                // Post delayed search with debouncing
                searchHandler.postDelayed(searchRunnable!!, SEARCH_DEBOUNCE_DELAY)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        Log.d("ChatListActivity", "🔍 Performing search: '$query'")
        val currentPosition = binding.viewPager.currentItem
        val currentFragment = supportFragmentManager.findFragmentByTag("f${currentPosition}")
        
        if (currentFragment is FriendsTabFragment) {
            currentFragment.performSearch(query)
        }
    }

    private fun setupViewPager() {
        val adapter = ChatPagerAdapter(this)
        binding.viewPager.adapter = adapter
        // Disable horizontal swiping between tabs — UI request, tabs are hidden
        // and the user should stay on the Chat tab.
        binding.viewPager.isUserInputEnabled = false

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Chat"
                1 -> "Friends"
                2 -> "My Requests"
                3 -> "Received"
                else -> ""
            }
        }.attach()

        val targetTab = intent.getIntExtra(
            "target_tab",
            FriendsTabFragment.TYPE_CHAT // default
        )

        val targetIndex = when (targetTab) {
            FriendsTabFragment.TYPE_FRIENDS -> 1
            FriendsTabFragment.TYPE_MY_REQUESTS -> 2
            FriendsTabFragment.TYPE_THEIR_REQUESTS -> 3
            else -> 0
        }
        binding.viewPager.setCurrentItem(targetIndex, false)
    }

    private fun loadTabCounts() {
        if (myUserId == 0) {
            Log.e("ChatListActivity", "❌ User ID is invalid!")
            return
        }

        Log.d("ChatListActivity", "📊 Loading tab counts for user: $myUserId")

        apiManager.getFriendTabsCounts(myUserId, object : NetworkCallback<FriendTabsCountsResponse> {
            override fun onResponse(
                call: Call<FriendTabsCountsResponse>,
                response: Response<FriendTabsCountsResponse>
            ) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        Log.d("ChatListActivity", "✅ Counts loaded: Chat=${data.chats_count}, Friends=${data.friends_count}, My=${data.my_requests_count}, Received=${data.received_requests_count}")
                        updateTabBadges(
                            data.chats_count,
                            data.friends_count,
                            data.my_requests_count,
                            data.received_requests_count
                        )
                    }
                } else {
                    Log.e("ChatListActivity", "❌ Failed to load counts: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<FriendTabsCountsResponse>, t: Throwable) {
                Log.e("ChatListActivity", "❌ Error loading counts: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.e("ChatListActivity", "❌ No network connection")
            }
        })
    }

    /**
     * Public method to refresh tab counts - called from fragments after accepting/rejecting requests
     */
    fun refreshTabCounts() {
        loadTabCounts()
    }

    private fun updateTabBadges(
        chatCount: Int,
        friendsCount: Int,
        myRequestsCount: Int,
        receivedRequestsCount: Int
    ) {
        runOnUiThread {
            binding.tabLayout.getTabAt(0)?.text = if (chatCount > 0) "Chat ($chatCount)" else "Chat"
            binding.tabLayout.getTabAt(1)?.text = if (friendsCount > 0) "Friends ($friendsCount)" else "Friends"
            binding.tabLayout.getTabAt(2)?.text = if (myRequestsCount > 0) "My Requests ($myRequestsCount)" else "My Requests"
            binding.tabLayout.getTabAt(3)?.text = if (receivedRequestsCount > 0) "Received ($receivedRequestsCount)" else "Received"
        }
    }

    private inner class ChatPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_CHAT)
                1 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_FRIENDS)
                2 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_MY_REQUESTS)
                3 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_THEIR_REQUESTS)
                else -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_CHAT)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up search handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }
}

