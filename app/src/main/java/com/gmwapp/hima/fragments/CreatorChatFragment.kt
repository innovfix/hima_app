package com.gmwapp.hima.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.databinding.FragmentCreatorChatBinding

/**
 * Female creator bottom-nav "Chat" destination: Friends / General sub-tabs over
 * [FriendsTabFragment] with [FriendsTabFragment.TYPE_CHAT_FRIENDS] and
 * [FriendsTabFragment.TYPE_CHAT_GENERAL].
 */
class CreatorChatFragment : Fragment() {

    private var _binding: FragmentCreatorChatBinding? = null
    private val binding get() = _binding!!

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
