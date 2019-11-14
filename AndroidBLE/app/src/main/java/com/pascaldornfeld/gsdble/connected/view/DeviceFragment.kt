package com.pascaldornfeld.gsdble.connected.view

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.connected.DeviceViewModel
import com.pascaldornfeld.gsdble.connected.hardware_library.WriteToDeviceIfc
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.view.subfragments.*
import kotlinx.android.synthetic.main.device_fragment.view.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong


class DeviceFragment : Fragment() {
    /**
     * shortcut to get a casted child fragment (findFragmentById)
     * as kotlin-android-extensions only supports findViewById
     */
    private fun <T : Fragment> fId(@IdRes id: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return try {
            this.childFragmentManager.findFragmentById(id) as? T
        } catch (ise: IllegalStateException) {
            null
        }
    }

    private var removableDeviceActivity: RemovableDeviceActivity? = null
    private var writeToDeviceIfc: WriteToDeviceIfc? = null

    private val lastConfig = AtomicReference<ImuConfig?>()
    private lateinit var viewModel: DeviceViewModel

    interface RemovableDeviceActivity {
        fun removeDeviceFragment(device: BluetoothDevice)
    }

    fun device() = requireArguments().getParcelable<BluetoothDevice>(DEVICE)!!

    // lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = DeviceViewModel.forDeviceFragment(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is RemovableDeviceActivity) removableDeviceActivity = context
        else Log.w(TAG, "Activity is not a RemovableDeviceActivity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.device_fragment, container, false)
        viewModel.disconnected.observe(viewLifecycleOwner, Observer<Boolean?> { maybe ->
            maybe?.let {
                if (it) {
                    writeToDeviceIfc = null
                    removableDeviceActivity?.removeDeviceFragment(device())
                        ?: Log.w(TAG, "Activity is not a RemovableDeviceActivity")
                }
            }
        })
        view.vDeviceAddress.text = device().address
        viewModel.dataDataRateAvg.observe(
            viewLifecycleOwner,
            Observer<Double?> { t ->
                view.vGraphDataRateAverage.text = t?.roundToLong()?.toString() ?: "-"
            })
        // datas
        view.vCheckEnable.apply {
            isChecked = false
            val accel = fId<SensorGraphFragment>(R.id.vGraphAccel)!!.also { vGraphAccel ->
                vGraphAccel.setTitle("Accelerometer")
                viewModel.dataAccel.getData().observe(
                    vGraphAccel.viewLifecycleOwner,
                    Observer<List<Pair<Long, Triple<Short, Short, Short>>>?> { maybeList ->
                        maybeList?.let { list -> vGraphAccel.updateData(list) }
                    })
            }
            val gyro = fId<SensorGraphFragment>(R.id.vGraphGyro)!!.also { vGraphGyro ->
                vGraphGyro.setTitle("Gyroscope")
                viewModel.dataGyro.getData().observe(
                    vGraphGyro.viewLifecycleOwner,
                    Observer<List<Pair<Long, Triple<Short, Short, Short>>>?> { maybeList ->
                        maybeList?.let { list -> vGraphGyro.updateData(list) }
                    })
            }
            setOnCheckedChangeListener { _, isChecked ->
                accel.showGraph(isChecked)
                gyro.showGraph(isChecked)
            }
        }
        // mtu and interval config
        fId<IntervalFragment>(R.id.vConfigIntv)!!.also { vConfigIntv ->
            vConfigIntv.setTitle("Interval Time")
            vConfigIntv.functionToApply = { writeToDeviceIfc?.writeConnectionPriority(it) }
            viewModel.dataInterval.observe(
                vConfigIntv.viewLifecycleOwner,
                Observer<Int?> { maybeIntv ->
                    maybeIntv?.let { intv -> vConfigIntv.setNewData(intv) }
                })
        }
        fId<MtuFragment>(R.id.vConfigMtu)!!.also { vConfigMtu ->
            vConfigMtu.setTitle("MTU")
            vConfigMtu.functionToApply = { writeToDeviceIfc?.writeMtu(it) }
            viewModel.dataMtu.observe(
                vConfigMtu.viewLifecycleOwner,
                Observer<Int?> { maybeMtu ->
                    maybeMtu?.let { mtu -> vConfigMtu.setNewData(mtu) }
                })
        }

        // imu config
        val vConfigOdr = fId<OdrFragment>(R.id.vConfigOdr)!!.apply {
            setTitle("ODR")
            functionToApply = { newOdr ->
                val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(odrIndex = newOdr) }
                if (newConfig != null) writeToDeviceIfc?.writeImuConfig(newConfig)
            }
        }
        val vConfigPause = fId<PauseFragment>(R.id.vConfigPause)!!.apply {
            setTitle("Pause")
            functionToApply = { newPaused ->
                val newConfig = lastConfig.updateAndGet { obj -> obj?.copy(paused = newPaused) }
                if (newConfig != null) writeToDeviceIfc?.writeImuConfig(newConfig)
            }
        }
        viewModel.dataImuConfig.observe(
            viewLifecycleOwner,
            Observer<ImuConfig?>
            { maybeConfig ->
                maybeConfig?.let { config ->
                    lastConfig.set(config)
                    vConfigOdr.setNewData(config.odrIndex)
                    vConfigPause.setNewData(config.paused)

                    viewModel.dataAccel.clearData()
                    viewModel.dataGyro.clearData()
                    viewModel.dataDataRate.clearData()
                }
            })
        // disconnect
        view.vDisconnectButton.setOnClickListener { writeToDeviceIfc?.doDisconnect() }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        writeToDeviceIfc?.doDisconnect()
    }

// object-related

    override fun toString(): String = "DeviceFragment(device=${device()})"

    companion object {
        private val TAG = DeviceFragment::class.java.simpleName.filter { it.isUpperCase() }
        const val DEVICE = "BluetoothDevice"

        fun newInstance(device: BluetoothDevice): DeviceFragment =
            DeviceFragment().apply {
                arguments = Bundle().apply { putParcelable(DEVICE, device) }
            }
    }


    fun setWriteToDeviceIfc(writeToDeviceIfc: WriteToDeviceIfc) {
        this.writeToDeviceIfc = writeToDeviceIfc
    }
}