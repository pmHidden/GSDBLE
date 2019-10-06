package com.pascaldornfeld.gsdble.scan

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.pascaldornfeld.gsdble.R
import kotlinx.android.synthetic.main.connect_fragment.*
import kotlinx.android.synthetic.main.connect_fragment.view.*

/**
 * fragment to scan for new devices
 */
class ScanFragment : Fragment() {
    private lateinit var adapter: ScanAdapter
    private var devicesFound = emptyList<ScanResult>()
        set(value) {
            field = value
            adapter.submitList(value)
        }
    private var scanning = false
    private val handler = Handler()

    private val leScanCallback by lazy {
        object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.w(TAG, "Scan ended with error code: $errorCode")
                stopScan()
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                Log.i(TAG, "found device!")
                if (result != null && isDeviceNotConnected(result.device)) devicesFound =
                    devicesFound.plusElement(result).distinctBy { it.device.address }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Log.i(TAG, "found devices!")
                if (results != null) devicesFound =
                    devicesFound.plus(results.filter { isDeviceNotConnected(it.device) })
                        .distinctBy { it.device.address }
            }
        }
    }

    private var scanAllowed: LiveData<Boolean>? = null
    private var leScanner: (() -> (BluetoothLeScanner?))? = null
    private var onUserWantsToConnect: ((BluetoothDevice) -> Unit)? = null
    private var isDeviceNotConnected: ((BluetoothDevice) -> Boolean) = { true }

    /**
     * @param scanAllowed if scan is allowed. usually when bluetooth is ready
     * @param leScanner function returning the ble-scanner
     * @param onUserWantsToConnect the user wants to connect to the bluetooth device
     */
    fun initialize(
        scanAllowed: LiveData<Boolean>,
        leScanner: () -> (BluetoothLeScanner?),
        onUserWantsToConnect: ((BluetoothDevice) -> Unit),
        isDeviceAlreadyConnected: ((BluetoothDevice) -> Boolean)
    ) {
        this.scanAllowed = scanAllowed
        this.leScanner = leScanner
        this.onUserWantsToConnect = onUserWantsToConnect
        this.isDeviceNotConnected = isDeviceAlreadyConnected
        scanAllowed.observe(
            this,
            Observer<Boolean> { t ->
                if (t != null) view?.vStartScanButton?.isEnabled = t
            })
    }


    /**
     * create adapter on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = ScanAdapter { newDevice: BluetoothDevice ->
            onUserWantsToConnect?.invoke(newDevice)
            devicesFound =
                devicesFound.filterNot { oldDevice: ScanResult -> newDevice.address == oldDevice.device.address }
        }
    }

    /**
     * add adapter to recyclerView after it was created.
     * set observer for enabling / disabling scan-button
     * add button listener for starting to scan
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.connect_fragment, container, false)
        view.vDevicesList.layoutManager =
            LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        view.vDevicesList.adapter = adapter
        view.vStartScanButton.isEnabled = false
        view.vStartScanButton.setOnClickListener {
            if (scanning) stopScan()
            else startScan()
        }
        return view
    }

    /**
     * clears the list of found devices on resume
     */
    override fun onResume() {
        super.onResume()
        devicesFound = emptyList()
    }

    /**
     * stop scanning on pause
     */
    override fun onPause() {
        super.onPause()
        stopScan()
    }

    companion object {
        private const val SCAN_PERIOD = 30000L
        private val TAG = ScanFragment::class.java.simpleName.filter { it.isUpperCase() }
    }

    /**
     * start scanning for devices.
     *
     * clears list of found devices before scanning
     * scan is filtering for devices called R.string.device_name
     * changes the text of the scan-button to 'stop'
     * timeout for scanning is SCAN_PERIOD
     *
     * ignored if scanning or ble-scanner not ready
     */
    private fun startScan() {
        synchronized(this) {
            if (!scanning) {
                try {
                    val scanner = leScanner?.invoke()
                    if (scanner != null) {
                        devicesFound = emptyList()
                        // we cannot filter by service uuid, since we are not advertising with service uuid.
                        // we are not advertising with service uuid, since service id is custom 128-bit, so it is too big to advertise with.
                        // this is why we must filter by device name.
                        scanner.startScan(
                            /*listOf(ScanFilter.Builder().setDeviceName(getString(R.string.device_name)).build()),
                            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),*/
                            leScanCallback
                        )
                        scanning = true
                        vStartScanButton.text = getString(R.string.scan_stop)
                        handler.postDelayed(
                            { stopScan() },
                            SCAN_PERIOD
                        )
                        Log.i(TAG, "scan started!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * stop scanning for devices.
     *
     * changes the text of the scan-button to 'start'
     * removes timeout-timer
     *
     * ignored if not scanning or ble-scanner not ready
     */
    private fun stopScan() {
        synchronized(this) {
            if (scanning) {
                try {
                    val scanner = leScanner?.invoke()
                    if (scanner != null) {
                        scanner.stopScan(leScanCallback)
                        handler.removeCallbacksAndMessages(null)
                        vStartScanButton.text = getString(R.string.scan_start)
                        scanning = false
                        Log.i(TAG, "scan stopped!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}