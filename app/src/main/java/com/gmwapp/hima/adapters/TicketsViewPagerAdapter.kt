package com.gmwapp.hima.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gmwapp.hima.fragments.TicketsTabFragment

class TicketsViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableListOf<TicketsTabFragment>()

    init {
        // 0 = Active tickets
        fragments.add(TicketsTabFragment.newInstance(0))
        // 1 = Resolved tickets
        fragments.add(TicketsTabFragment.newInstance(1))
    }

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

    fun getFragment(position: Int): TicketsTabFragment? {
        return if (position in fragments.indices) fragments[position] else null
    }
}







