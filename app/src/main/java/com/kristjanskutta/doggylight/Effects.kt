package com.kristjanskutta.doggylight

object Effects {
    val offsets = hashMapOf(
        BLEConstants.LEDBlinkSettingsCharacteristic to hashMapOf(
            "effect_color" to 0,
            "effect_speed" to 3,
            "effect_fade_in" to 4,
            "effect_fade_out" to 5,
            "effect_rainbow" to 6,
            "effect_rainbow_speed" to 7
        ),
        BLEConstants.LEDWaveSettingsCharacteristic to hashMapOf(
            "effect_color_1" to 0,
            "effect_color_2" to 3,
            "effect_color_3" to 6,
            "effect_number_colors" to 9,
            "effect_speed" to 10,
            "effect_length" to 11,
            "effect_rainbow" to 12,
            "effect_rainbow_speed" to 13
        ),
        BLEConstants.LEDColorWheelSettingsCharacteristic to hashMapOf(
            "effect_brightness" to 0,
            "effect_speed" to 1,
            "effect_length" to 2
        ),
        BLEConstants.LEDVisualizerSettingsCharacteristic to hashMapOf(
            "effect_brightness" to 0,
            "effect_decay" to 1
        )
    )

    fun isColor(key: String?): Boolean {
        val colorNames =
            hashMapOf(
                "effect_color" to true,
                "effect_color_1" to true,
                "effect_color_2" to true,
                "effect_color_3" to true,
                "effect_color_primary" to true,
                "effect_color_secondary" to true
            )
        return colorNames[key] ?: false
    }

    fun isBoolean(key: String?): Boolean {
        val colorNames =
            hashMapOf(
                "effect_fade_in" to true,
                "effect_fade_out" to true,
                "effect_rainbow" to true
            )
        return colorNames[key] ?: false
    }

    val effectIndexToString = hashMapOf(
        0 to "blink",
        1 to "wave",
        2 to "wheel",
        3 to "visualizer"
    )

    val effectStringToIndex = hashMapOf(
        "blink" to 0,
        "wave" to 1,
        "wheel" to 2,
        "visualizer" to 3
    )

    val effectUUIDToResource = hashMapOf(
        BLEConstants.LEDBlinkSettingsCharacteristic to R.xml.collar_effects_preferences_blink,
        BLEConstants.LEDWaveSettingsCharacteristic to R.xml.collar_effects_preferences_wave,
        BLEConstants.LEDColorWheelSettingsCharacteristic to R.xml.collar_effects_preferences_color_wheel,
        BLEConstants.LEDVisualizerSettingsCharacteristic to R.xml.collar_effects_preferences_color_visualizer
    )

    val effectResourceToUUID = hashMapOf(
        R.xml.collar_effects_preferences_blink to BLEConstants.LEDBlinkSettingsCharacteristic,
        R.xml.collar_effects_preferences_wave to BLEConstants.LEDWaveSettingsCharacteristic,
        R.xml.collar_effects_preferences_color_wheel to BLEConstants.LEDColorWheelSettingsCharacteristic,
        R.xml.collar_effects_preferences_color_visualizer to BLEConstants.LEDVisualizerSettingsCharacteristic
    )

    val effectIndexToUUID = hashMapOf(
        0 to BLEConstants.LEDBlinkSettingsCharacteristic,
        1 to BLEConstants.LEDWaveSettingsCharacteristic,
        2 to BLEConstants.LEDColorWheelSettingsCharacteristic,
        3 to BLEConstants.LEDVisualizerSettingsCharacteristic
    )
}