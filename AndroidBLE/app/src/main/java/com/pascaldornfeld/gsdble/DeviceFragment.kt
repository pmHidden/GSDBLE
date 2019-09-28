package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.connection.GsdbleDevice
import com.pascaldornfeld.gsdble.overview.components.CharaConfigManager
import com.pascaldornfeld.gsdble.overview.components.CharaDataManager
import com.pascaldornfeld.gsdble.overview.fragments.IntervalFragment
import com.pascaldornfeld.gsdble.overview.fragments.MtuFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData
import kotlinx.android.synthetic.main.device_fragment.*

class DeviceFragment : Fragment(), ReadDeviceIfc {
    interface RemovableDeviceActivity {
        fun removeDeviceFragment(device: BluetoothDevice)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is RemovableDeviceActivity) removableDeviceActivity = context
        else Log.w(TAG, "Activity is not a RemovableDeviceActivity")
    }

    override fun afterConnect() {
        (vConfigIntv as IntervalFragment).apply {
            setTitle("Interval Time")
            functionToApply = { writeDeviceIfc?.writeConnectionPriority(it) }
        }
        (vConfigMtu as MtuFragment).apply {
            setTitle("MTU")
            functionToApply = { writeDeviceIfc?.writeMtu(it) }
        }
    }

    override fun afterDisconnect() {
        val removeInterface = removableDeviceActivity
        if (removeInterface != null) removeInterface.removeDeviceFragment(device)
        else Log.w(TAG, "Activity is not a RemovableDeviceActivity")
    }

    override fun readImuData(imuData: ImuData) {
        charaDataManager?.onNewData(imuData)
    }

    override fun readImuConfig(imuConfig: ImuConfig) {
        charaConfigManager?.onNewData(imuConfig)
    }

    override fun readMtu(mtu: Int) {
        (vConfigMtu as MtuFragment).setNewData(mtu)
    }

    override fun readConnectionSpeed(interval: Int, latency: Int, timeout: Int) {
        // TODO damn...
    }

    private var removableDeviceActivity: RemovableDeviceActivity? = null
    lateinit var device: BluetoothDevice
        private set
    private var charaDataManager: CharaDataManager? = null
    private var charaConfigManager: CharaConfigManager? = null
    private var writeDeviceIfc: WriteDeviceIfc? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = arguments!!.getParcelable(DEVICE)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.device_fragment, container, false)
        writeDeviceIfc = GsdbleDevice(view.context, this)
        charaDataManager = CharaDataManager(this)
        charaConfigManager =
            CharaConfigManager(writeDeviceIfc, this) { charaDataManager?.resetGraphFragments() }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        writeDeviceIfc?.doDisconnect()
    }

    companion object {
        private const val DEVICE = "device"
        private val TAG = DeviceFragment::class.java.simpleName.filter { it.isUpperCase() }

        @JvmStatic
        fun newInstance(device: BluetoothDevice) =
            DeviceFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(DEVICE, device)
                }
            }
    }
}
