package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityChatListBinding
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.retrofit.ApiManager
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        setupUserId()
        setupClickListeners()
        setupSearchListener()
        setupViewPager()
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
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        // ✅ SIMPLE: Set status bar icons to LIGHT (white) - works on all devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
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

