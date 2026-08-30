package com.floatoverlay.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.floatoverlay.app.ui.game.GameLauncherFragment
import com.floatoverlay.app.ui.logs.LogsFragment
import com.floatoverlay.app.ui.saved.SavedFragment
import com.floatoverlay.app.ui.overlay.OverlayListFragment
import com.floatoverlay.app.ui.stream.StreamFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OverlayListFragment()
            1 -> SavedFragment()
            2 -> LogsFragment()
            3 -> GameLauncherFragment()
            4 -> StreamFragment()
            else -> OverlayListFragment()
        }
    }
}
