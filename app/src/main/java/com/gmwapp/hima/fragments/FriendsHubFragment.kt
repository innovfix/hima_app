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
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.databinding.FragmentFriendsHubBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

/**
 * Male bottom-nav "Friends" destination (Friends-Gated Chat; replaces the standalone
 * "Favourite" page). Four underline tabs (Variant A):
 *  - Friends   -> [FriendsTabFragment.TYPE_CHAT_FRIENDS]
 *  - Requests  -> [FriendsTabFragment.TYPE_THEIR_REQUESTS] (Accept/Reject)
 *  - Sent      -> [FriendsTabFragment.TYPE_MY_REQUESTS]
 *  - Favourites-> the existing starred-creators list ([FavouriteFragment] embedded).
 */
@AndroidEntryPoint
class FriendsHubFragment : Fragment(), Refreshable, NetworkRetryable {

    @Inject
    lateinit var apiManager: ApiManager

    private var _binding: FragmentFriendsHubBinding? = null
    private val binding get() = _binding!!

    private var receivedCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val baseMarginTop = (binding.friendsHubHeader.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.friendsHubHeader) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
            mlp.topMargin = baseMarginTop + top
            v.layoutParams = mlp
            insets
        }
        ViewCompat.requestApplyInsets(binding.friendsHubHeader)

        binding.vpFriendsHub.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 4
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_CHAT_FRIENDS)
                1 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_THEIR_REQUESTS)
                2 -> FriendsTabFragment.newInstance(FriendsTabFragment.TYPE_MY_REQUESTS)
                else -> FavouriteFragment.newInstance(embedded = true)
            }
        }
        TabLayoutMediator(binding.tabsFriendsHub, binding.vpFriendsHub) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.chat_tab_friends)
                1 -> getString(R.string.chat_tab_requests)
                2 -> getString(R.string.chat_tab_sent)
                else -> getString(R.string.favourite)
            }
        }.attach()

        loadCounts()
    }

    override fun onResume() {
        super.onResume()
        loadCounts()
        applyPendingSubTab()
    }

    /**
     * If a friend-request notification deep-link requested a specific sub-tab,
     * jump to it (0=Friends, 1=Requests, 2=Sent). No-op when none requested.
     */
    fun applyPendingSubTab() {
        if (_binding == null) return
        val sub = (activity as? com.gmwapp.hima.activities.MainActivity)?.consumePendingFriendsSubTab() ?: -1
        if (sub in 0..2) binding.vpFriendsHub.setCurrentItem(sub, false)
    }

    /** Called by child FriendsTabFragment after accept/reject/remove to sync badges. */
    fun refreshCounts() = loadCounts()

    /** Refresh the Requests tab count badge from the friend-tabs counts endpoint. */
    private fun loadCounts() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        if (userId == 0) return
        apiManager.getFriendTabsCounts(userId, object : NetworkCallback<FriendTabsCountsResponse> {
            override fun onResponse(
                call: Call<FriendTabsCountsResponse>,
                response: Response<FriendTabsCountsResponse>
            ) {
                if (!isAdded || _binding == null) return
                val d = response.body()?.data
                if (response.isSuccessful && response.body()?.success == true && d != null) {
                    receivedCount = d.received_requests_count
                    // Append " (n)" to each tab when its count > 0; otherwise show the
                    // plain label (no "(0)"). Mirrors the male FriendsListActivity badges.
                    fun labelWithCount(resId: Int, count: Int): String {
                        val base = getString(resId)
                        return if (count > 0) "$base ($count)" else base
                    }
                    binding.tabsFriendsHub.getTabAt(0)?.text =
                        labelWithCount(R.string.chat_tab_friends, d.friends_count)
                    binding.tabsFriendsHub.getTabAt(1)?.text =
                        labelWithCount(R.string.chat_tab_requests, d.received_requests_count)
                    binding.tabsFriendsHub.getTabAt(2)?.text =
                        labelWithCount(R.string.chat_tab_sent, d.my_requests_count)
                    binding.tabsFriendsHub.getTabAt(3)?.text =
                        labelWithCount(R.string.favourite, d.favourites_count)
                    // Mirror pending requests on the bottom-nav "Friends" icon too.
                    (activity as? com.gmwapp.hima.activities.MainActivity)?.setFriendsRequestCount(receivedCount)
                }
            }

            override fun onFailure(call: Call<FriendTabsCountsResponse>, t: Throwable) {}
            override fun onNoNetwork() {}
        })
    }

    private fun currentChild(): Fragment? =
        childFragmentManager.findFragmentByTag("f" + binding.vpFriendsHub.currentItem)

    /** Bottom-nav re-tap: refresh counts and forward to the visible tab if it supports it. */
    override fun refresh() {
        if (_binding == null) return
        loadCounts()
        (currentChild() as? Refreshable)?.refresh()
    }

    override fun onNetworkRetry() {
        (currentChild() as? NetworkRetryable)?.onNetworkRetry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
