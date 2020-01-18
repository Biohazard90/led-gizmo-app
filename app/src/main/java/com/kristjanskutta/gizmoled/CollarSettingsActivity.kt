package com.kristjanskutta.gizmoled

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT32
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.*
import android.app.*
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import android.view.Menu
import android.view.MenuItem

private const val TITLE_TAG = "settingsActivityTitle"

interface OnFragmentPreferenceChanged {
    fun onFragmentPreferenceChanged(key: String, value: Int)
    fun onFragmentPreferenceChanged(key: String, value: Boolean)
    fun onFragmentPreferenceChanged(key: String, value: String)
}

class CollarSettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    OnFragmentPreferenceChanged {

    private val PERMISSION_REQUEST_RECORD_AUDIO = 6001

    var device: BluetoothDevice? = null
    var ledService: BluetoothGattService? = null
    var currentEffectCharacteristic: BluetoothGattCharacteristic? = null
    var currentEffectId: UUID? = null
    var currentEffectSettings: ByteArray? = null
    var lastResetCall = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        device = intent?.extras?.getParcelable("collarDevice")
        val name = intent?.extras?.getString("collarName")

//        if (savedInstanceState == null) {
//            showLoadingMenu()
//        } else {
//            title = savedInstanceState.getCharSequence(TITLE_TAG)
//        }
//        supportFragmentManager.addOnBackStackChangedListener {
//            if (supportFragmentManager.backStackEntryCount == 0) {
////                setTitle(name + " Settings")
//            }
//        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(name + " Settings")

//        createPreferenceListener();
    }

    override fun onResume() {
        super.onResume()

        showLoadingMenu()
        device?.connectGatt(this, false, gattWrapper, TRANSPORT_LE)
    }

    override fun onPause() {
        super.onPause()
        gattWrapper.close()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_collar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                val effectIndex = Effects.effectUUIDToIndex[currentEffectId]!!
                val fnResetCharacteristic = ledService?.getCharacteristic(BLEConstants.FnResetCharacteristic)
                val newValue = byteArrayOf((++lastResetCall).toByte(), effectIndex.toByte())
                fnResetCharacteristic?.setValue(newValue)
                gattWrapper.wrappedWriteCharacteristic(fnResetCharacteristic)

                if (lastResetCall > 255) {
                    lastResetCall = 1
                }

                loadPreferenceMenu(Effects.effectIndexToString[effectIndex]!!)
                gattWrapper.wrappedReadCharacteristic(currentEffectCharacteristic)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            updateVisualizerStreamEnabled()
        }
    }

    private fun showLoadingMenu() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, CollarLoadingFragment())
            .commit()
    }

    private fun loadPreferenceMenu(currentEffect: String) {
        val bundle = Bundle()
        bundle.putString("currentEffect", currentEffect)
        val fragInfo = CollarEffectsFragment()
        fragInfo.arguments = bundle

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, fragInfo)
            .commit()
    }

    private fun loadPreferenceMenu(subPreferences: Int, effectSettings: ByteArray) {
        val bundle = Bundle()
        bundle.putInt("subPreferences", subPreferences)
        bundle.putByteArray("effectSettings", effectSettings)
        val fragInfo = CollarEffectsFragment()
        fragInfo.arguments = bundle

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, fragInfo)
            .commit()
    }

    private fun updateVisualizerStreamEnabled() {
        val shouldEnableStream = currentEffectId == BLEConstants.LEDVisualizerSettingsCharacteristic
        if (shouldEnableStream) {
            if (BLEService.needsPermissions(this)) {
                ActivityCompat.requestPermissions(
                    this, BLEService.getPermissionList(), PERMISSION_REQUEST_RECORD_AUDIO
                )
            } else {
                // Enable stream
                Intent(this, BLEService::class.java).also { intent ->
                    intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_REGISTER_DEVICE)
                    intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
                    startService(intent)
                }
            }
        } else {
            // Disable stream
            Intent(this, BLEService::class.java).also { intent ->
                intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_UNREGISTER_DEVICE)
                intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
                startService(intent)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        } else {
            gattWrapper.close()
//            finish()
            NavUtils.navigateUpFromSameTask(this)
            return true
//            return super.onSupportNavigateUp()
        }
    }

    var gattWrapper: BluetoothGattWrapper = object : BluetoothGattWrapper() {
        override fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            ledService = gatt!!.getService(BLEConstants.LEDService)
            val ledTypeCharacteristic = ledService!!.getCharacteristic(BLEConstants.LEDTypeCharacteristic)
            wrappedReadCharacteristic(ledTypeCharacteristic)
        }

        override fun onWrappedDisconnected(gatt: BluetoothGatt?) {
            runOnUiThread {
                Toast.makeText(this@CollarSettingsActivity, getString(R.string.device_lost), Toast.LENGTH_SHORT).show()

                val upIntent = Intent(applicationContext, MainActivity::class.java)
                if (NavUtils.shouldUpRecreateTask(this@CollarSettingsActivity, upIntent)) {
                    TaskStackBuilder.create(this@CollarSettingsActivity).addNextIntentWithParentStack(upIntent).startActivities()
                } else {
                    upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(upIntent)
                    finish()
                }
            }
        }

        override fun onWrappedCharacteristicRead(
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {

            if (status != GATT_SUCCESS) {
                return
            }
            when (characteristic?.uuid) {
                BLEConstants.LEDTypeCharacteristic -> {
                    val currentType = characteristic?.getIntValue(FORMAT_SINT32, 0) ?: 0
                    val effectUUID = Effects.effectIndexToUUID[currentType]
                    wrappedReadCharacteristic(ledService?.getCharacteristic(effectUUID))

                    runOnUiThread {
                        loadPreferenceMenu(Effects.effectIndexToString[currentType]!!)
                    }
                }

                BLEConstants.LEDBlinkSettingsCharacteristic,
                BLEConstants.LEDWaveSettingsCharacteristic,
                BLEConstants.LEDColorWheelSettingsCharacteristic,
                BLEConstants.LEDVisualizerSettingsCharacteristic,
                BLEConstants.LEDVisorSettingsCharacteristic,
                BLEConstants.LEDPoliceSettingsCharacteristic,
                BLEConstants.LEDChristmasSettingsCharacteristic -> {
                    val value = characteristic?.value!!
                    runOnUiThread {
                        currentEffectCharacteristic = characteristic
                        currentEffectId = characteristic?.uuid
                        currentEffectSettings = value
                        loadPreferenceMenu(Effects.effectUUIDToResource[currentEffectId]!!, value)
                        updateVisualizerStreamEnabled()
                    }
                }

                else -> throw Exception()
            }
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    override fun onFragmentPreferenceChanged(key: String, value: Int) {
        if (gattWrapper != null &&
            currentEffectSettings != null
        ) {
            val isColor = Effects.isColor(key)
            if (isColor) {
                val convertedValue = ColorUtilities.BGR2RGB(value);
                val offset = Effects.offsets[currentEffectId]!![key]!!
                if (offset + 2 >= currentEffectSettings!!.size) {
                    throw ArrayIndexOutOfBoundsException()
                }
                currentEffectSettings!![offset] = (convertedValue and 0xFF).toByte()
                currentEffectSettings!![offset + 1] = (convertedValue shr 8 and 0xFF).toByte()
                currentEffectSettings!![offset + 2] = (convertedValue shr 16 and 0xFF).toByte()
            } else {
                val offset = Effects.offsets[currentEffectId]!![key]!!
                if (offset >= currentEffectSettings!!.size) {
                    throw ArrayIndexOutOfBoundsException()
                }
                currentEffectSettings!![offset] = value.toByte()
            }

            currentEffectCharacteristic?.value = currentEffectSettings
            gattWrapper?.wrappedWriteCharacteristic(currentEffectCharacteristic)
        }
    }

    override fun onFragmentPreferenceChanged(key: String, value: String) {
        var typeIndex = -1
        when (key) {
            "led_type" -> {
                typeIndex = Effects.effectStringToIndex[value]!!
            }
        }

        // Update LED type on device
        val ledTypeCharacteristic = ledService?.getCharacteristic(BLEConstants.LEDTypeCharacteristic)
        ledTypeCharacteristic?.setValue(typeIndex, FORMAT_UINT8, 0)
        gattWrapper?.wrappedWriteCharacteristic(ledTypeCharacteristic)

        loadPreferenceMenu(value)

        // Load new effect data
        val newEffectCharacteristic = ledService?.getCharacteristic(Effects.effectIndexToUUID[typeIndex])
        gattWrapper?.wrappedReadCharacteristic(newEffectCharacteristic)
    }

    override fun onFragmentPreferenceChanged(key: String, value: Boolean) {
        if (gattWrapper != null) {
            val offset = Effects.offsets[currentEffectId]!![key]!!
            if (offset >= currentEffectSettings!!.size) {
                throw ArrayIndexOutOfBoundsException()
            }
            currentEffectSettings!![offset] = if (value) 1 else 0
            currentEffectCharacteristic?.value = currentEffectSettings
            gattWrapper?.wrappedWriteCharacteristic(currentEffectCharacteristic)
//            connectedGatt?.executeReliableWrite()
        }
    }
}

class CollarLoadingFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.collar_effects_loading, rootKey)
    }
}

