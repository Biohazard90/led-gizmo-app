package com.kristjanskutta.gizmoled

import java.util.*

object BLEConstants {
    val LEDService = UUID.fromString("e8942ca1-eef7-4a95-afeb-e3d07e8af52e")
    val LEDServiceV = UUID.fromString("e8942ca1-eef7-4a95-afeb-e3d07e8af52d")
    val VisualizerStatus = UUID.fromString("e8942ca1-eef7-4a95-afeb-e3d07e8af52d")

    // Bidirectional
    val LEDTypeCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfb00")
    val strLEDEffectSettingsCharacteristicBase = "e8942ca1-d9e7-4c45-b96c-10cf850bfa"

//    val LEDBlinkSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa01")
//    val LEDWaveSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa02")
//    val LEDColorWheelSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa03")
//    val LEDVisualizerSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa04")
//    val LEDVisorSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa05")
//    val LEDPoliceSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa06")
//    val LEDChristmasSettingsCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-10cf850bfa07")

    // Upstream
    val AudioDataCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-20cf850bfa00")
    val FnResetCharacteristic = UUID.fromString("e8942ca1-d9e7-4c45-b96c-20cf850bfa01")
}