package com.pascaldornfeld.gsdble.overview.components

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.fragments.IntervalFragment
import com.pascaldornfeld.gsdble.overview.fragments.MtuFragment
import no.nordicsemi.android.ble.BleManagerCallbacks

class MyBleManagerCallbacks(
    private val myBleManager: MyBleManager,
    fragmentById: ((Int) -> Fragment?),
    private var unsetLastConfigAndOtherConfigFragments: (() -> Unit),
    private var onDisconnected: (() -> Unit),
    private var context: Context
) : BleManagerCallbacks {
    private val vConfigIntv: IntervalFragment by lazy { fragmentById(R.id.vConfigIntv) as IntervalFragment }
    private val vConfigMtu: MtuFragment by lazy { fragmentById(R.id.vConfigMtu) as MtuFragment }

    fun onNewMtu(mtu: Int) = vConfigMtu.setNewData(mtu)
    fun onNewInterval(interval: IntervalFragment.Interval) = vConfigIntv.setNewData(interval)

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        unsetLastConfigAndOtherConfigFragments()
        vConfigIntv.functionToApply = null
        vConfigMtu.functionToApply = null
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        onDisconnected()
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        myBleManager.errorRefresh()
        // this happens when device is still registered from a previous connection
        // 'stackoverflow' says that errorRefresh will clear the ble cache but it doesn't help
        // shouldClearCacheWhenDisconnected = true also doesn't help
        // so just wait until supervision timeout is over or restart bluetooth
        Toast.makeText(
            context,
            "Please restart bluetooth or wait about 30 sec",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        myBleManager.disconnectDevice()
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        vConfigIntv.setTitle("Interval Time")
        vConfigIntv.functionToApply = { myBleManager.writeNewInterval(it) }
        vConfigMtu.setTitle("MTU")
        vConfigMtu.functionToApply = { myBleManager.writeNewMtu(it) }
    }

    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        myBleManager.disconnectDevice()
    }

    override fun onBondingFailed(device: BluetoothDevice) {}
    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {}
    override fun onBondingRequired(device: BluetoothDevice) {}
    override fun onBonded(device: BluetoothDevice) {}
    override fun onDeviceConnected(device: BluetoothDevice) {}
    override fun onDeviceConnecting(device: BluetoothDevice) {}
}