class CollarEffectsFragment : PreferenceFragmentCompat() {
    private var listener: OnFragmentPreferenceChanged? = null
    private var listenerUpdateEnabled = true
    var preferenceListener = object : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences,
            key: String
        ) {
            if (listener == null) {
                return
            }
            if (!listenerUpdateEnabled) {
                return
            }
            var success = false
            try {
                val value = sharedPreferences.getInt(key, 0xFF0000)
                listener?.onFragmentPreferenceChanged(key, value)
                success = true
            } catch (e: ClassCastException) {
            }

            if (!success) {
                try {
                    val value = sharedPreferences.getString(key, "")
                    listener?.onFragmentPreferenceChanged(key, value!!)
                    success = true
                } catch (e: ClassCastException) {
                }
            }

            if (!success) {
                try {
                    val value = sharedPreferences.getBoolean(key, false)
                    listener?.onFragmentPreferenceChanged(key, value!!)
                    //success = true
                } catch (e: ClassCastException) {
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.collar_effects_preferences, rootKey)

        val currentEffect = arguments?.getString("currentEffect") ?: ""
        if (currentEffect.isNotEmpty()) {
            listenerUpdateEnabled = false
            preferenceManager.sharedPreferences.edit().putString("led_type", currentEffect).apply()
            listenerUpdateEnabled = true
        }

        val subPreferences = arguments?.getInt("subPreferences") ?: 0
        if (subPreferences != 0) {
            val effectSettings = arguments?.getByteArray("effectSettings")
            if (effectSettings != null) {
                when (subPreferences) {
                    R.xml.collar_effects_preferences_blink,
                    R.xml.collar_effects_preferences_wave,
                    R.xml.collar_effects_preferences_wheel,
                    R.xml.collar_effects_preferences_visualizer,
                    R.xml.collar_effects_preferences_visor,
                    R.xml.collar_effects_preferences_police,
                    R.xml.collar_effects_preferences_christmas -> {
                        listenerUpdateEnabled = false
                        val preferences = preferenceManager.sharedPreferences.edit();
                        val uuid = Effects.effectResourceToUUID[subPreferences]!!
                        val offsets = Effects.offsets[uuid]!!

                        offsets.forEach { (key, position) ->
                            if (Effects.isColor(key)) {
                                preferences.putInt(
                                    key,
                                    ColorUtilities.BytesRGB2BGR(
                                        effectSettings[position],
                                        effectSettings[position + 1],
                                        effectSettings[position + 2]
                                    )
                                )
                            } else if (Effects.isBoolean(key)) {
                                preferences.putBoolean(key, effectSettings[position].toInt() != 0)
                            } else {
                                // Int
                                preferences.putInt(key, effectSettings[position].toUByte().toInt())
                            }
                        }

                        preferences.apply()
                        listenerUpdateEnabled = true
                    }
                    else -> throw Exception()
                }

//                if (subPreferences == R.xml.collar_effects_preferences_visualizer) {
                // Attach bluetooth stuff to service until the type gets changed again

//        if (BLEService.needsPermissions(this)) {
//            ActivityCompat.requestPermissions(
//                this, BLEService.getPermissionList(), PERMISSION_REQUEST_RECORD_AUDIO
//            )
//        } else {
////            BLEVisualizer.getInstance().start()
//        }
//                } else {
//                    // Remove bluetooth device from service since we changed the effect type now
//                }
            }

            addPreferencesFromResource(subPreferences)
        } else {
            addPreferencesFromResource(R.xml.collar_effects_loading)
        }

        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(
            preferenceListener
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentPreferenceChanged) {
            listener = context


        }
    }

    override fun onDetach() {
        listener = null
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(
            preferenceListener
        )
        super.onDetach()
    }
}