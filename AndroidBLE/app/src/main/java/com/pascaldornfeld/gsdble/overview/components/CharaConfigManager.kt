package com.pascaldornfeld.gsdble.overview.components

import com.pascaldornfeld.gsdble.DeviceFragment
import com.pascaldornfeld.gsdble.WriteDeviceIfc
import com.pascaldornfeld.gsdble.overview.fragments.OdrFragment
import com.pascaldornfeld.gsdble.overview.fragments.PauseFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import kotlinx.android.synthetic.main.device_fragment.*
import java.util.concurrent.atomic.AtomicReference

class CharaConfigManager(
    writeDeviceIfc: WriteDeviceIfc?,
    private val deviceFragment: DeviceFragment,
    private val resetGraphFragments: (() -> Unit)
) {
    init {
        (deviceFragment.vConfigOdr as OdrFragment).apply {
            setTitle("ODR")
            functionToApply = { newOdr ->
                val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(odrIndex = newOdr) }
                if (newConfig != null) writeDeviceIfc?.writeImuConfig(newConfig)
            }
        }

        (deviceFragment.vConfigPause as PauseFragment).apply {
            setTitle("Pause")
            functionToApply = { newPaused ->
                val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(paused = newPaused) }
                if (newConfig != null) writeDeviceIfc?.writeImuConfig(newConfig)
            }
        }

    }

    private val lastConfig = AtomicReference<ImuConfig?>()

    fun onNewData(data: ImuConfig) {
        lastConfig.set(data)
        (deviceFragment.vConfigOdr as OdrFragment).setNewData(data.odrIndex)
        (deviceFragment.vConfigPause as PauseFragment).setNewData(data.paused)
        resetGraphFragments()
    }
}