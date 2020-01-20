package com.kristjanskutta.gizmoled

import android.content.Context
import java.util.*

object Effects {
//    val offsets = hashMapOf(
//        BLEConstants.LEDBlinkSettingsCharacteristic to hashMapOf(
//            "effect_color" to 0,
//            "effect_speed" to 3,
//            "effect_fade_in" to 4,
//            "effect_fade_out" to 5,
//            "effect_rainbow" to 6,
//            "effect_rainbow_speed" to 7
//        ),
//        BLEConstants.LEDWaveSettingsCharacteristic to hashMapOf(
//            "effect_color_1" to 0,
//            "effect_color_2" to 3,
//            "effect_color_3" to 6,
//            "effect_number_colors" to 9,
//            "effect_speed" to 10,
//            "effect_length" to 11,
//            "effect_rainbow" to 12,
//            "effect_rainbow_speed" to 13
//        ),
//        BLEConstants.LEDColorWheelSettingsCharacteristic to hashMapOf(
//            "effect_brightness" to 0,
//            "effect_speed" to 1,
//            "effect_length" to 2
//        ),
//        BLEConstants.LEDVisualizerSettingsCharacteristic to hashMapOf(
//            "effect_color_1" to 0,
//            "effect_color_2" to 3,
//            "effect_brightness" to 6,
//            "effect_decay" to 7,
//            "effect_speed" to 8,
//            "effect_rainbow" to 9,
//            "effect_rainbow_speed" to 10
//        ),
//        BLEConstants.LEDVisorSettingsCharacteristic to hashMapOf(
//            "effect_color_1" to 0,
//            "effect_color_2" to 3,
//            "effect_number_colors" to 6,
//            "effect_speed" to 7,
//            "effect_fade_in" to 8,
//            "effect_fade_out" to 9,
//            "effect_rainbow" to 10,
//            "effect_rainbow_speed" to 11
//        ),
//        BLEConstants.LEDPoliceSettingsCharacteristic to hashMapOf(
//            "effect_color_1" to 0,
//            "effect_color_2" to 3,
//            "effect_speed" to 6
//        ),
//        BLEConstants.LEDChristmasSettingsCharacteristic to hashMapOf(
//            "effect_color_1" to 0,
//            "effect_color_2" to 3,
//            "effect_color_3" to 6,
//            "effect_speed" to 9,
//            "effect_decay" to 10
//        )
//    )
//
//    fun isColor(key: String?): Boolean {
//        val colorNames =
//            hashMapOf(
//                "effect_color" to true,
//                "effect_color_1" to true,
//                "effect_color_2" to true,
//                "effect_color_3" to true,
//                "effect_color_primary" to true,
//                "effect_color_secondary" to true
//            )
//        return colorNames[key] ?: false
//    }
//
//    fun isBoolean(key: String?): Boolean {
//        val colorNames =
//            hashMapOf(
//                "effect_fade_in" to true,
//                "effect_fade_out" to true,
//                "effect_rainbow" to true
//            )
//        return colorNames[key] ?: false
//    }
//
//    val effectIndexToString = hashMapOf(
//        0 to "blink",
//        1 to "wave",
//        2 to "wheel",
//        3 to "visualizer",
//        4 to "visor",
//        5 to "police",
//        6 to "christmas"
//    )
//
//    val effectStringToIndex = hashMapOf(
//        "blink" to 0,
//        "wave" to 1,
//        "wheel" to 2,
//        "visualizer" to 3,
//        "visor" to 4,
//        "police" to 5,
//        "christmas" to 6
//    )
//
//    val effectUUIDToResource = hashMapOf(
//        BLEConstants.LEDBlinkSettingsCharacteristic to R.xml.collar_effects_preferences_blink,
//        BLEConstants.LEDWaveSettingsCharacteristic to R.xml.collar_effects_preferences_wave,
//        BLEConstants.LEDColorWheelSettingsCharacteristic to R.xml.collar_effects_preferences_wheel,
//        BLEConstants.LEDVisualizerSettingsCharacteristic to R.xml.collar_effects_preferences_visualizer,
//        BLEConstants.LEDVisorSettingsCharacteristic to R.xml.collar_effects_preferences_visor,
//        BLEConstants.LEDPoliceSettingsCharacteristic to R.xml.collar_effects_preferences_police,
//        BLEConstants.LEDChristmasSettingsCharacteristic to R.xml.collar_effects_preferences_christmas
//    )
//
//    val effectUUIDToIndex = hashMapOf(
//        BLEConstants.LEDBlinkSettingsCharacteristic to 0,
//        BLEConstants.LEDWaveSettingsCharacteristic to 1,
//        BLEConstants.LEDColorWheelSettingsCharacteristic to 2,
//        BLEConstants.LEDVisualizerSettingsCharacteristic to 3,
//        BLEConstants.LEDVisorSettingsCharacteristic to 4,
//        BLEConstants.LEDPoliceSettingsCharacteristic to 5,
//        BLEConstants.LEDChristmasSettingsCharacteristic to 6
//    )
//
//    val effectResourceToUUID = hashMapOf(
//        R.xml.collar_effects_preferences_blink to BLEConstants.LEDBlinkSettingsCharacteristic,
//        R.xml.collar_effects_preferences_wave to BLEConstants.LEDWaveSettingsCharacteristic,
//        R.xml.collar_effects_preferences_wheel to BLEConstants.LEDColorWheelSettingsCharacteristic,
//        R.xml.collar_effects_preferences_visualizer to BLEConstants.LEDVisualizerSettingsCharacteristic,
//        R.xml.collar_effects_preferences_visor to BLEConstants.LEDVisorSettingsCharacteristic,
//        R.xml.collar_effects_preferences_police to BLEConstants.LEDPoliceSettingsCharacteristic,
//        R.xml.collar_effects_preferences_christmas to BLEConstants.LEDChristmasSettingsCharacteristic
//    )
//
//    val effectIndexToUUID = hashMapOf(
//        0 to BLEConstants.LEDBlinkSettingsCharacteristic,
//        1 to BLEConstants.LEDWaveSettingsCharacteristic,
//        2 to BLEConstants.LEDColorWheelSettingsCharacteristic,
//        3 to BLEConstants.LEDVisualizerSettingsCharacteristic,
//        4 to BLEConstants.LEDVisorSettingsCharacteristic,
//        5 to BLEConstants.LEDPoliceSettingsCharacteristic,
//        6 to BLEConstants.LEDChristmasSettingsCharacteristic
//    )

