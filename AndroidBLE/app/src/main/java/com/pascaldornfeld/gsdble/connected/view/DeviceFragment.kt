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
import androidx.lifecycle.ViewModelProviders
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.connected.GsdbleViewModel
import com.pascaldornfeld.gsdble.connected.gsdble_library.WriteToDeviceIfc
import com.pascaldornfeld.gsdble.connected.gsdble_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.view.subfragments.*
import kotlinx.android.synthetic.main.device_fragment.view.*
import java.util.concurrent.atomic.AtomicReference


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
    private lateinit var viewModel: GsdbleViewModel

    interface RemovableDeviceActivity {
        fun removeDeviceFragment(device: BluetoothDevice)
    }

    fun device() = requireArguments().getParcelable<BluetoothDevice>(DEVICE)!!

    // lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(GsdbleViewModel::class.java)
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
        // datas
        fId<SensorGraphFragment>(R.id.vGraphAccel)!!.also { vGraphAccel ->
            vGraphAccel.setTitle("Accelerometer")
            viewModel.dataAccel.getData().observe(
                vGraphAccel.viewLifecycleOwner,
                Observer<List<Pair<Long, Triple<Short, Short, Short>>>?> { maybeList ->
                    maybeList?.let { list -> vGraphAccel.updateData(list) }
                })
        }
        fId<SensorGraphFragment>(R.id.vGraphGyro)!!.also { vGraphGyro ->
            vGraphGyro.setTitle("Gyroscope")
            viewModel.dataGyro.getData().observe(
                vGraphGyro.viewLifecycleOwner,
                Observer<List<Pair<Long, Triple<Short, Short, Short>>>?> { maybeList ->
                    maybeList?.let { list -> vGraphGyro.updateData(list) }
                })
        }
        fId<LongTimeGraphFragment>(R.id.vGraphTime)!!.also { vGraphTime ->
            vGraphTime.setTitle("Packet Delivery Time")
            vGraphTime.dataDescription = "data time vs delivery time"
            viewModel.dataTime.getData().observe(
                vGraphTime.viewLifecycleOwner,
                Observer<List<Pair<Long, Long>>?> { maybeList ->
                    maybeList?.let { list -> vGraphTime.updateData(list) }
                })
        }
        fId<DoubleTimeGraphFragment>(R.id.vGraphTimeDeviation)!!.also { vGraphTimeDeviation ->
            vGraphTimeDeviation.setTitle("Time Deviation")
            vGraphTimeDeviation.dataDescription = "deviation"
            viewModel.dataTimeDev.getData().observe(
                vGraphTimeDeviation.viewLifecycleOwner,
                Observer<List<Pair<Long, Double>>?> { maybeList ->
                    maybeList?.let { list -> vGraphTimeDeviation.updateData(list) }
                })
        }
        fId<LongTimeGraphFragment>(R.id.vGraphDataRate)!!.also { vGraphDataRate ->
            vGraphDataRate.setTitle("Data Rate")
            vGraphDataRate.dataDescription = "data time vs data rate"
            viewModel.dataDataRate.getData().observe(
                vGraphDataRate.viewLifecycleOwner,
                Observer<List<Pair<Long, Long>>?> { maybeList ->
                    maybeList?.let { list -> vGraphDataRate.updateData(list) }
                })
        }
        fId<DoubleTimeGraphFragment>(R.id.vGraphDataRateAverage)!!.also { vGraphDataRateAverage ->
            vGraphDataRateAverage.setTitle("Data Rate Average")
            vGraphDataRateAverage.dataDescription = "average"
            viewModel.dataDataRateAvg.getData().observe(
                vGraphDataRateAverage.viewLifecycleOwner,
                Observer<List<Pair<Long, Double>>?> { maybeList ->
                    maybeList?.let { list -> vGraphDataRateAverage.updateData(list) }
                })
        }
        fId<DoubleTimeGraphFragment>(R.id.vGraphDataRateDeviation)!!.also { vGraphDataRateDeviation ->
            vGraphDataRateDeviation.setTitle("Data Rate Deviation")
            vGraphDataRateDeviation.dataDescription = "deviation"
            viewModel.dataDataRateDev.getData().observe(
                vGraphDataRateDeviation.viewLifecycleOwner,
                Observer<List<Pair<Long, Double>>?> { maybeList ->
                    maybeList?.let { list -> vGraphDataRateDeviation.updateData(list) }
                })
        }

        // mtu and interval config
        fId<IntervalFragment>(R.id.vConfigIntv)!!.also { vConfigIntv ->
            vConfigIntv.setTitle("Interval Time")
            vConfigIntv.functionToApply = { writeToDeviceIfc?.writeConnectionPriority(it) }
            viewModel.dataIntervalSeconds.observe(
                vConfigIntv.viewLifecycleOwner,
                Observer<Int?> { maybeIntv ->
                    maybeIntv?.let { intv -> vConfigIntv.applyDataFromIntervalTime(intv) }
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
            Observer<ImuConfig?> { maybeConfig ->
                maybeConfig?.let { config ->
                    lastConfig.set(config)
                    vConfigOdr.setNewData(config.odrIndex)
                    vConfigPause.setNewData(config.paused)

                    viewModel.dataAccel.clearData()
                    viewModel.dataGyro.clearData()
                    viewModel.dataTime.clearData()
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