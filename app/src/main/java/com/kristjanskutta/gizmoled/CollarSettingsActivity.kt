package com.kristjanskutta.gizmoled

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import android.app.*
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.preference.*
import com.google.android.material.textfield.TextInputEditText
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import org.xmlpull.v1.XmlPullParser


private const val TITLE_TAG = "settingsActivityTitle"

interface OnFragmentPreferenceChanged {
    fun onFragmentPreferenceChanged(key: String, value: Int)
    fun onFragmentPreferenceChanged(key: String, value: String)
    fun onFragmentPreferenceChanged(key: String, value: Boolean)
}

class CollarSettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    OnFragmentPreferenceChanged {

    companion object {
        var lastResetCall = 0
    }

    private val PERMISSION_REQUEST_RECORD_AUDIO = 6001

    var device: BluetoothDevice? = null
    var ledService: BluetoothGattService? = null
    var currentEffectCharacteristic: BluetoothGattCharacteristic? = null
    var currentEffectIndex = 0
    var currentEffectSettings: ByteArray? = null
    var currentEffectList: IntArray? = null

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

        updateTitle(name)
//        createPreferenceListener()
    }

    fun updateTitle(name: String?) {
        title = "$name Settings"
    }

    override fun onResume() {
        super.onResume()

        showLoadingMenu()
        device?.connectGatt(this, false, gattWrapper, TRANSPORT_LE)
    }

    override fun onPause() {
        gattWrapper.close()
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_collar, menu)
        return true
    }

    private fun callRemoteFunction(index: Byte, args: ByteArray?) {
        var newValue = byteArrayOf((++lastResetCall).toByte(), index)
        if (args != null) {
            newValue += args!!
        }

        val fnResetCharacteristic =
            ledService?.getCharacteristic(BLEConstants.FnCallCharacteristic)
        fnResetCharacteristic?.value = newValue
        gattWrapper.wrappedWriteCharacteristic(fnResetCharacteristic)

        if (lastResetCall > 255) {
            lastResetCall = 1
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                callRemoteFunction(0, byteArrayOf(currentEffectIndex.toByte()))

                loadPreferenceMenu(currentEffectIndex)
                gattWrapper.wrappedReadCharacteristic(currentEffectCharacteristic)
                true
            }
            R.id.action_rename -> {
                showRenameDeviceDialog()
                true
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

    private fun loadPreferenceMenu(effectIndex: Int, effectList: IntArray? = null) {
        if (effectList != null) {
            currentEffectList = effectList
        }

        val bundle = Bundle()
        bundle.putInt("effectIndex", effectIndex)
        bundle.putIntArray("effectList", currentEffectList)
        val fragInfo = CollarEffectsFragment()
        fragInfo.arguments = bundle

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, fragInfo)
            .commit()
    }

    private fun loadPreferenceMenu(effectIndex: Int, effectSettings: ByteArray) {
        val bundle = Bundle()
        bundle.putInt("effectIndex", effectIndex)
        bundle.putIntArray("effectList", currentEffectList)
        bundle.putByteArray("effectSettings", effectSettings)
        val fragInfo = CollarEffectsFragment()
        fragInfo.arguments = bundle

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, fragInfo)
            .commit()
    }

    private fun updateVisualizerStreamEnabled() {
        val shouldEnableStream = currentEffectList != null &&
                currentEffectIndex < currentEffectList!!.size &&
                Effects.visualizerEffectNames.contains(
                    currentEffectList!!.elementAt(
                        currentEffectIndex
                    )
                )

        if (shouldEnableStream && BLEService.needsPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, BLEService.getPermissionList(), PERMISSION_REQUEST_RECORD_AUDIO
            )
        } else if (shouldEnableStream) {
            Intent(this, BLEService::class.java).also { intent ->
                intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_ADD_KNOWN_DEVICE)
                intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
                startService(intent)
            }
        } else {
            Intent(this, BLEService::class.java).also { intent ->
                intent.putExtra(
                    BLEService.INTENT_KEY_COMMAND,
                    BLEService.COMMAND_REMOVE_KNOWN_DEVICE
                )
                intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
                startService(intent)
            }
        }
