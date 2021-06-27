package com.kristjanskutta.gizmoled

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.*
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val SCAN_PERIOD: Long = 20000
    private val REQUEST_ENABLE_BLUETOOTH = 1337
    private val PERMISSION_REQUEST_ALL = 5001
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var bleBinder: BLEService.BLEBinder? = null
    private var useBGService = false

    private val PREF_USE_SERVICE_KEY = "serviceEnabled"

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

        useBGService =
            PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_USE_SERVICE_KEY, useBGService)

        setContentView(R.layout.activity_main)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        setSupportActionBar(toolbar)

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            // Start Location Settings Activity, you should explain to the user why he need to enable location before.
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

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

        findViewById<FloatingActionButton>(R.id.addDevice).setOnClickListener { view ->
            stopScanningAndClear()
            scanLeDevice(true)
        }


        //content = findViewById(android.R.id.content)

//        collars.add("dev 0")
//        collars.add("dev 1")
//        rv_collars.adapter?.notifyDataSetChanged()
        // Creates a vertical Layout Manager
        val rvCollars = findViewById<RecyclerView>(R.id.rv_collars)
        rvCollars?.layoutManager = LinearLayoutManager(this)

        // Access the RecyclerView Adapter and load the data into it
        rvCollars?.adapter = CollarsAdapter(collars, this)

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
        registerReceiver(bleReceiver, IntentFilter("STOPSCANNER"))
        scanLeDevice(true)

        if (bleBinder == null) {
            val bindIntent = Intent(this, BLEService::class.java)
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        scanLeDevice(false)
        unregisterReceiver(bleReceiver)
        if (bleBinder != null) {
            unbindService(serviceConnection)
            bleBinder = null
        }

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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val serviceIsRunning = useBGService
        menu.findItem(R.id.action_test_visualizer)?.isVisible = false
        val stopOption = menu.findItem(R.id.action_stop_service)
        val startOption = menu.findItem(R.id.action_start_service)
        stopOption?.isVisible = serviceIsRunning
        startOption?.isVisible = !serviceIsRunning && bluetoothAdapter?.bluetoothLeScanner != null
        //return true
        return super.onCreateOptionsMenu(menu)
    }

    private fun startBGService() {
        if (!useBGService) {
            return
        }
        if (bleBinder == null) {
            val bindIntent = Intent(this, BLEService::class.java)
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, BLEService::class.java).also { intent ->
            intent.putExtra(
                BLEService.INTENT_KEY_COMMAND,
                BLEService.COMMAND_START_CONTINUOUS_SERVICE
            )
            startService(intent)
        }
    }

    private fun stopBGService() {
        if (bleBinder != null) {
            unbindService(serviceConnection)
            bleBinder = null
        }
        Intent(this@MainActivity, BLEService::class.java).also { intent ->
            intent.putExtra(
                BLEService.INTENT_KEY_COMMAND,
                BLEService.COMMAND_END_CONTINUOUS_SERVICE
            )
            startService(intent)
        }
        invalidateOptionsMenu()
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
            R.id.action_stop_service -> {
                useBGService = false
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(PREF_USE_SERVICE_KEY, useBGService).apply()
                stopBGService()
                true
            }
            R.id.action_start_service -> {
                useBGService = true
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(PREF_USE_SERVICE_KEY, useBGService).apply()
                startBGService()
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

        if (knownCollars.contains(device.address)) {
            return
        }

        knownCollars.add(device.address)

        val nCollar = Collar(device, device.name!!, connected)
        collars.add(nCollar)
        findViewById<RecyclerView>(R.id.rv_collars).adapter?.notifyDataSetChanged()
    }

    private fun addConnectedCollars(): Boolean {
        if (bleBinder == null) {
            return false
        }

        val connectedCollars = bleBinder?.getService()?.getDevices()!!
        for (collar in connectedCollars) {
            addFoundCollar(collar, true)
        }

        return connectedCollars.isNotEmpty()
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
            if (result?.scanRecord == null || result.device == null || result.device?.name == null) {
                return
            }

            val device = result.device!!

            if (knownCollars.contains(device.address)) {
                return
            }

            val services = result.scanRecord?.serviceUuids
            val hasVisualizerService = services?.contains(ParcelUuid(BLEConstants.LEDServiceV))
            if (services?.contains(ParcelUuid(BLEConstants.LEDService)) != true &&
                hasVisualizerService != true
            ) {
                return
            }

            if (hasVisualizerService == true) {
                Intent(this@MainActivity, BLEService::class.java).also { intent ->
                    intent.putExtra(
                        BLEService.INTENT_KEY_COMMAND,
                        BLEService.COMMAND_ADD_KNOWN_DEVICE
                    )
                    intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
                    startService(intent)
                }
            } else {
                Intent(this@MainActivity, BLEService::class.java).also { intent ->
                    intent.putExtra(
                        BLEService.INTENT_KEY_COMMAND,
                        BLEService.COMMAND_REMOVE_KNOWN_DEVICE
                    )
                    intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
                    startService(intent)
                }
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
        if (bluetoothLeScanner != null) {
            if (enable) { // Stops scanning after a pre-defined scan period.
                val currentScanIndex = ++lastScanIndex
                handler.postDelayed({
                    if (currentScanIndex == lastScanIndex) {
                        mScanning = false
                        bluetoothLeScanner.stopScan(mLeScanCallback)
                    }
                }, SCAN_PERIOD)
                mScanning = true

//                startBGService()

                bluetoothLeScanner.startScan(mLeScanCallback)

                setListSearching()
                addConnectedCollars()
            } else {
                mScanning = false
                bluetoothLeScanner.stopScan(mLeScanCallback)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            bleBinder = binder as BLEService.BLEBinder
            addConnectedCollars()
            invalidateOptionsMenu()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    private fun setListSearching() {
        listInit = true
        knownCollars.clear()
        collars.clear()
        collars.add(Collar(null, getString(R.string.device_searching), false))
        findViewById<RecyclerView>(R.id.rv_collars).adapter?.notifyDataSetChanged()
    }

    private fun setListFailure() {
        listInit = false
        knownCollars.clear()
        collars.clear()
        collars.add(Collar(null, getString(R.string.device_nothing_found), false))
        findViewById<RecyclerView>(R.id.rv_collars).adapter?.notifyDataSetChanged()
    }
}
