package com.gmwapp.hima.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.databinding.ActivityFriendsListBinding
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension
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
class FriendsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsListBinding

    @Inject
    lateinit var apiManager: ApiManager

    private val friendStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadTabCounts()
        }
    }

    override fun onResume() {
        super.onResume()
        loadTabCounts()
        val filter = IntentFilter(OneSignalNotificationServiceExtension.ACTION_FRIEND_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(friendStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(friendStatusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(friendStatusReceiver) } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        setupToolbar()
        setupViewPager()
        loadTabCounts()  // Load counts immediately
        onBackPressedBtn()
        applyLightSystemBars()

        // Light status bar background → dark status-bar icons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.cvBack.setOnClickListener {
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

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

                if (messageCameWhenIsAlive == 0) {
                    val intent = Intent(this@FriendsListActivity, MainActivity::class.java).apply {
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

    private fun setupViewPager() {
        val adapter = FriendsPagerAdapter(this)
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

    private fun loadTabCounts() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0

        if (userId == 0) {
            Log.e("FriendsListActivity", "❌ User ID is invalid!")
            return
        }

        Log.d("FriendsListActivity", "📊 Loading tab counts for user: $userId")

        apiManager.getFriendTabsCounts(userId, object : NetworkCallback<FriendTabsCountsResponse> {
            override fun onResponse(
                call: Call<FriendTabsCountsResponse>,
                response: Response<FriendTabsCountsResponse>
            ) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        Log.d("FriendsListActivity", "✅ Counts loaded: Chat=${data.chats_count}, Friends=${data.friends_count}, My=${data.my_requests_count}, Received=${data.received_requests_count}")
                        updateTabBadges(
                            data.chats_count,
                            data.friends_count,
                            data.my_requests_count,
                            data.received_requests_count
                        )
                    }
                } else {
                    Log.e("FriendsListActivity", "❌ Failed to load counts: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<FriendTabsCountsResponse>, t: Throwable) {
                Log.e("FriendsListActivity", "❌ Error loading counts: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.e("FriendsListActivity", "❌ No network connection")
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

    private inner class FriendsPagerAdapter(activity: FragmentActivity) :
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
}