    fun getEffectCharacterisUUID(effectId: Int): UUID {
        return UUID.fromString(
            BLEConstants.strLEDEffectSettingsCharacteristicBase +
                    effectId.toString().padStart(2, '0'))
    }

    private val effectIdToName = hashMapOf(
        0 to R.string.effect_blink,
        1 to R.string.effect_wheel,
        10 to R.string.effect_visualizer
    )

    fun getEffectTitle(effectNameId: UByte, context: Context?): String {
        return context?.getString(effectIdToName[effectNameId.toInt()]!!) ?: "err"
    }

    private val effectIdToCategoryTitle = hashMapOf(
        0 to R.string.category_blink_settings,
        1 to R.string.category_wheel_settings,
        10 to R.string.category_visualizer_settings
    )

    fun getEffectSettingCategoryTitle(effectNameId: UByte, context: Context?): String {
        return context?.getString(effectIdToCategoryTitle[effectNameId.toInt()]!!) ?: "err"
    }

    private val effectSettingNames = hashMapOf(
        0 to R.string.preference_color,
        1 to R.string.preference_color_1,
        2 to R.string.preference_color_2,
        3 to R.string.preference_color_3,
        4 to R.string.preference_num_colors,
        5 to R.string.preference_speed,
        6 to R.string.preference_brightness,
        7 to R.string.preference_length,
        8 to R.string.preference_decay,
        9 to R.string.preference_rainbow,
        10 to R.string.preference_rainbow_cycle_speed,
        11 to R.string.preference_rainbow_cycle_length,
        12 to R.string.preference_fade_in,
        13 to R.string.preference_fade_out
    )

    fun getEffectSettingName(settingNameId: UByte, context: Context?): String {
        return context?.getString(effectSettingNames[settingNameId.toInt()]!!) ?: "err"
    }

    val visualizerEffectNames = hashSetOf(10)
}