package com.pascaldornfeld.gsdble

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

/**
 * Adapter for fragment viewHolder.
 * There is one main-fragment and 0..n optional fragments.
 *
 * @param fragmentManager the support fragment manager
 * @param mainFragment The main-fragment allows searching for devices.
 * @param optionalFragments There is one optional fragment for each connected device.
 */
class MyFragmentAdapter(
    fragmentManager: FragmentManager,
    private val mainFragment: Fragment,
    private val optionalFragments: MutableList<Fragment>
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment =
        if (position == 0) mainFragment else optionalFragments[position - 1]

    override fun getCount(): Int = 1 + optionalFragments.size
}