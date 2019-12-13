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
        ),
        BLEConstants.LEDVisorSettingsCharacteristic to hashMapOf(
            "effect_color_1" to 0,
            "effect_color_2" to 3,
            "effect_number_colors" to 6,
            "effect_speed" to 7,
            "effect_fade_in" to 8,
            "effect_fade_out" to 9,
            "effect_rainbow" to 10,
            "effect_rainbow_speed" to 11
        ),
        BLEConstants.LEDPoliceSettingsCharacteristic to hashMapOf(
            "effect_color_1" to 0,
            "effect_color_2" to 3,
            "effect_speed" to 6
        ),
        BLEConstants.LEDChristmasSettingsCharacteristic to hashMapOf(
            "effect_color_1" to 0,
            "effect_color_2" to 3,
            "effect_color_3" to 6,
            "effect_speed" to 9,
            "effect_decay" to 10
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
        3 to "visualizer",
        4 to "visor",
        5 to "police",
        6 to "christmas"
    )

    val effectStringToIndex = hashMapOf(
        "blink" to 0,
        "wave" to 1,
        "wheel" to 2,
        "visualizer" to 3,
        "visor" to 4,
        "police" to 5,
        "christmas" to 6
    )

    val effectUUIDToResource = hashMapOf(
        BLEConstants.LEDBlinkSettingsCharacteristic to R.xml.collar_effects_preferences_blink,
        BLEConstants.LEDWaveSettingsCharacteristic to R.xml.collar_effects_preferences_wave,
        BLEConstants.LEDColorWheelSettingsCharacteristic to R.xml.collar_effects_preferences_wheel,
        BLEConstants.LEDVisualizerSettingsCharacteristic to R.xml.collar_effects_preferences_visualizer,
        BLEConstants.LEDVisorSettingsCharacteristic to R.xml.collar_effects_preferences_visor,
        BLEConstants.LEDPoliceSettingsCharacteristic to R.xml.collar_effects_preferences_police,
        BLEConstants.LEDChristmasSettingsCharacteristic to R.xml.collar_effects_preferences_christmas
    )

    val effectUUIDToIndex = hashMapOf(
        BLEConstants.LEDBlinkSettingsCharacteristic to 0,
        BLEConstants.LEDWaveSettingsCharacteristic to 1,
        BLEConstants.LEDColorWheelSettingsCharacteristic to 2,
        BLEConstants.LEDVisualizerSettingsCharacteristic to 3,
        BLEConstants.LEDVisorSettingsCharacteristic to 4,
        BLEConstants.LEDPoliceSettingsCharacteristic to 5,
        BLEConstants.LEDChristmasSettingsCharacteristic to 6
    )

    val effectResourceToUUID = hashMapOf(
        R.xml.collar_effects_preferences_blink to BLEConstants.LEDBlinkSettingsCharacteristic,
        R.xml.collar_effects_preferences_wave to BLEConstants.LEDWaveSettingsCharacteristic,
        R.xml.collar_effects_preferences_wheel to BLEConstants.LEDColorWheelSettingsCharacteristic,
        R.xml.collar_effects_preferences_visualizer to BLEConstants.LEDVisualizerSettingsCharacteristic,
        R.xml.collar_effects_preferences_visor to BLEConstants.LEDVisorSettingsCharacteristic,
        R.xml.collar_effects_preferences_police to BLEConstants.LEDPoliceSettingsCharacteristic,
        R.xml.collar_effects_preferences_christmas to BLEConstants.LEDChristmasSettingsCharacteristic
    )

    val effectIndexToUUID = hashMapOf(
        0 to BLEConstants.LEDBlinkSettingsCharacteristic,
        1 to BLEConstants.LEDWaveSettingsCharacteristic,
        2 to BLEConstants.LEDColorWheelSettingsCharacteristic,
        3 to BLEConstants.LEDVisualizerSettingsCharacteristic,
        4 to BLEConstants.LEDVisorSettingsCharacteristic,
        5 to BLEConstants.LEDPoliceSettingsCharacteristic,
        6 to BLEConstants.LEDChristmasSettingsCharacteristic
    )
}