package com.pascaldornfeld.gsdble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.connected.DeviceViewModel
import com.pascaldornfeld.gsdble.connected.hardware_library.DeviceManager
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.view.DeviceFragment
import com.pascaldornfeld.gsdble.scan.ScanDialogFragment
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : AppCompatActivity(), DeviceFragment.RemovableDeviceActivity {
    private var bleReady = true
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter }
    private lateinit var connectDialog: ScanDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        connectDialog = ScanDialogFragment().apply {
            initialize(
                { bluetoothAdapter!!.bluetoothLeScanner },
                { addDeviceFragment(it) },
                {
                    supportFragmentManager.fragments
                        .filterIsInstance<DeviceFragment>()
                        .none { knownDevice -> (knownDevice.device().address == it.address) }
                }
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean =
        if (bleReady) {
            menuInflater.inflate(R.menu.main_menu, menu)
            true
        } else false

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return if (item != null && item.itemId == R.id.search) {
            connectDialog.show(supportFragmentManager, null)
            true
        } else super.onOptionsItemSelected(item)
    }

    /**
     * remove the fragment from the adapter-list
     */
    override fun removeDeviceFragment(device: BluetoothDevice) {
        supportFragmentManager.fragments
            .filterIsInstance<DeviceFragment>()
            .find { it.device().address == device.address }
            ?.let { supportFragmentManager.beginTransaction().remove(it).commit() }
    }

    /**
     * create a device-fragment. connect to the device.
     * add the fragment to the adapter-list
     */
    private fun addDeviceFragment(device: BluetoothDevice) {
        try {
            supportFragmentManager
                .beginTransaction()
                .add(vFragmentContainer.id, DeviceFragment.newInstance(device))
                .commit()
        } catch (e: Exception) {
            Log.w(TAG, "Could not add Device Fragment")
            e.printStackTrace()
        }
    }

    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is DeviceFragment) fragment.setWriteToDeviceIfc(
            DeviceManager(
                fragment.device(),
                this,
                DeviceViewModel.forDeviceFragment(fragment),
                BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
                ImuConfig(3, false) // odr = 208Hz
            )
        )
    }

    /**
     * check all the permissions and requirements for a ble scan
     */
    override fun onResume() {
        super.onResume()
        bleReady = false
        invalidateOptionsMenu()
        if (checkPermissions() && checkBluetoothEnabled() && checkLocationEnabled()) {
            bleReady = true
            invalidateOptionsMenu()
        }
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
     * check for permissions. ask for permission if not
     * @return true if permission was granted
     */
    private fun checkPermissions(): Boolean =
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_PERMISSION
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
        private const val REQUEST_PERMISSION = 2
        private val TAG = MainActivity::class.java.simpleName.filter { it.isUpperCase() }
    }
}