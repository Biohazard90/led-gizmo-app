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
        )
    )

    fun isColor(key: String?): Boolean {
        val colorNames =
            hashMapOf(
                "effect_color" to true
            )
        return colorNames[key] ?: false
    }
}