//        else if (!shouldEnableStream) {
//            Intent(this, BLEService::class.java).also { intent ->
//                intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_UNREGISTER_DEVICE)
//                intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
//                startService(intent)
//            }
//        }

//        if (shouldEnableStream) {
//            if (BLEService.needsPermissions(this)) {
//                ActivityCompat.requestPermissions(
//                    this, BLEService.getPermissionList(), PERMISSION_REQUEST_RECORD_AUDIO
//                )
//            } else {
//                // Enable stream
//                Intent(this, BLEService::class.java).also { intent ->
//                    intent.putExtra(
//                        BLEService.INTENT_KEY_COMMAND,
//                        BLEService.COMMAND_REGISTER_DEVICE
//                    )
//                    intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
//                    startService(intent)
//                }
//            }
//        } else {
//            // Disable stream
//            Intent(this, BLEService::class.java).also { intent ->
//                intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_UNREGISTER_DEVICE)
//                intent.putExtra(BLEService.INTENT_KEY_DEVICE, device)
//                startService(intent)
//            }
//        }
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

    var gattWrapper: BluetoothGattWrapper = object : BluetoothGattWrapper(true) {
        override fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            ledService = gatt!!.getService(BLEConstants.LEDService)
            val ledTypeCharacteristic =
                ledService!!.getCharacteristic(BLEConstants.LEDTypeCharacteristic)
            wrappedReadCharacteristic(ledTypeCharacteristic)
        }

        override fun onWrappedDisconnected(gatt: BluetoothGatt?) {
            runOnUiThread {
                Toast.makeText(
                    this@CollarSettingsActivity,
                    getString(R.string.device_lost),
                    Toast.LENGTH_SHORT
                ).show()

                val upIntent = Intent(applicationContext, MainActivity::class.java)
                if (NavUtils.shouldUpRecreateTask(this@CollarSettingsActivity, upIntent)) {
                    TaskStackBuilder.create(this@CollarSettingsActivity)
                        .addNextIntentWithParentStack(upIntent).startActivities()
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
                    // Fetch all available effects into drop down
                    // Set currently selected effect
                    // Reload menu with selected effect properties
                    val typeInfo = characteristic?.value!!
                    var selectedEffect = 0
                    //val effectVisualizerFlags = typeInfo[2].toInt()
                    val numEffects = typeInfo[2].toInt()
                    var effectList = IntArray(numEffects)

//                    visualizerIndices.clear()
                    for (i in 0 until numEffects) {
                        effectList[i] = typeInfo[3 + i].toInt()

                        if (i == typeInfo[0].toInt()) {
                            selectedEffect = i
                        }
                    }

                    runOnUiThread {
                        wrappedReadCharacteristic(
                            ledService?.getCharacteristic(
                                Effects.getEffectIndexCharacteristicUUID(
                                    selectedEffect
                                )
                            )
                        )

                        currentEffectIndex = selectedEffect
                        loadPreferenceMenu(selectedEffect, effectList)
                    }
                }
            }

            if (characteristic?.uuid.toString()
                    .startsWith(BLEConstants.strLEDEffectSettingsCharacteristicBase)
            ) {
                val value = characteristic?.value!!
                runOnUiThread {
                    currentEffectCharacteristic = characteristic
//                    Log.i("asdf", "Set current characteristic: ${currentEffectCharacteristic?.uuid.toString()}")
//                    currentEffectId = characteristic?.uuid
                    currentEffectSettings = value
                    loadPreferenceMenu(
                        currentEffectIndex,
                        value
                    )
                    updateVisualizerStreamEnabled()
                }
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
        if (currentEffectSettings != null
        ) {
            val offsetRegex = "prefSetting(\\d+)".toRegex()
            val offset = offsetRegex.find(key)!!.groupValues[1].toInt()
            val typeRegex = "prefType(\\d+)".toRegex()
            val type = typeRegex.find(key)!!.groupValues[1].toInt()
            val isColor = type == 1
            if (isColor) {
                val convertedValue = ColorUtilities.BGR2RGB(value)
                if (offset + 2 >= currentEffectSettings!!.size) {
                    throw ArrayIndexOutOfBoundsException()
                }
                currentEffectSettings!![offset] = (convertedValue and 0xFF).toByte()
                currentEffectSettings!![offset + 1] = (convertedValue shr 8 and 0xFF).toByte()
                currentEffectSettings!![offset + 2] = (convertedValue shr 16 and 0xFF).toByte()
            } else {
                if (offset >= currentEffectSettings!!.size) {
                    throw ArrayIndexOutOfBoundsException()
                }
                currentEffectSettings!![offset] = value.toByte()
            }

            currentEffectCharacteristic?.value = currentEffectSettings
            gattWrapper.wrappedWriteCharacteristic(currentEffectCharacteristic)
        }
    }

    override fun onFragmentPreferenceChanged(key: String, value: String) {
        if (key == "led_type") {
            val effectIndex = value.toInt()
            if (currentEffectList == null ||
                effectIndex < 0 ||
                effectIndex >= currentEffectList!!.size ||
                effectIndex == currentEffectIndex
            ) {
                return
            }
            // Update LED type on device
            val ledTypeCharacteristic =
                ledService?.getCharacteristic(BLEConstants.LEDTypeCharacteristic)
            ledTypeCharacteristic?.setValue(effectIndex, FORMAT_UINT8, 0)
            gattWrapper.wrappedWriteCharacteristic(ledTypeCharacteristic)

            currentEffectIndex = effectIndex
            loadPreferenceMenu(effectIndex)

            // Load new effect data
            val newEffectCharacteristic =
                ledService?.getCharacteristic(Effects.getEffectIndexCharacteristicUUID(effectIndex))
            gattWrapper.wrappedReadCharacteristic(newEffectCharacteristic)
            return
        }
    }

    override fun onFragmentPreferenceChanged(key: String, value: Boolean) {
        val offsetRegex = "prefSetting(\\d+)".toRegex()
        val offset = offsetRegex.find(key)!!.groupValues[1].toInt()
        if (offset >= currentEffectSettings!!.size) {
            throw ArrayIndexOutOfBoundsException()
        }
        currentEffectSettings!![offset] = if (value) 1 else 0
        currentEffectCharacteristic?.value = currentEffectSettings
        gattWrapper.wrappedWriteCharacteristic(currentEffectCharacteristic)
    }

    fun showRenameDeviceDialog() {
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.dialog_title_rename_device))

        val view = layoutInflater.inflate(R.layout.dialog_rename_device, null)
        val deviceNameInput = view.findViewById(R.id.deviceName) as TextInputEditText

        deviceNameInput.setText(device?.name)

        builder.setView(view)

        // set up the ok button
        builder.setPositiveButton(android.R.string.ok) { dialog, p1 ->
            val inputText = deviceNameInput.text
//            var isValid = true
//            if (inputText?.isBlank() != false) {
//                deviceNameInput.error = getString(R.string.dialog_device_name_empty_validation)
//                isValid = false
//            }

//            if (isValid) {
            val newName = inputText.toString()
            updateTitle(newName)
            callRemoteFunction(1, newName.toByteArray())
            dialog.dismiss()
//            }
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, p1 ->
            dialog.cancel()
        }

        builder.show()
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
                    listener?.onFragmentPreferenceChanged(key, value)
                    //success = true
                } catch (e: ClassCastException) {
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.collar_effects_preferences, rootKey)

        val currentEffect = arguments?.getInt("effectIndex") ?: -1
        if (currentEffect != -1) {
            listenerUpdateEnabled = false
            preferenceManager.sharedPreferences.edit()
                .putString("led_type", currentEffect.toString()).apply()
            listenerUpdateEnabled = true
        }

        val effectList = arguments?.getIntArray("effectList")
        if (effectList != null && currentEffect != -1) {
            val effectListPreference = preferenceScreen.getPreference(0) as ListPreference
            val strList = IntArray(effectList.size) { i -> i }.map { e -> e.toString() }
                .toTypedArray()//effectList.map { e -> e.toString() }.toTypedArray()
            effectListPreference.entryValues = strList

            val labels = effectList.map { e ->
                Effects.getEffectTitle(
                    e.toUByte(),
                    preferenceScreen.context
                )
            }.toTypedArray()
            effectListPreference.entries = labels
            effectListPreference.value = currentEffect.toString()
        }

        val effectSettings = arguments?.getByteArray("effectSettings")
        if (effectSettings != null && effectList != null && currentEffect != -1) {
            val effectBaseKey = "prefEffect$currentEffect"
            listenerUpdateEnabled = false
            val preferences = preferenceManager.sharedPreferences.edit()

            var itr = effectSettings.iterator()
            var settingIndex = 0
            var valueOffset = 0

            val parser: XmlPullParser = resources.getXml(R.xml.attr_pref_base)
            parser.next()
            parser.nextTag()

            val effectNameId = effectList[currentEffect]
            val category = PreferenceCategory(preferenceScreen.context)
            category.isIconSpaceReserved = false
            category.title = Effects.getEffectSettingCategoryTitle(
                effectNameId.toUByte(),
                preferenceScreen.context
            )
            preferenceScreen.addPreference(category)

            while (itr.hasNext()) {
                val type = itr.next().toInt()
                val dataOffset = valueOffset + 2
                val key = "${effectBaseKey}prefSetting${dataOffset}prefType${type}"
                ++settingIndex

                when (type) {
                    1 -> { // Color
                        val name = itr.next().toUByte()
                        val r = itr.next()
                        val g = itr.next()
                        val b = itr.next()
                        valueOffset += 5

                        val attr = android.util.Xml.asAttributeSet(parser)
                        val preference =
                            ColorPreferenceCompat(preferenceScreen.context, attr)
                        preference.title = Effects.getEffectSettingName(name, context)
                        preference.key = key
                        preference.setDefaultValue("0xFFFFFFFF")
                        preferences.putInt(key, ColorUtilities.BytesRGB2BGR(r, g, b))
                        preferences.apply()
                        category.addPreference(preference)
                    }
                    2 -> { // Slider
                        val name = itr.next().toUByte()
                        val value = itr.next().toUByte().toInt()
                        val vMin = itr.next().toUByte().toInt()
                        val vMax = itr.next().toUByte().toInt()
                        valueOffset += 5

                        val attr = android.util.Xml.asAttributeSet(parser)
                        val preference =
                            SeekBarPreference(preferenceScreen.context, attr)
                        preference.title = Effects.getEffectSettingName(name, context)
                        preference.key = key
                        preference.setDefaultValue("1")
                        preference.min = vMin
                        preference.max = vMax
                        preference.showSeekBarValue = true
                        preferences.putInt(key, value)
                        preferences.apply()
                        category.addPreference(preference)
                    }
                    3 -> { // Checkbox
                        val name = itr.next().toUByte()
                        val value = itr.next().toInt()
                        valueOffset += 3

                        val attr = android.util.Xml.asAttributeSet(parser)
                        val preference =
                            SwitchPreference(preferenceScreen.context, attr)
                        preference.title = Effects.getEffectSettingName(name, context)
                        preference.key = key
                        preference.setDefaultValue(false)
                        preferences.putBoolean(key, value != 0)
                        preferences.apply()
                        category.addPreference(preference)
                    }
                }

                if (!itr.hasNext()) {
                    break
                }
            }

            listenerUpdateEnabled = true
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