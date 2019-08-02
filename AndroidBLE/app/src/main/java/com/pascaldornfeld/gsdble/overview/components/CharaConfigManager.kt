package com.pascaldornfeld.gsdble.overview.components

import android.bluetooth.BluetoothDevice
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.fragments.OdrFragment
import com.pascaldornfeld.gsdble.overview.fragments.PauseFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.callback.SuccessCallback
import java.util.concurrent.atomic.AtomicReference

class CharaConfigManager(
    fragmentById: ((Int) -> Fragment?),
    private val lastConfig: AtomicReference<ImuConfig?>,
    private val resetGraphFragments: (() -> Unit)
) : MyBleManager.MyDataReceivedCallback<ImuConfig>, FailCallback {
    private val vConfigOdr by lazy { fragmentById(R.id.vConfigOdr) as OdrFragment }
    private val vConfigPause by lazy { fragmentById(R.id.vConfigPause) as PauseFragment }

    fun disableOdrAndPauseConfig() {
        vConfigOdr.functionToApply = null
        vConfigPause.functionToApply = null
        lastConfig.set(null)
    }

    fun buildSuccessCallback(myBleManager: MyBleManager) = SuccessCallback {
        vConfigPause.setTitle("Pause")
        vConfigPause.functionToApply = { newPaused ->
            // write new config
            val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(paused = newPaused) }
            if (newConfig != null) myBleManager.writeNewConfig(newConfig)
        }

        vConfigOdr.setTitle("ODR")
        vConfigOdr.functionToApply = { newOdr ->
            // write new config
            val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(odr = newOdr) }
            if (newConfig != null) myBleManager.writeNewConfig(newConfig)
        }
    }

    override fun onNewData(data: ImuConfig) {
        lastConfig.set(data)
        vConfigOdr.setNewData(data.odr)
        vConfigPause.setNewData(data.paused)
        resetGraphFragments()
    }

    override fun onRequestFailed(device: BluetoothDevice, status: Int) {
        vConfigOdr.setTitle("Failed Notify Config $status")
    }
}