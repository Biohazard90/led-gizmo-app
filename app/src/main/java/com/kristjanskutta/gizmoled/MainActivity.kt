package com.kristjanskutta.gizmoled

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*


class MainActivity : AppCompatActivity() {

    private val SCAN_PERIOD: Long = 10000
    private val REQUEST_ENABLE_BLUETOOTH = 1337
    private val PERMISSION_REQUEST_ALL = 5001
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var bleBinder: BLEService.BLEBinder? = null

    val collars: ArrayList<Collar> = ArrayList()
    val knownCollars: MutableSet<String> = HashSet()
    var listInit = true

    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scanLeDevice(false)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            )
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            )
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                PERMISSION_REQUEST_ALL
            )
        }

        addDevice.setOnClickListener { view ->
            stopScanningAndClear()
            scanLeDevice(true)
        }


        //content = findViewById(android.R.id.content)

//        collars.add("dev 0")
//        collars.add("dev 1")
//        rv_collars.adapter?.notifyDataSetChanged()
        // Creates a vertical Layout Manager
        rv_collars.layoutManager = LinearLayoutManager(this)

        // Access the RecyclerView Adapter and load the data into it
        rv_collars.adapter = CollarsAdapter(collars, this)

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
        }
    }

//    override fun onStart() {
//        super.onStart()
//        registerReceiver(bleReceiver, IntentFilter("STOPSCANNER"))
//        scanLeDevice(true)
//
//        val bindIntent = Intent(this, BLEService::class.java)
//        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
//    }

    override fun onResume() {
        super.onResume()
        bleBinder = null
        registerReceiver(bleReceiver, IntentFilter("STOPSCANNER"))
        scanLeDevice(true)

        val bindIntent = Intent(this, BLEService::class.java)
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        scanLeDevice(false)
        unregisterReceiver(bleReceiver)
        unbindService(serviceConnection)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            scanLeDevice(true)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_ALL) {
            scanLeDevice(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_test_visualizer -> {
                stopScanningAndClear()
                val intent = Intent(this, DevVisualizerActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addFoundCollar(device: BluetoothDevice, connected: Boolean) {
        if (listInit) {
            listInit = false
            knownCollars.clear()
            collars.clear()
        }

        knownCollars.add(device.address)

        val nCollar = Collar(device, device.name!!, connected)
        collars.add(nCollar)
        rv_collars.adapter?.notifyDataSetChanged()
    }

    private fun addConnectedCollars(): Boolean {
        if (bleBinder == null) {
            return false
        }

        val connectedCollars = bleBinder?.getService()?.getDevices()!!
        for (collar in connectedCollars) {
            addFoundCollar(collar, true)
        }

        return !connectedCollars.isEmpty()
    }

    private fun stopScanningAndClear() {
        if (mScanning) {
            scanLeDevice(false)
        }
        knownCollars.clear()
        collars.clear()
    }

    private val mLeScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result?.scanRecord == null || result?.device == null || result?.device?.name == null) {
                return
            }

            val device = result.device!!

            if (knownCollars.contains(device.address)) {
                return
            }

            if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(BLEConstants.LEDService)) != true) {
                return
            }

            if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                addFoundCollar(device, false)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult?>?) {
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                setListFailure()
            }
        }
    }

    private var mScanning = false
    private var lastScanIndex = 0
    private fun scanLeDevice(enable: Boolean) {
        if (bluetoothAdapter == null) {
            return
        }

        if (mScanning) {
            return
        }

        val bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        if (enable) { // Stops scanning after a pre-defined scan period.
            val currentScanIndex = ++lastScanIndex
            handler.postDelayed({
                if (currentScanIndex == lastScanIndex) {
                    mScanning = false
                    bluetoothLeScanner.stopScan(mLeScanCallback)
                }
            }, SCAN_PERIOD)
            mScanning = true
            bluetoothLeScanner.startScan(mLeScanCallback)

//            val connectedDevices = .devices()
            setListSearching()
            addConnectedCollars()
        } else {
            mScanning = false
            bluetoothLeScanner.stopScan(mLeScanCallback)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            bleBinder = binder as BLEService.BLEBinder
            addConnectedCollars()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleBinder = null
        }
    }

    private fun setListSearching() {
        listInit = true
        knownCollars.clear()
        collars.clear()
        collars.add(Collar(null, getString(R.string.device_searching), false))
        rv_collars.adapter?.notifyDataSetChanged()
    }

    private fun setListFailure() {
        if (listInit) {
            listInit = false
            knownCollars.clear()
            collars.clear()
            collars.add(Collar(null, getString(R.string.device_nothing_found), false))
            rv_collars.adapter?.notifyDataSetChanged()
        }
    }
}
