package com.kristjanskutta.doggylight

import android.bluetooth.*
import java.util.*
import java.util.concurrent.locks.ReentrantLock

abstract class BluetoothGattWrapper() : BluetoothGattCallback() {

    protected var connectedGatt: BluetoothGatt? = null

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
            when (cmd.type) {
                0 -> {
                    connectedGatt?.readCharacteristic(cmd.characteristic)
                }

                1 -> {
                    connectedGatt?.writeCharacteristic(cmd.characteristic)
                }
            }
        }
        lock.unlock()
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // We successfully connected, proceed with service discovery
                connectedGatt = gatt
                gatt?.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // We successfully disconnected on our own request
                connectedGatt = null
                gatt?.close();
            } else {
                // We're CONNECTING or DISCONNECTING, ignore for now
            }
        } else {
            connectedGatt = null
            gatt?.close();
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        onWrappedServicesDiscovered(this, status)
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)
        lock.lock()
        isQueueBusy = false
        lock.unlock()

        onWrappedCharacteristicRead(this, characteristic, status)
        processQueue()
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        lock.lock()
        isQueueBusy = false
        lock.unlock()

        processQueue()
    }

    open fun onWrappedServicesDiscovered(wrapper: BluetoothGattWrapper?, status: Int) {}
    open fun onWrappedCharacteristicRead(wrapper: BluetoothGattWrapper?, characteristic: BluetoothGattCharacteristic?, status: Int) {}

    open fun close() {
        connectedGatt?.close()
    }

    fun wrappedGetService(id: UUID): BluetoothGattService? {
        return connectedGatt?.getService(id)
    }

    fun wrappedReadCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        queue.add(Command(0, characteristic!!))
        processQueue()
    }

    fun wrappedWriteCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        val newCommand = Command(1, characteristic!!)
        if (queue.contains(newCommand)) {
            return
        }
        queue.add(newCommand)
        processQueue()
    }
}