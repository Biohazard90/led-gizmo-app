package com.kristjanskutta.doggylight

import java.util.*

object BLEConstants {
    val LEDService = UUID.fromString("e8942ca1-eef7-4a95-afeb-e3d07e8af52e")

    val LEDTypeCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa00")
    val LEDBlinkSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa01")
    val LEDWaveSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa02")
    val LEDColorWheelSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa03")
    val LEDVisualizerSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa04")
    val LEDVisorSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa05")

    val AudioDataCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-20cf850bfa00")
}