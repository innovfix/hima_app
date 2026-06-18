package com.gmwapp.hima.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.launch
import com.google.android.material.tabs.TabLayoutMediator
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.databinding.FragmentCreatorChatBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

/**
 * Female creator bottom-nav "Chat" destination (Friends-Gated Chat). Segmented-pill
 * sub-tabs over [FriendsTabFragment]: Friends ([FriendsTabFragment.TYPE_CHAT_FRIENDS]),
 * Requests received ([FriendsTabFragment.TYPE_THEIR_REQUESTS]) and Sent
 * ([FriendsTabFragment.TYPE_MY_REQUESTS]).
 */
@AndroidEntryPoint
class CreatorChatFragment : Fragment() {

    @Inject
    lateinit var apiManager: ApiManager

    private var _binding: FragmentCreatorChatBinding? = null
    private val binding get() = _binding!!

    private var friendsUnread = 0
    private var receivedCount = 0
    private var sentCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatorChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (requireActivity() is MainActivity) {
            // Segmented-pill tabs carry a rounded background, so push them below the
            // status bar with top MARGIN (not padding) — padding would extend the pill
            // background up into the status-bar area.
            val baseMarginTop = (binding.tabsCreatorChat.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            ViewCompat.setOnApplyWindowInsetsListener(binding.tabsCreatorChat) { v, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
                mlp.topMargin = baseMarginTop + statusBarInset
                v.layoutParams = mlp
                insets
            }
            ViewCompat.requestApplyInsets(binding.tabsCreatorChat)
        }

        binding.vpCreatorChat.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_CHAT_FRIENDS)
                1 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_THEIR_REQUESTS)
                else -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_MY_REQUESTS)
            }
        }
        TabLayoutMediator(binding.tabsCreatorChat, binding.vpCreatorChat) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.chat_tab_friends)
                1 -> getString(R.string.chat_tab_requests)
                else -> getString(R.string.chat_tab_sent)
            }
        }.attach()

        loadTabUnreadCounts()
    }

    override fun onResume() {
        super.onResume()
        loadTabUnreadCounts()
        registerCreatorChatListRefreshReceiver()
        startCollectingSocketNewMessage()
    }

    override fun onPause() {
        super.onPause()
        unregisterCreatorChatListRefreshReceiver()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Re-fetches the unread totals on every list-refresh broadcast — `getMyChat*`
     * is the source of truth for sub-tab counts, and the broadcasts are rare
     * enough (per push) that calling the API instead of trying to bump in-memory
     * counters keeps the friends/general split consistent.
     */
    private var creatorChatListRefreshReceiver: android.content.BroadcastReceiver? = null
    private var creatorChatListRefreshReceiverRegistered: Boolean = false

    private fun registerCreatorChatListRefreshReceiver() {
        if (creatorChatListRefreshReceiverRegistered) return
        val ctx = context ?: return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (!isAdded || intent == null) return
                if (intent.action != com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH) return
                loadTabUnreadCounts()
            }
        }
        val filter = android.content.IntentFilter(
            com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(
                receiver,
                filter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter)
        }
        creatorChatListRefreshReceiver = receiver
        creatorChatListRefreshReceiverRegistered = true
    }

    private fun unregisterCreatorChatListRefreshReceiver() {
        if (!creatorChatListRefreshReceiverRegistered) return
        val ctx = context ?: return
        val receiver = creatorChatListRefreshReceiver ?: return
        runCatching { ctx.unregisterReceiver(receiver) }
        creatorChatListRefreshReceiver = null
        creatorChatListRefreshReceiverRegistered = false
    }

    /**
     * Optimistic in-memory unread bump on socket new_message so the tab title
     * updates instantly. The broadcast / next loadTabUnreadCounts call corrects
     * the totals if our optimistic count drifts (e.g. counted in wrong sub-tab).
     */
    private fun startCollectingSocketNewMessage() {
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                com.gmwapp.hima.socket.SocketManager.getInstance().newMessage.collect { msg ->
                    if (!isAdded) return@collect
                    val mySelfId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
                    val peerId = msg.fromUserId ?: return@collect
                    if (peerId == mySelfId) return@collect
                    // Skip if user has the thread open — the read flow handles it.
                    if (com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(context, peerId)) return@collect
                    // We don't know which sub-tab the peer belongs to here; the
                    // friends API call inside loadTabUnreadCounts is cheap (sums
                    // unreadCount across pages) so just refresh both totals.
                    loadTabUnreadCounts()
                }
            }
        }
    }

    private fun loadTabUnreadCounts() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        if (userId == 0) return

        apiManager.getMyChatFriends(userId, null, 100, 0, object : NetworkCallback<MyChatResponse> {
            override fun onResponse(call: Call<MyChatResponse>, response: Response<MyChatResponse>) {
                if (!isAdded) return
                friendsUnread = if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.chats?.sumOf { it.unreadCount } ?: 0
                } else {
                    0
                }
                refreshTabTitles()
            }

            override fun onFailure(call: Call<MyChatResponse>, t: Throwable) {
                if (!isAdded) return
            }

            override fun onNoNetwork() {
                if (!isAdded) return
            }
        })

        // Requests (received) + Sent counts come from the friend-tabs counts endpoint.
        apiManager.getFriendTabsCounts(userId, object : NetworkCallback<FriendTabsCountsResponse> {
            override fun onResponse(call: Call<FriendTabsCountsResponse>, response: Response<FriendTabsCountsResponse>) {
                if (!isAdded) return
                val d = response.body()?.data
                if (response.isSuccessful && response.body()?.success == true && d != null) {
                    receivedCount = d.received_requests_count
                    sentCount = d.my_requests_count
                }
                refreshTabTitles()
            }

            override fun onFailure(call: Call<FriendTabsCountsResponse>, t: Throwable) {
                if (!isAdded) return
            }

            override fun onNoNetwork() {
                if (!isAdded) return
            }
        })
    }

    private fun refreshTabTitles() {
        if (_binding == null) return
        binding.tabsCreatorChat.getTabAt(0)?.text = formatTitle(R.string.chat_tab_friends, friendsUnread)
        binding.tabsCreatorChat.getTabAt(1)?.text = formatTitle(R.string.chat_tab_requests, receivedCount)
        binding.tabsCreatorChat.getTabAt(2)?.text = formatTitle(R.string.chat_tab_sent, sentCount)
        // Unread messages drive the message badge; received-requests use the dedicated
        // setter so they aren't conflated with the general-bucket unread count.
        (activity as? MainActivity)?.updateChatUnreadCountBadge(friendsUnread, 0)
        (activity as? MainActivity)?.setChatRequestsCount(receivedCount)
    }

    private fun formatTitle(@StringRes resId: Int, count: Int): String {
        val base = getString(resId)
        return if (count > 0) "$base ($count)" else base
    }
}
