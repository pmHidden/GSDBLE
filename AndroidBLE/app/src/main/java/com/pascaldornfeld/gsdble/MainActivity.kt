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
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.pascaldornfeld.gsdble.connected.GsdbleViewModel
import com.pascaldornfeld.gsdble.connected.gsdble_library.GsdbleManager
import com.pascaldornfeld.gsdble.connected.view.DeviceFragment
import com.pascaldornfeld.gsdble.connected.view.DeviceFragment.Companion.DEVICE
import com.pascaldornfeld.gsdble.scan.ScanDialogFragment
import kotlinx.android.synthetic.main.main_activity.*


class MainActivity : AppCompatActivity(), DeviceFragment.RemovableDeviceActivity {
    private var bleReady = true
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter }
    private val fragmentList = mutableListOf<DeviceFragment>()
    private lateinit var connectDialog: ScanDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        connectDialog = ScanDialogFragment().apply {
            initialize(
                { bluetoothAdapter!!.bluetoothLeScanner },
                { addDeviceFragment(it) },
                {
                    fragmentList.none { knownDevice -> (knownDevice.device().address == it.address) }
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
        fragmentList.find { it.device().address == device.address }?.let { toRemove ->
            if (fragmentList.remove(toRemove))
                supportFragmentManager.beginTransaction().remove(toRemove).commit()
        }
    }

    /**
     * create a device-fragment. connect to the device.
     * add the fragment to the adapter-list
     */
    private fun addDeviceFragment(device: BluetoothDevice) {
        val gsdbleFragment = DeviceFragment.newInstance(device)
        fragmentList.add(gsdbleFragment)
        supportFragmentManager.beginTransaction().add(vFragmentContainer.id, gsdbleFragment)
            .commit()
    }

    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is DeviceFragment) {
            val device = fragment.requireArguments().getParcelable<BluetoothDevice>(DEVICE)!!
            val itsViewModel = ViewModelProviders.of(fragment).get(GsdbleViewModel::class.java)
            val gsdbleManager = GsdbleManager(device, this, itsViewModel)
            fragment.setWriteToDeviceIfc(gsdbleManager)
        }
    }

    /**
     * check all the permissions and requirements for a ble scan
     */
    override fun onResume() {
        super.onResume()
        bleReady = false
        invalidateOptionsMenu()
        if (checkBluetoothEnabled() && checkLocationPermission() && checkLocationEnabled()) {
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