package com.kristjanskutta.doggylight

import android.bluetooth.BluetoothDevice

class Collar(device: BluetoothDevice?, name: String, connected: Boolean) {
    var device = device
    var name = name
    val connected = connected
}
