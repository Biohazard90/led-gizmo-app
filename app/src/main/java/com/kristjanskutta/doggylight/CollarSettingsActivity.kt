package com.kristjanskutta.doggylight

import android.bluetooth.*
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT32
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.service.autofill.Validators.or
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.lang.Exception
import java.util.*
import kotlin.experimental.or


private const val TITLE_TAG = "settingsActivityTitle"

interface OnFragmentPreferenceChanged {
    fun onFragmentPreferenceChanged(key: String, value: Int)
    fun onFragmentPreferenceChanged(key: String, value: Boolean)
    fun onFragmentPreferenceChanged(key: String, value: String)
}

class CollarSettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    OnFragmentPreferenceChanged {

    var device: BluetoothDevice? = null
    var connectedGatt: BluetoothGatt? = null
    var ledService: BluetoothGattService? = null
    var currentEffectCharacteristic: BluetoothGattCharacteristic? = null
    var currentEffectId: UUID? = null
    var currentEffectSettings: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        device = intent?.extras?.getParcelable("collarDevice")
        val name = intent?.extras?.getString("collarName")

        if (savedInstanceState == null) {
            showLoadingMenu()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
//                setTitle(name + " Settings")
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(name + " Settings")

        device?.connectGatt(this, false, mBluetoothGattCallback, TRANSPORT_LE)

//        createPreferenceListener();
    }

