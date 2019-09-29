package com.pascaldornfeld.gsdble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.connection.GsdbleDevice
import com.pascaldornfeld.gsdble.overview.components.CharaConfigManager
import com.pascaldornfeld.gsdble.overview.components.CharaDataManager
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData

class DeviceFragment : Fragment(), ReadDeviceIfc {
    /**
     * shortcut to get a casted child fragment (findFragmentById)
     * kotlin-android-extensions only supports findViewById
     */
    private fun <T : Fragment> fId(@IdRes id: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return this.childFragmentManager.findFragmentById(id) as? T
    }

    private var removableDeviceActivity: RemovableDeviceActivity? = null
    lateinit var device: BluetoothDevice
        private set
    private var charaDataManager: CharaDataManager? = null
    private var charaConfigManager: CharaConfigManager? = null
    private var writeDeviceIfc: WriteDeviceIfc? = null

    interface RemovableDeviceActivity {
        fun removeDeviceFragment(device: BluetoothDevice)
    }

    // lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)
        writeDeviceIfc = GsdbleDevice(context, this)
        if (context is RemovableDeviceActivity) removableDeviceActivity = context
        else Log.w(TAG, "Activity is not a RemovableDeviceActivity")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = arguments!!.getParcelable(DEVICE)!!
        writeDeviceIfc?.doConnect(device)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.device_fragment, container, false)
        charaDataManager = CharaDataManager(
            fId(R.id.vGraphAccel)!!,
            fId(R.id.vGraphGyro)!!,
            fId(R.id.vGraphTime)!!,
            fId(R.id.vGraphTimeDeviation)!!,
            fId(R.id.vGraphDataRate)!!,
            fId(R.id.vGraphDataRateAverage)!!,
            fId(R.id.vGraphDataRateDeviation)!!
        )
        charaConfigManager =
            CharaConfigManager(
                { writeDeviceIfc.takeIf { it != null && it.isReadyToBeWritten() } },
                fId(R.id.vConfigOdr)!!,
                fId(R.id.vConfigPause)!!,
                fId(R.id.vConfigIntv)!!,
                fId(R.id.vConfigMtu)!!
            ) { charaDataManager?.resetGraphFragments() }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        charaConfigManager = null
        charaDataManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        writeDeviceIfc?.doDisconnect()
    }

    // ReadDeviceIfc

    override fun afterDisconnect() {
        writeDeviceIfc = null
        val removeInterface = removableDeviceActivity
        if (removeInterface != null) removeInterface.removeDeviceFragment(device)
        else Log.w(TAG, "Activity is not a RemovableDeviceActivity")
    }

    override fun readImuData(imuData: ImuData) {
        charaDataManager?.onNewData(imuData)
    }

    override fun readImuConfig(imuConfig: ImuConfig) {
        charaConfigManager?.onNewConfig(imuConfig)
    }

    override fun readMtu(mtu: Int) {
        charaConfigManager?.onNewMtu(mtu)
    }

    override fun readConnectionSpeed(interval: Int, latency: Int, timeout: Int) {
        charaConfigManager?.onNewInterval(interval)
    }

    // object-related

    override fun toString(): String = "DeviceFragment(device=$device)"

    companion object {
        private const val DEVICE = "device"
        private val TAG = DeviceFragment::class.java.simpleName.filter { it.isUpperCase() }

        @JvmStatic
        fun newInstance(device: BluetoothDevice) =
            DeviceFragment().apply {
                arguments = Bundle().apply { putParcelable(DEVICE, device) }
            }
    }
}
