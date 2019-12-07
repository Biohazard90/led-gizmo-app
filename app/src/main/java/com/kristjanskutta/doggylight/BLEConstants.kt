package com.kristjanskutta.doggylight

import java.util.*

object BLEConstants {
    val LEDService = UUID.fromString("e8942ca1-eef7-4a95-afeb-e3d07e8af52e")
    val LEDTypeCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa00")
    val LEDBlinkSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa01")
}