    private fun showLoadingMenu() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, CollarLoadingFragment())
            .commit()
    }

    private fun loadPreferenceMenu(subPreferences: Int?, effectSettings: ByteArray?) {
        val bundle = Bundle()
        subPreferences?.let { bundle.putInt("subPreferences", it) }
        effectSettings?.let { bundle.putByteArray("effectSettings", it) }
        val fragInfo = CollarEffectsFragment()
        fragInfo.arguments = bundle

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, fragInfo)
            .commit()
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
            connectedGatt?.close()
            connectedGatt = null
            finish()
            return true
        }
    }

    private val mBluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.i("LOGGER", "BLE connection state: $newState")

            if (status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // We successfully connected, proceed with service discovery
                    connectedGatt = gatt
                    gatt?.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // We successfully disconnected on our own request
                    connectedGatt = null
                    gatt?.close();
                } else {
                    // We're CONNECTING or DISCONNECTING, ignore for now
                }
            } else {
                connectedGatt = null
                gatt?.close();
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            ledService = gatt?.getService(BLEConstants.LEDService)
            val ledTypeCharacteristic = ledService?.getCharacteristic(BLEConstants.LEDTypeCharacteristic)
//            val stat = ledColorCharacteristic?.setValue(0xAAAAAAAA.toInt(), FORMAT_SINT32, 0)
//            gatt?.writeCharacteristic(ledColorCharacteristic)
//            Log.i("LOGGER", "Col stat: $stat")
            gatt?.readCharacteristic(ledTypeCharacteristic)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status != GATT_SUCCESS) {
                return
            }
            when (characteristic?.uuid) {
                BLEConstants.LEDTypeCharacteristic -> {
//                val currentColor = characteristic?.getIntValue(FORMAT_SINT32, 0) ?: 0
//                val man = supportFragmentManager.fragments[0] as PreferenceFragmentCompat
//                man.preferenceManager.sharedPreferences.edit().putInt("effect_color", currentColor)

                    val currentType = characteristic?.getIntValue(FORMAT_SINT32, 0) ?: 0
                    when (currentType) {
                        0 ->
                            gatt?.readCharacteristic(ledService?.getCharacteristic(BLEConstants.LEDBlinkSettingsCharacteristic))
                    }

                    // TODO adjust drop down thingy!!!!

                    runOnUiThread {
                        loadPreferenceMenu(null, null)
                    }
//                val ledBlinkSettingsCharacteristic = ledService?.getCharacteristic(BLEConstants.LEDBlinkSettingsCharacteristic)
                }

                BLEConstants.LEDBlinkSettingsCharacteristic -> {
                    val value = characteristic?.value!!
                    runOnUiThread {
                        currentEffectCharacteristic = characteristic
                        currentEffectId = characteristic?.uuid
                        currentEffectSettings = value
                        loadPreferenceMenu(R.xml.collar_effects_preferences_blink, value)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
//            if (status != GATT_SUCCESS) {
//                throw Exception("crud")
//            }
        }
    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        val id: Int = item.getItemId()
//        if (id == android.R.id.home) {
//            finish() // or go to another activity
//            return true
//        }
//        return super.onOptionsItemSelected(item)
//    }

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
        if (connectedGatt != null &&
            currentEffectSettings != null
        ) {
            val isColor = Effects.isColor(key)
            if (isColor) {
                val convertedValue = ColorUtilities.BGR2RGB(value);
                val offset = Effects.offsets[currentEffectId]!![key]!!
                currentEffectSettings!![offset] = (convertedValue and 0xFF).toByte()
                currentEffectSettings!![offset + 1] = (convertedValue shr 8 and 0xFF).toByte()
                currentEffectSettings!![offset + 2] = (convertedValue shr 16 and 0xFF).toByte()
            } else {
                val offset = Effects.offsets[currentEffectId]!![key]!!
                currentEffectSettings!![offset] = value.toByte()
            }

            currentEffectCharacteristic?.value = currentEffectSettings
            connectedGatt?.writeCharacteristic(currentEffectCharacteristic)
        }
    }

    override fun onFragmentPreferenceChanged(key: String, value: String) {
        var typeIndex = -1
        when (key) {
            "led_type" -> {
                when (value) {
                    "blink" -> typeIndex = 0
                    "wave" -> typeIndex = 1
                }
            }
        }

        // Update LED type on device
        val ledTypeCharacteristic = ledService?.getCharacteristic(BLEConstants.LEDTypeCharacteristic)
        ledTypeCharacteristic?.setValue(typeIndex, FORMAT_SINT32, 0)
        connectedGatt?.writeCharacteristic(ledTypeCharacteristic)
    }

    override fun onFragmentPreferenceChanged(key: String, value: Boolean) {
        if (connectedGatt != null) {
            val offset = Effects.offsets[currentEffectId]!![key]!!
            currentEffectSettings!![offset] = if (value) 1 else 0
            currentEffectCharacteristic?.value = currentEffectSettings
            connectedGatt?.writeCharacteristic(currentEffectCharacteristic)
        }
    }

//    companion object {
//        fun getLayoutIdFromTypeName(ledType: String?): Int {
//            var subPreferencesId = R.xml.collar_effects_preferences_blink
//            when (ledType) {
//                "wave" -> subPreferencesId = R.xml.collar_effects_preferences_wave
//            }
//            return subPreferencesId
//        }
//    }
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

        val subPreferences = arguments?.getInt("subPreferences") ?: 0
        if (subPreferences != 0) {
            val effectSettings = arguments?.getByteArray("effectSettings")
            if (effectSettings != null) {
                when (subPreferences) {
                    R.xml.collar_effects_preferences_blink -> {
                        listenerUpdateEnabled = false
                        val preferences = preferenceManager.sharedPreferences.edit();
                        val offsets = Effects.offsets[BLEConstants.LEDBlinkSettingsCharacteristic]!!
                        val colorOffset = offsets["effect_color"]!!
                        preferences.putInt(
                            "effect_color",
                            ColorUtilities.BytesRGB2BGR(
                                effectSettings[colorOffset],
                                effectSettings[colorOffset + 1],
                                effectSettings[colorOffset + 2]
                            )
                        )

                        preferences.putInt("effect_speed", effectSettings[offsets["effect_speed"]!!].toInt())
                        preferences.putBoolean("effect_fade_in", effectSettings[offsets["effect_fade_in"]!!].toInt() != 0)
                        preferences.putBoolean("effect_fade_out", effectSettings[offsets["effect_fade_out"]!!].toInt() != 0)
                        preferences.putBoolean("effect_rainbow", effectSettings[offsets["effect_rainbow"]!!].toInt() != 0)
                        preferences.putInt("effect_rainbow_speed", effectSettings[offsets["effect_rainbow_speed"]!!].toInt())
                        preferences.apply()
                        listenerUpdateEnabled = true
                    }
                }
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