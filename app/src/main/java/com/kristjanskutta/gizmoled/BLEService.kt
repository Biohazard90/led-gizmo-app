package com.kristjanskutta.gizmoled


import android.Manifest.permission.MODIFY_AUDIO_SETTINGS
import android.Manifest.permission.RECORD_AUDIO
import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.media.audiofx.Visualizer.MEASUREMENT_MODE_PEAK_RMS
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet


interface AudioListener {
    fun receiveProcessedAudio(data: ByteArray) {}
}

interface AudioProcessor {
    fun addListener(listener: AudioListener)
    fun removeListener(listener: AudioListener)
    fun removeAllListeners()
    fun shutdown()
}

class BLEService : Service() {

    companion object {
        val COMMAND_REGISTER_DEVICE = 1000
        val COMMAND_UNREGISTER_DEVICE = 1001
        val COMMAND_REGISTER_CALLBACK = 1002
        val COMMAND_UNREGISTER_CALLBACK = 1003

        val INTENT_KEY_COMMAND = "cmd"
        val INTENT_KEY_DEVICE = "device"
        val INTENT_KEY_CALLBACK = "callback"

        fun needsPermissions(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED
        }

        fun getPermissionList(): Array<String> {
            return arrayOf(
                RECORD_AUDIO,
                MODIFY_AUDIO_SETTINGS
            )
        }

        private val callbacksLock: Lock = ReentrantLock()
        private val callbacks = HashMap<String, AudioListener>()

        fun addDirectCallback(tag: String, callback: AudioListener) {
            callbacksLock.lock()
            callbacks[tag] = callback
            callbacksLock.unlock()
        }

//        fun removeDirectCallback(tag: String){
//            callbacksLock.lock()
//            callbacks.remove(tag)
//            callbacksLock.unlock()
//        }
    }

    private var notificationChannel: NotificationChannel? = null
    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null
    private val devicesLock: Lock = ReentrantLock()
    private val devices: Deque<BluetoothDevice> = LinkedList<BluetoothDevice>()

