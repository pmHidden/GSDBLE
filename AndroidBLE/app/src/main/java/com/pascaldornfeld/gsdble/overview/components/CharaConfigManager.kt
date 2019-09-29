package com.pascaldornfeld.gsdble.overview.components

import com.pascaldornfeld.gsdble.WriteDeviceIfc
import com.pascaldornfeld.gsdble.overview.fragments.IntervalFragment
import com.pascaldornfeld.gsdble.overview.fragments.MtuFragment
import com.pascaldornfeld.gsdble.overview.fragments.OdrFragment
import com.pascaldornfeld.gsdble.overview.fragments.PauseFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import java.util.concurrent.atomic.AtomicReference

class CharaConfigManager(
    writeDeviceIfc: (() -> WriteDeviceIfc?),
    private val odrFragment: OdrFragment,
    private val pauseFragment: PauseFragment,
    private val intvFragment: IntervalFragment,
    private val mtuFragment: MtuFragment,
    private val resetGraphFragments: (() -> Unit)
) {
    init {
        odrFragment.apply {
            setTitle("ODR")
            functionToApply = { newOdr ->
                val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(odrIndex = newOdr) }
                if (newConfig != null) writeDeviceIfc()?.writeImuConfig(newConfig)
            }
        }
        pauseFragment.apply {
            setTitle("Pause")
            functionToApply = { newPaused ->
                val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(paused = newPaused) }
                if (newConfig != null) writeDeviceIfc()?.writeImuConfig(newConfig)
            }
        }
        intvFragment.apply {
            setTitle("Interval Time")
            functionToApply = { writeDeviceIfc()?.writeConnectionPriority(it) }
        }
        mtuFragment.apply {
            setTitle("MTU")
            functionToApply = { writeDeviceIfc()?.writeMtu(it) }
        }
    }

    private val lastConfig = AtomicReference<ImuConfig?>()

    fun onNewConfig(data: ImuConfig) {
        lastConfig.set(data)
        odrFragment.setNewData(data.odrIndex)
        pauseFragment.setNewData(data.paused)
        resetGraphFragments()
    }

    fun onNewInterval(priority: Int) = intvFragment.applyDataFromIntervalTime(priority)

    fun onNewMtu(mtu: Int) = mtuFragment.setNewData(mtu)
}