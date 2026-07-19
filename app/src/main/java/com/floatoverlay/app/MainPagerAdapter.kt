package com.floatoverlay.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.floatoverlay.app.ui.game.GameLauncherFragment
import com.floatoverlay.app.ui.logs.LogsFragment
import com.floatoverlay.app.ui.overlay.OverlayListFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OverlayListFragment()
            1 -> LogsFragment()
            2 -> GameLauncherFragment()
            else -> OverlayListFragment()
        }
    }
}
