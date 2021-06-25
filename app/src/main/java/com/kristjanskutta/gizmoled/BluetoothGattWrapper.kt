package com.kristjanskutta.gizmoled

import android.bluetooth.*
import android.util.Log
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet

interface BLEWrapperInterface {
    fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {}
    fun onWrappedCharacteristicRead(characteristic: BluetoothGattCharacteristic?, status: Int) {}
    fun onWrappedDisconnected(gatt: BluetoothGatt?) {}
}

class BluetoothGattWrapperInstance {
    class Command(
        val type: Int = 0,
        val callback: BLEWrapperInterface,
        val characteristic: BluetoothGattCharacteristic
    ) {
        override fun equals(other: Any?): Boolean {
            return other is Command &&
                    type == other?.type && characteristic == other?.characteristic
        }
    }

    val queue: Queue<Command> = LinkedList<Command>()
    val lock: ReentrantLock = ReentrantLock()
    var busyCommand: Command? = null
//    var isQueueBusy = false
//    var callbacks = HashSet<BLEWrapperInterface>()
}

abstract class BluetoothGattWrapper(val shouldAutoDisconnect: Boolean) : BluetoothGattCallback(),
    BLEWrapperInterface {

    companion object {
        var deviceHelpers = HashMap<String, BluetoothGattWrapperInstance>()
    }

    @Volatile
    protected var connectedGatt: BluetoothGatt? = null

    private var sharedInstance: BluetoothGattWrapperInstance? = null

    private fun processQueue() {
        sharedInstance!!.lock.lock()
        if (!sharedInstance!!.queue.isEmpty() &&
            sharedInstance!!.busyCommand == null
        ) {
            val cmd = sharedInstance!!.queue.remove()
            sharedInstance!!.busyCommand = cmd
            sharedInstance!!.lock.unlock()

            when (cmd.type) {
                0 -> {
                    connectedGatt?.readCharacteristic(cmd.characteristic)
                }

                1 -> {
                    val writeStatus =
                        connectedGatt?.writeCharacteristic(cmd.characteristic) ?: false
                    if (!writeStatus) {
                        Log.i("asdf", "write failed")
                    }
                }
            }
        } else {
            sharedInstance!!.lock.unlock()
        }
    }

    private fun clearQueue() {
        if (sharedInstance != null) {
            sharedInstance!!.lock.lock()
            if (sharedInstance!!.busyCommand?.callback == this) {
                sharedInstance!!.busyCommand = null
            }
            sharedInstance!!.queue.removeIf { q -> q.callback == this }
            sharedInstance!!.lock.unlock()
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        if (status == BluetoothGatt.GATT_SUCCESS &&
            gatt != null
        ) {
            if (deviceHelpers.containsKey(gatt.device.address)) {
                sharedInstance = deviceHelpers[gatt.device.address]
            } else {
                sharedInstance = BluetoothGattWrapperInstance()
                deviceHelpers[gatt.device.address] = sharedInstance!!
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // We successfully connected, proceed with service discovery
                connectedGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // We successfully disconnected on our own request
                onWrappedDisconnected(gatt)
                connectedGatt = null
                if (shouldAutoDisconnect) {
                    gatt.close()
                }
                clearQueue()
            } else {
                // We're CONNECTING or DISCONNECTING, ignore for now
            }
        } else {
            onWrappedDisconnected(gatt)
            connectedGatt = null
            if (shouldAutoDisconnect) {
                gatt?.close()
            }
            clearQueue()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            onWrappedServicesDiscovered(gatt, status)
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
        sharedInstance!!.lock.lock()
        val callback = sharedInstance!!.busyCommand?.callback
        sharedInstance!!.busyCommand = null
        sharedInstance!!.lock.unlock()

        callback?.onWrappedCharacteristicRead(characteristic, status)
//        sharedInstance!!.callbacks.forEach { c -> c.onWrappedCharacteristicRead(characteristic, status) }
//        onWrappedCharacteristicRead(characteristic, status)
        processQueue()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        sharedInstance!!.lock.lock()
//        sharedInstance!!.isQueueBusy = false
        sharedInstance!!.busyCommand = null
        sharedInstance!!.lock.unlock()

        processQueue()
    }

    override fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {}
    override fun onWrappedCharacteristicRead(
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
    }

    override fun onWrappedDisconnected(gatt: BluetoothGatt?) {}

    open fun close() {
        connectedGatt?.close()
//        sharedInstance?.callbacks?.remove(this)
        clearQueue()
    }

//    fun wrappedGetService(id: UUID): BluetoothGattService? {
//        return connectedGatt?.getService(id)
//    }

    fun wrappedReadCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        if (sharedInstance == null) {
            return
        }
        sharedInstance!!.lock.lock()
        sharedInstance!!.queue.add(BluetoothGattWrapperInstance.Command(0, this, characteristic!!))
        sharedInstance!!.lock.unlock()
        processQueue()
    }

    fun wrappedWriteCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        if (sharedInstance == null) {
            return
        }
        val newCommand = BluetoothGattWrapperInstance.Command(1, this, characteristic!!)
        sharedInstance!!.lock.lock()
        if (sharedInstance!!.queue.contains(newCommand)) {
            sharedInstance!!.lock.unlock()
//            Log.i("asdf", "dropped write")
            return
        }
        sharedInstance!!.queue.add(newCommand)
        sharedInstance!!.lock.unlock()
        processQueue()
    }
}