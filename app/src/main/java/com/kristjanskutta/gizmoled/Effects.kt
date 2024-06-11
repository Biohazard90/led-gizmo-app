package com.kristjanskutta.gizmoled

import android.content.Context
import java.util.*

object Effects {

    fun getEffectIndexCharacteristicUUID(effectIndex: Int): UUID {
        return UUID.fromString(
            BLEConstants.strLEDEffectSettingsCharacteristicBase +
                    effectIndex.toString().padStart(2, '0'))
    }

    private val effectIdToName = hashMapOf(
        0 to R.string.effect_blink,
        1 to R.string.effect_wheel,
        2 to R.string.effect_opaque,
        3 to R.string.effect_gradient,
        4 to R.string.effect_test,
        5 to R.string.effect_visualizer,
        6 to R.string.effect_pulse,
        7 to R.string.effect_sparkle,
        8 to R.string.effect_christmas,
        9 to R.string.effect_accel,
        10 to R.string.effect_noise,
        11 to R.string.effect_empty,
        12 to R.string.effect_waves,
        13 to R.string.effect_drops,
        14 to R.string.effect_meteor,
        15 to R.string.effect_wipe,
        16 to R.string.effect_fire,
        17 to R.string.effect_ambient
    )

    fun getEffectTitle(effectNameId: UByte, context: Context?): String {
        return context?.getString(effectIdToName[effectNameId.toInt()]!!) ?: "err"
    }

    private val effectIdToCategoryTitle = hashMapOf(
        0 to R.string.category_blink_settings,
        1 to R.string.category_wheel_settings,
        2 to R.string.category_opaque_settings,
        3 to R.string.category_gradient_settings,
        4 to R.string.category_test_settings,
        5 to R.string.category_visualizer_settings,
        6 to R.string.category_pulse_settings,
        7 to R.string.category_sparkle_settings,
        8 to R.string.category_christmas_settings,
        9 to R.string.category_accel_settings,
        10 to R.string.category_noise_settings,
        11 to R.string.category_empty_settings,
        12 to R.string.category_waves_settings,
        13 to R.string.category_drops_settings,
        14 to R.string.category_meteor_settings,
        15 to R.string.category_wipe_settings,
        16 to R.string.category_fire_settings,
        17 to R.string.category_ambient_settings
    )

    fun getEffectSettingCategoryTitle(effectNameId: UByte, context: Context?): String {
        return context?.getString(effectIdToCategoryTitle[effectNameId.toInt()]!!) ?: "err"
    }

    val visualizerEffectNames = hashSetOf(5)

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
        13 to R.string.preference_fade_out,
        14 to R.string.preference_angle,
        15 to R.string.preference_sensitivity,
        16 to R.string.preference_background_color,
        17 to R.string.preference_rainbow_offset,
        18 to R.string.preference_background_brightness,
        19 to R.string.preference_sparkle_brightness,
        20 to R.string.preference_sparkle_amount,
        21 to R.string.preference_sparkle_speed,
    )

    fun getEffectSettingName(settingNameId: UByte, context: Context?): String {
        return context?.getString(effectSettingNames[settingNameId.toInt()]!!) ?: "err"
    }
}