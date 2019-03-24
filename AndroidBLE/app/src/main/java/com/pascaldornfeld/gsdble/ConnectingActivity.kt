package com.pascaldornfeld.gsdble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConnectingActivity : AppCompatActivity() {
    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val vBtStartScan by lazy(LazyThreadSafetyMode.NONE) { findViewById<Button>(R.id.bt_startScan) }
    private val handler = Handler()
    private val leScanCallback by lazy(LazyThreadSafetyMode.NONE) {
        object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                stopScan()
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (result != null) {
                    vBtStartScan.isEnabled = false
                    stopScan()
                    startActivity(Intent(this@ConnectingActivity, ConnectedActivity::class.java).putExtra(ConnectedActivity.EXTRA_DEVICE, result.device))
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Toast.makeText(this@ConnectingActivity, "not_impl: onBatchScanResults", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vBtStartScan.setOnClickListener {
            if (scanning) stopScan()
            else startScan()
        }
    }

    private fun startScan() {
        synchronized(this) {
            scanning = true
            try {
                bluetoothAdapter.bluetoothLeScanner.startScan(
                    listOf(ScanFilter.Builder().setDeviceName("GSDBLE").build()),
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    leScanCallback
                )
                vBtStartScan.text = "Stop Scan"
                handler.postDelayed({
                    Toast.makeText(this, "no device found", Toast.LENGTH_SHORT).show()
                    stopScan()
                }, SCAN_PERIOD)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopScan() {
        synchronized(this) {
            scanning = false
            try {
                handler.removeCallbacksAndMessages(null)
                vBtStartScan.text = "Start Scan"
                bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!bluetoothAdapter.isEnabled) startActivityForResult(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            REQUEST_ENABLE_BT
        )
        else askForLocationPermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) askForLocationPermission()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_LOC && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) vBtStartScan.isEnabled =
            true
    }

    private fun askForLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_PERMISSION_LOC)
            return
        }
        vBtStartScan.isEnabled = true
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSION_LOC = 2
        private const val SCAN_PERIOD = 10000L
    }
}
