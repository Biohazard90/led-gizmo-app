package com.kristjanskutta.doggylight

import android.bluetooth.BluetoothDevice

class Collar {
    constructor(device: BluetoothDevice?, name: String) {
        this.device = device
        this.name = name
    }

    var device: BluetoothDevice? = null
    var name: String = ""
}