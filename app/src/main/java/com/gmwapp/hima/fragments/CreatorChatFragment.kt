package com.gmwapp.hima.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.databinding.FragmentCreatorChatBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

/**
 * Female creator bottom-nav "Chat" destination: Friends / General sub-tabs over
 * [FriendsTabFragment] with [FriendsTabFragment.TYPE_CHAT_FRIENDS] and
 * [FriendsTabFragment.TYPE_CHAT_GENERAL].
 */
@AndroidEntryPoint
class CreatorChatFragment : Fragment() {

    @Inject
    lateinit var apiManager: ApiManager

    private var _binding: FragmentCreatorChatBinding? = null
    private val binding get() = _binding!!

    private var friendsUnread = 0
    private var generalUnread = 0

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
            val basePaddingTop = binding.tabsCreatorChat.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(binding.tabsCreatorChat) { v, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                v.setPadding(
                    v.paddingLeft,
                    basePaddingTop + statusBarInset,
                    v.paddingRight,
                    v.paddingBottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(binding.tabsCreatorChat)
        }

        binding.vpCreatorChat.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_CHAT_FRIENDS)
                else -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_CHAT_GENERAL)
            }
        }
        TabLayoutMediator(binding.tabsCreatorChat, binding.vpCreatorChat) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.chat_tab_friends)
                else -> getString(R.string.chat_tab_general)
            }
        }.attach()

        loadTabUnreadCounts()
    }

    override fun onResume() {
        super.onResume()
        loadTabUnreadCounts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

        apiManager.getMyChatGeneral(userId, null, 100, 0, object : NetworkCallback<MyChatResponse> {
            override fun onResponse(call: Call<MyChatResponse>, response: Response<MyChatResponse>) {
                if (!isAdded) return
                generalUnread = if (response.isSuccessful && response.body()?.success == true) {
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
    }

    private fun refreshTabTitles() {
        if (_binding == null) return
        binding.tabsCreatorChat.getTabAt(0)?.text = formatTitle(R.string.chat_tab_friends, friendsUnread)
        binding.tabsCreatorChat.getTabAt(1)?.text = formatTitle(R.string.chat_tab_general, generalUnread)
        (activity as? MainActivity)?.updateChatUnreadCountBadge(friendsUnread, generalUnread)
    }

    private fun formatTitle(@StringRes resId: Int, count: Int): String {
        val base = getString(resId)
        return if (count > 0) "$base ($count)" else base
    }
}