    fun getDevices(): List<BluetoothDevice> {
        devicesLock.lock()
        val devicesCopy = devices.toList()
        devicesLock.unlock()
        return devicesCopy
    }

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper, context: Context) : Handler(looper) {
        var context: Context = context

        private fun shouldStopService(): Boolean {
            return devices.isEmpty() &&
                    callbacks.isEmpty()
        }

        private fun stopService() {
            audioProcessor.shutdown()
            stopSelf()
        }

        override fun handleMessage(msg: Message) {
            when (msg.arg1) {
                COMMAND_REGISTER_DEVICE -> {
                    val device: BluetoothDevice = msg.obj as BluetoothDevice
                    devicesLock.lock()
                    if (!devices.contains(device)) {
                        devices.add(device)
                    }
                    devicesLock.unlock()

                    device.connectGatt(context, false, gattWrapper, BluetoothDevice.TRANSPORT_LE)
                }

                COMMAND_UNREGISTER_DEVICE -> {
                    val device: BluetoothDevice = msg.obj as BluetoothDevice
                    devicesLock.lock()
                    devices.remove(device)
                    devicesLock.unlock()

                    if (shouldStopService()) {
                        gattWrapper.close()
                        stopService()
                    }
                }

                COMMAND_REGISTER_CALLBACK -> {
                    val listenerTag = msg.obj as String
                    callbacksLock.lock()
                    val listener = callbacks[listenerTag]!!
                    callbacksLock.unlock()
                    audioProcessor.addListener(listener)
                }

                COMMAND_UNREGISTER_CALLBACK -> {
                    val listenerTag = msg.obj as String
                    callbacksLock.lock()
                    val listener = callbacks[listenerTag]!!
                    callbacks.remove(listenerTag)
                    callbacksLock.unlock()
                    audioProcessor.removeListener(listener)

                    if (shouldStopService()) {
                        gattWrapper.close()
                        stopService()
                    }
                }
            }
        }

        var audioProcessor: AudioProcessor = object : AudioProcessor, Visualizer.OnDataCaptureListener {

            val FFT_FX_SIZE = 6

            private var listeners = HashSet<AudioListener>()

            private var visualizer: Visualizer? = null
            private var visualizerRange: Int = 1
            private var fftFXBoundsLow = FloatArray(FFT_FX_SIZE, { i -> 0.0f })
            private var fftFXBoundsHigh = FloatArray(FFT_FX_SIZE, { i -> 0.0f })
            private var fftFXLightTimer = FloatArray(FFT_FX_SIZE, { i -> 0.0f })
            private var fftFXLastTime: Long = 0
            private var fftFXFrametime: Float = 0.0f

            override fun addListener(listener: AudioListener) {
                if (visualizer == null) {
                    visualizer = Visualizer(0)
                }
                listeners.add(listener)
                if (listeners.size == 1) {
                    beginCapture()
                }
            }

            override fun removeListener(listener: AudioListener) {
                listeners.remove(listener)
                if (listeners.isEmpty()) {
                    endCapture()
                }
            }

            override fun removeAllListeners() {
                listeners.clear()
                endCapture()
            }

            override fun shutdown() {
                removeAllListeners()
                visualizer?.release()
            }

            fun beginCapture() {
                if (visualizer != null && !visualizer!!.enabled) {
                    val range = Visualizer.getCaptureSizeRange()
                    visualizer!!.captureSize = Math.max(range[0], Math.min(range[1], 1024))
                    visualizerRange = visualizer!!.captureSize
                    visualizer!!.setMeasurementMode(MEASUREMENT_MODE_PEAK_RMS)
                    visualizer!!.setDataCaptureListener(this, Visualizer.getMaxCaptureRate(), false, true)
                    visualizer!!.enabled = true
                    fftFXLastTime = System.currentTimeMillis()
                }
            }

            fun endCapture() {
                if (visualizer != null && visualizer!!.enabled) {
                    visualizer!!.enabled = false
                }
            }

            private fun sampleFFT(fft: FloatArray, boundsIndex: Int, start: Int, end: Int, threshold: Float): Float {
                var sum = 0.0f
                var vMin = 1.0f
                var vMax = 0.0f

                for (i in start until end) {
                    sum += fft[i]
                    vMin = Math.min(fft[i], vMin)
                    vMax = Math.max(fft[i], vMax)
                }

                sum /= (end - start).toFloat()

                if (vMin < fftFXBoundsLow[boundsIndex]) {
                    //fftFXBoundsLow[boundsIndex] = min
                    fftFXBoundsLow[boundsIndex] += (vMin - fftFXBoundsLow[boundsIndex]) * Math.min(1.0f, fftFXFrametime)
                } else {
                    fftFXBoundsLow[boundsIndex] += (vMin - fftFXBoundsLow[boundsIndex]) * Math.min(1.0f, fftFXFrametime * 0.1f)
                }

                if (vMax > fftFXBoundsHigh[boundsIndex]) {
                    //fftFXBoundsHigh[boundsIndex] = max
                    fftFXBoundsHigh[boundsIndex] += (vMax - fftFXBoundsHigh[boundsIndex]) * Math.min(1.0f, fftFXFrametime)
                } else {
                    fftFXBoundsHigh[boundsIndex] += (vMax - fftFXBoundsHigh[boundsIndex]) * Math.min(1.0f, fftFXFrametime * 0.1f)
                }

                // Map between 0 and 1
//                val boundsHigh = (fftFXBoundsHigh[boundsIndex] - fftFXBoundsLow[boundsIndex]) * threshold
//                val boundsLow = fftFXBoundsLow[boundsIndex]
//                val boundsDelta = (boundsHigh - boundsLow).toFloat()
//                return Math.max(0.0f, Math.min((vMax - boundsLow) / boundsDelta, 1.0f))

                fftFXLightTimer[boundsIndex] -= fftFXFrametime

                // Step between 0 and 1
                if (vMax >= (fftFXBoundsHigh[boundsIndex] - fftFXBoundsLow[boundsIndex]) * threshold + fftFXBoundsLow[boundsIndex]) {
                    fftFXLightTimer[boundsIndex] = 0.1f
                    return 1.0f
                } else {
                    if (fftFXLightTimer[boundsIndex] > 0.0f) {
                        return 1.0f
                    }
                    return 0.0f
                }
            }

            var fft: FloatArray? = null
            val peakRMS = Visualizer.MeasurementPeakRms()
            override fun onFftDataCapture(visualizer: Visualizer?, bytes: ByteArray?, samplingRate: Int) {
//                if (fft == null ||
//                    fft?.size != bytes!!.size / 2) {
//                    fft = FloatArray(bytes!!.size / 2)
//                }

//                for (i in fft!!.indices) {
//                    val real = bytes[i * 2 + 0].toFloat() / 128.0f
//                    val imag = bytes[i * 2 + 1].toFloat() / 128.0f
//                    fft!![i] = (real * real + imag * imag)
//                }

                val n: Int = bytes!!.size
//                val magnitudes = FloatArray(n / 2 + 1)
//                val phases = FloatArray(n / 2 + 1)
                if (fft == null ||
                    fft?.size != n / 2 + 1
                ) {
                    fft = FloatArray(n / 2 + 1)
                }

                fft!![0] = Math.abs(bytes!![0].toFloat()) // DC
                fft!![n / 2] = Math.abs(bytes!![1].toFloat()) // Nyquist
//                phases[0] = 0.0f
//                phases[n / 2] = 0.0f
                for (k in 1 until n / 2) {
                    val i = k * 2
                    fft!![k] = Math.hypot(bytes[i].toDouble(), bytes[i + 1].toDouble()).toFloat()
//                    phases[k] = Math.atan2(fft[i + 1].toDouble(), fft[i].toDouble()).toFloat()
                }

                val currentMillis: Long = System.currentTimeMillis()
                fftFXFrametime = (currentMillis - fftFXLastTime).toFloat() / 1000.0f
                fftFXLastTime = currentMillis

                //visualizer!!.getMeasurementPeakRms(peakRMS)
//                Log.i("asdf", "peak ${peakRMS.mRms.toFloat()/peakRMS.mPeak.toFloat()}")

                val processedData = byteArrayOf(
                    //Math.min(255.0f, Math.max(0.0f, (peakRMS.mRms.toFloat() / peakRMS.mPeak.toFloat() - 2.0f) * 100.0f)).toByte(),
                    (sampleFFT(fft!!, 0, 0, 2, 1.0f).toDouble() * 255).toByte(),
                    (sampleFFT(fft!!, 1, 4, 12, 1f).toDouble() * 255).toByte(),
                    (sampleFFT(fft!!, 2, 12, 32, 1f).toDouble() * 255).toByte(),
                    (sampleFFT(fft!!, 3, 32, 64, 1f).toDouble() * 255).toByte(),
                    (sampleFFT(fft!!, 4, 64, 256, 1f).toDouble() * 255).toByte(),
                    (sampleFFT(fft!!, 5, 256, 512, 1f).toDouble() * 255).toByte()
                )

//                    var maxAmp = 0.0f
//                    for (i in 0 until 5) {
//                        maxAmp = Math.max(maxAmp, fftFXBoundsHigh[i])
//                    }
//                    for (i in 0 until 5) {
//                        fftFXBoundsHigh[i] = Math.max(fftFXBoundsHigh[i], maxAmp * 0.1f)
//                    }

                listeners.forEach { listener ->
                    listener.receiveProcessedAudio(processedData)
                }
            }

            override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            }
        }

        var gattWrapper: BluetoothGattWrapper = object : BluetoothGattWrapper() {

            var ledService: BluetoothGattService? = null
            var audioDataCharacteristic: BluetoothGattCharacteristic? = null
            val deviceListeners = HashMap<BluetoothDevice, AudioListener>()

            override fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                connectedGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                ledService = gatt?.getService(BLEConstants.LEDService)
                audioDataCharacteristic = ledService?.getCharacteristic(BLEConstants.AudioDataCharacteristic)
                if (audioDataCharacteristic != null &&
                    gatt != null
                ) {
                    val deviceListener = object : AudioListener {
                        override fun receiveProcessedAudio(data: ByteArray) {
                            audioDataCharacteristic?.value = data
                            wrappedWriteCharacteristic(audioDataCharacteristic)
                        }
                    }
                    deviceListeners[gatt!!.device] = deviceListener;
                    audioProcessor.addListener(deviceListener)
                }
            }

            override fun onWrappedDisconnected(gatt: BluetoothGatt?) {
                if (gatt != null && deviceListeners[gatt!!.device] != null) {
                    val device = gatt!!.device
                    audioProcessor.removeListener(deviceListeners[device]!!)
                    deviceListeners.remove(device)
                }

                this@ServiceHandler.obtainMessage()?.also { msg ->
                    msg.arg1 = COMMAND_UNREGISTER_DEVICE
                    msg.obj = gatt?.device
                    serviceHandler?.sendMessage(msg)
                }
            }

            override fun close() {
                deviceListeners.clear()
//                audioProcessor.removeAllListeners()

                audioDataCharacteristic = null
                ledService = null
                super.close()
            }
        }
    }

    override fun onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread("ServiceStartArguments", THREAD_PRIORITY_BACKGROUND).apply {
            start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper, this@BLEService)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val shouldRegister = intent.extras?.getInt(INTENT_KEY_COMMAND) == COMMAND_REGISTER_DEVICE ||
                intent.extras?.getInt(INTENT_KEY_COMMAND) == COMMAND_REGISTER_CALLBACK
        val device: BluetoothDevice? = intent.extras?.get(INTENT_KEY_DEVICE) as? BluetoothDevice
        val audioCallback: String? = intent.extras?.get(INTENT_KEY_CALLBACK) as? String

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        if (device != null) {
            serviceHandler?.obtainMessage()?.also { msg ->
                msg.arg1 = intent.extras?.getInt(INTENT_KEY_COMMAND)!!
                msg.obj = device
                serviceHandler?.sendMessage(msg)
            }
        } else {
            serviceHandler?.obtainMessage()?.also { msg ->
                msg.arg1 = intent.extras?.getInt(INTENT_KEY_COMMAND)!!
                msg.obj = audioCallback
                serviceHandler?.sendMessage(msg)
            }
        }

        var pendingIntent: PendingIntent? = null
        if (shouldRegister) {
            if (device != null) {
                pendingIntent = Intent(this, CollarSettingsActivity::class.java).let { notificationIntent ->
                    notificationIntent.putExtra("collarName", device?.name!!)
                    notificationIntent.putExtra("collarDevice", device)
                    TaskStackBuilder.create(this)
                        .addNextIntentWithParentStack(notificationIntent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
                }
            } else {
                pendingIntent = Intent(this, DevVisualizerActivity::class.java).let { notificationIntent ->
                    TaskStackBuilder.create(this)
                        .addNextIntentWithParentStack(notificationIntent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
                }
            }
        }

        if (shouldRegister) {
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    "LEDAudioVisualizerChannel",
                    "LEDAudioVisualizerChannel", NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationChannel?.setSound(null, null)

                val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                service.createNotificationChannel(notificationChannel!!)
            }

            val notification: Notification = Notification.Builder(this, "LEDAudioVisualizerChannel")
                .setContentTitle(getText(R.string.service_title))
                .setContentText(getText(R.string.service_content))
                .setSmallIcon(R.drawable.ic_equalizer_24px)
                .setContentIntent(pendingIntent)
                .build()

            startForeground(10, notification)
        }

        return START_NOT_STICKY
    }

    inner class BLEBinder : Binder() {
        fun getService(): BLEService = this@BLEService
    }

    val binder = BLEBinder()

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onDestroy() {
    }
}
