package com.kristjanskutta.doggylight

import android.bluetooth.*
import java.util.*
import java.util.concurrent.locks.ReentrantLock

abstract class BluetoothGattWrapper() : BluetoothGattCallback() {

    @Volatile protected var connectedGatt: BluetoothGatt? = null

    private class Command(val type: Int = 0, val characteristic: BluetoothGattCharacteristic) {
        override fun equals(other: Any?): Boolean {
            return other is Command &&
                    type == other?.type && characteristic == other?.characteristic
        }
    }

    private val queue: Queue<Command> = LinkedList<Command>()
    private val lock: ReentrantLock = ReentrantLock()
    private var isQueueBusy = false

    private fun processQueue() {
        lock.lock()
        if (!queue.isEmpty() &&
            !isQueueBusy
        ) {
            isQueueBusy = true
            val cmd = queue.remove()
            lock.unlock()

            when (cmd.type) {
                0 -> {
                    connectedGatt?.readCharacteristic(cmd.characteristic)
                }

                1 -> {
                    connectedGatt?.writeCharacteristic(cmd.characteristic)
                }
            }
        } else {
            lock.unlock()
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // We successfully connected, proceed with service discovery
                connectedGatt = gatt
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // We successfully disconnected on our own request
                onWrappedDisconnected(gatt)
                connectedGatt = null
                gatt?.close();
            } else {
                // We're CONNECTING or DISCONNECTING, ignore for now
            }
        } else {
            onWrappedDisconnected(gatt)
            connectedGatt = null
            gatt?.close()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            onWrappedServicesDiscovered(gatt, status)
        }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)
        lock.lock()
        isQueueBusy = false
        lock.unlock()

        onWrappedCharacteristicRead(characteristic, status)
        processQueue()
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        lock.lock()
        isQueueBusy = false
        lock.unlock()

        processQueue()
    }

    open fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {}
    open fun onWrappedCharacteristicRead(characteristic: BluetoothGattCharacteristic?, status: Int) {}
    open fun onWrappedDisconnected(gatt: BluetoothGatt?) {}

    open fun close() {
        connectedGatt?.close()
    }

    fun wrappedGetService(id: UUID): BluetoothGattService? {
        return connectedGatt?.getService(id)
    }

    fun wrappedReadCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        lock.lock()
        queue.add(Command(0, characteristic!!))
        lock.unlock()
        processQueue()
    }

    fun wrappedWriteCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        val newCommand = Command(1, characteristic!!)
        lock.lock()
        if (queue.contains(newCommand)) {
            lock.unlock()
            return
        }
        queue.add(newCommand)
        lock.unlock()
        processQueue()
    }
}