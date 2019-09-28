package com.pascaldornfeld.gsdble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import kotlinx.android.synthetic.main.main_activity.*


class MainActivity : FragmentActivity(), DeviceFragment.RemovableDeviceActivity {

    private val bleReady = MutableLiveData<Boolean>()
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter }
    private val fragmentList = mutableListOf<DeviceFragment>()
    private lateinit var fragmentAdapter: MyFragmentPagerAdapter<DeviceFragment>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val connectFragment = ConnectFragment()
        connectFragment.initialize(
            bleReady,
            { bluetoothAdapter!!.bluetoothLeScanner },
            { addDeviceFragment(it) },
            { fragmentList.none { knownDevice -> knownDevice.device.address == it.address } }
        )
        fragmentAdapter =
            MyFragmentPagerAdapter(supportFragmentManager, connectFragment, fragmentList)
        vFragmentPager.adapter = fragmentAdapter
    }

    override fun removeDeviceFragment(device: BluetoothDevice) {
        fragmentList.removeIf { it.device.address == device.address }
        fragmentAdapter.notifyDataSetChanged()
    }

    private fun addDeviceFragment(device: BluetoothDevice) {
        fragmentList.add(DeviceFragment.newInstance(device))
        fragmentAdapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        bleReady.postValue(false)
        if (checkBluetoothEnabled() && checkLocationPermission() && checkLocationEnabled())
            bleReady.postValue(true)
    }

    /**
     * check if bluetooth is available and enabled. ask for enabled if not
     * @return true if bluetooth was available and ready
     */
    private fun checkBluetoothEnabled(): Boolean {
        if (bluetoothAdapter != null) {
            return if (!bluetoothAdapter!!.isEnabled) {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    REQUEST_ENABLE_BT
                )
                false
            } else true
        } else {
            AlertDialog.Builder(this)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.bt_not_available)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
            return false
        }
    }

    /**
     * check for location permission. ask for permission if not
     * @return true if permission was granted
     */
    private fun checkLocationPermission(): Boolean =
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSION_LOC
            )
            false
        } else true

    /**
     * check if location is enabled. ask for enabled if not
     * @return true if location was enabled
     */
    private fun checkLocationEnabled(): Boolean {
        val locationManager = (getSystemService(Context.LOCATION_SERVICE) as LocationManager?)
        return if (locationManager != null
            && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        ) true
        else {
            AlertDialog.Builder(this)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage("Please enable location on your device")
                .setPositiveButton(android.R.string.ok)
                { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .show()
            false
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSION_LOC = 2
    }
}