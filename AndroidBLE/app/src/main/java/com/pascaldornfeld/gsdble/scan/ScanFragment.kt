package com.pascaldornfeld.gsdble.scan

import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pascaldornfeld.gsdble.R
import kotlinx.android.synthetic.main.connect_fragment.*
import kotlinx.android.synthetic.main.connect_fragment.view.*

/**
 * fragment to scan for new devices
 */
class ScanFragment : DialogFragment() {
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
                if (result != null && isDeviceNotConnected(result.device)) devicesFound =
                    devicesFound.plusElement(result).distinctBy { it.device.address }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                if (results != null) devicesFound =
                    devicesFound.plus(results.filter { isDeviceNotConnected(it.device) })
                        .distinctBy { it.device.address }
            }
        }
    }

    private var leScanner: (() -> (BluetoothLeScanner?))? = null
    private var onUserWantsToConnect: ((BluetoothDevice) -> Unit)? = null
    private var isDeviceNotConnected: ((BluetoothDevice) -> Boolean) = { true }

    /**
     * @param leScanner function returning the ble-scanner
     * @param onUserWantsToConnect the user wants to connect to the bluetooth device
     */
    fun initialize(
        leScanner: () -> (BluetoothLeScanner?),
        onUserWantsToConnect: ((BluetoothDevice) -> Unit),
        isDeviceAlreadyConnected: ((BluetoothDevice) -> Boolean)
    ) {
        this.leScanner = leScanner
        this.onUserWantsToConnect = onUserWantsToConnect
        this.isDeviceNotConnected = isDeviceAlreadyConnected
    }

    /**
     * create adapter on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = ScanAdapter { newDevice: BluetoothDevice ->
            onUserWantsToConnect?.invoke(newDevice)
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = activity!!.layoutInflater.inflate(R.layout.connect_fragment, null).apply {
            vDevicesList.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            vDevicesList.adapter = adapter
        }
        return activity?.let {
            AlertDialog.Builder(it)
                .setView(view)
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onResume() {
        super.onResume()
        devicesFound = emptyList()
        startScan()
    }

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
                            {
                                stopScan()
                                context?.let { context ->
                                    Toast.makeText(context, "timeout", Toast.LENGTH_SHORT).show()
                                }
                            },
                            SCAN_PERIOD
                        )
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
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}