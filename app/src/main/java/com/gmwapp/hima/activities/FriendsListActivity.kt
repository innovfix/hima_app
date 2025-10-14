package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.databinding.ActivityFriendsListBinding
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FriendsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewPager()
        onBackPressedBtn()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {

            val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

            if (messageCameWhenIsAlive == 0) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                finish()
            }        }
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

