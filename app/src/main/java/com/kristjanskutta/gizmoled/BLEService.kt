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
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min


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
            return ContextCompat.checkSelfPermission(
                context,
                RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        MODIFY_AUDIO_SETTINGS
                    ) != PackageManager.PERMISSION_GRANTED
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
                    val hasDevices = !devices.isEmpty()
                    devices.remove(device)
                    devicesLock.unlock()

                    if (hasDevices && shouldStopService()) {
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
                    val hasDevices = !callbacks.isEmpty()
                    val listener = callbacks[listenerTag]!!
                    callbacks.remove(listenerTag)
                    callbacksLock.unlock()
                    audioProcessor.removeListener(listener)

                    if (hasDevices && shouldStopService()) {
                        gattWrapper.close()
                        stopService()
                    }
                }
            }
        }

        var audioProcessor: AudioProcessor =
            object : AudioProcessor, Visualizer.OnDataCaptureListener {

                val FFT_FX_SIZE = 6

                private var listeners = HashSet<AudioListener>()

                private var visualizer: Visualizer? = null
                private var visualizerRange: Int = 1

                private var fftHistoryAvg: ArrayList<FloatArray> = ArrayList()
                private var fftHistoryPeak: ArrayList<FloatArray> = ArrayList()
                private var fftHistoryWriter = -1

                //                private var fftFXLightTimer = FloatArray(FFT_FX_SIZE) { 0.0f }
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

                        while (fftHistoryAvg.size < 40) {
                            fftHistoryAvg.add(FloatArray(FFT_FX_SIZE))
                            fftHistoryPeak.add(FloatArray(FFT_FX_SIZE))
                        }

                        val range = Visualizer.getCaptureSizeRange()
                        visualizer!!.captureSize = max(range[0], min(range[1], 1024))
                        visualizerRange = visualizer!!.captureSize
                        visualizer!!.setMeasurementMode(MEASUREMENT_MODE_PEAK_RMS)
                        visualizer!!.setDataCaptureListener(
                            this,
                            Visualizer.getMaxCaptureRate(),
                            false,
                            true
                        )
                        visualizer!!.enabled = true
                        fftFXLastTime = System.currentTimeMillis()
                    }
                }

                fun endCapture() {
                    if (visualizer != null && visualizer!!.enabled) {
                        visualizer!!.enabled = false
                    }
                }

                private fun sampleFFT(
                    fft: FloatArray,
                    boundsIndex: Int,
                    start: Int,
                    end: Int,
                    threshold: Float
                ): Float {
                    var sum = 0.0f
//                    var vMin = 255.0f
                    var vMax = 0.0f

                    for (i in start until end) {
                        sum += fft[i]
//                        vMin = min(fft[i], vMin)
                        vMax = max(fft[i], vMax)
                    }

                    sum /= (end - start).toFloat()

                    if (boundsIndex == 0) {
                        ++fftHistoryWriter
                        if (fftHistoryWriter >= fftHistoryAvg.size) {
                            fftHistoryWriter = 0
                        }
                    }

                    var aAvg = fftHistoryAvg[fftHistoryWriter]
                    var aPeak = fftHistoryPeak[fftHistoryWriter]
                    aAvg[boundsIndex] = sum
                    aPeak[boundsIndex] = vMax

                    var cAvg = 0.0f
                    var cPeak = 0.0f
                    fftHistoryAvg.forEach { f -> cAvg += f[boundsIndex] }
                    fftHistoryPeak.forEach { f -> cPeak = max(cPeak, f[boundsIndex]) }
                    cAvg /= fftHistoryAvg.size.toFloat()
                    //cPeak /= fftHistoryPeak.size.toFloat()

//                    if (boundsIndex == 0) {
//                        Log.i("asdf", "cAvg: $cAvg, cPeak $cPeak")
//                    }

                    //fftFXLightTimer[boundsIndex] -= fftFXFrametime

                    return if (cPeak > cAvg * 1.25 &&
                        vMax >= (cPeak - cAvg) * threshold + cAvg
                    ) {
                        //fftFXLightTimer[boundsIndex] = 0.1f
                        1.0f
                    } else {
                        // if (fftFXLightTimer[boundsIndex] > 0.0f) {
                        //    return 1.0f
                        //  }
                        0.0f
                    }
                }

                var fft: FloatArray? = null
                //                var fftLast: FloatArray? = null
                var fftSmooth: FloatArray = FloatArray(FFT_FX_SIZE) { 0.0f }
                var fftBeat: ByteArray = ByteArray(FFT_FX_SIZE) { 0 }
                //                                val peakRMS = Visualizer.MeasurementPeakRms()
                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    bytes: ByteArray?,
                    samplingRate: Int
                ) {

                    val n: Int = bytes!!.size
//                val magnitudes = FloatArray(n / 2 + 1)
//                val phases = FloatArray(n / 2 + 1)
                    if (fft == null ||
                        fft?.size != n / 2 + 1
                    ) {
                        fft = FloatArray(n / 2 + 1) { 0.0f }
//                        fftLast = FloatArray(n / 2 + 1) { 0.0f }
//                        fftSmooth = FloatArray(n / 2 + 1) { 0.0f }
                    }

                    fft!![0] = abs(bytes[0].toFloat()) // DC
                    fft!![n / 2] = abs(bytes[1].toFloat()) // Nyquist
//                phases[0] = 0.0f
//                phases[n / 2] = 0.0f
                    for (k in 1 until n / 2) {
                        val i = k * 2

//                        val rfk =  bytes.get(i).toDouble()
//                        val ifk =  bytes.get(i + 1).toDouble()
//
//                        var magnitude = rfk * rfk + ifk * ifk
//                        val dbValue = if (magnitude > 0.0) (10 * Math.log10(magnitude)) else 0.0
//                        fft!![k] = dbValue.toFloat()

                        fft!![k] = hypot(bytes[i].toDouble(), bytes[i + 1].toDouble()).toFloat()
//                    phases[k] = Math.atan2(fft[i + 1].toDouble(), fft[i].toDouble()).toFloat()
                    }

                    val currentMillis: Long = System.currentTimeMillis()
                    fftFXFrametime = (currentMillis - fftFXLastTime).toFloat() / 1000.0f
                    fftFXLastTime = currentMillis

//                    visualizer!!.getMeasurementPeakRms(peakRMS)
//                Log.i("asdf", "peak ${peakRMS.mPeak.toFloat()}, rms ${peakRMS.mRms.toFloat()}")


                    val processedData = floatArrayOf(
                        //Math.min(255.0f, Math.max(0.0f, (peakRMS.mRms.toFloat() / peakRMS.mPeak.toFloat() - 2.0f) * 100.0f)).toByte(),
                        sampleFFT(fft!!, 0, 0, 2, 0.6f),
                        sampleFFT(fft!!, 1, 4, 8, 0.7f),
                        sampleFFT(fft!!, 2, 8, 16, 0.75f),
                        sampleFFT(fft!!, 3, 16, 32, 0.75f),
                        sampleFFT(fft!!, 4, 32, 224, 0.75f),
                        sampleFFT(fft!!, 5, 224, 512, 0.75f)
                    )

                    for (i in 0 until FFT_FX_SIZE) {
                        if (processedData[i] > 0.5f) {
                            fftSmooth[i] = processedData[i]
                        } else {
                            fftSmooth[i] += (processedData[i] - fftSmooth[i]) * min(
                                1.0f,
                                fftFXFrametime * (if (i == 0) 4.0f else 8.0f)
                            )
                        }
                        val hasBeat = fftSmooth[i] >= 0.5f
//                        fftBeat[i] = (fftSmooth[i] * 255).toByte()
                        fftBeat[i] = if (hasBeat) 255.toByte() else 0

//                        if (hasBeat) {
//                            fftFXLightTimer[i] = 0.05f
//                        } else {
//                            if (fftFXLightTimer[i] > 0.0f) {
//                                fftBeat[i] = 255.toByte()
//                                fftFXLightTimer[i] -= fftFXFrametime
//                            }
//                        }
                    }

//                    Log.i("asdf", "low: ${processedData[0]}")

//                    var maxAmp = 0.0f
//                    for (i in 0 until 5) {
//                        maxAmp = Math.max(maxAmp, fftFXBoundsHigh[i])
//                    }
//                    for (i in 0 until 5) {
//                        fftFXBoundsHigh[i] = Math.max(fftFXBoundsHigh[i], maxAmp * 0.1f)
//                    }

                    listeners.forEach { listener ->
                        listener.receiveProcessedAudio(fftBeat)
                    }
                }

                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) {
                }
            }

        var gattWrapper = object : BluetoothGattWrapper() {

            var ledService: BluetoothGattService? = null
            var audioDataCharacteristic: BluetoothGattCharacteristic? = null
            val deviceListeners = HashMap<BluetoothDevice, AudioListener>()
            var audioFrame: Int = 0

            override fun onWrappedServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                connectedGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                ledService = gatt?.getService(BLEConstants.LEDService)
                audioDataCharacteristic =
                    ledService?.getCharacteristic(BLEConstants.AudioDataCharacteristic)
                if (audioDataCharacteristic != null &&
                    gatt != null
                ) {
                    val deviceListener = object : AudioListener {
                        override fun receiveProcessedAudio(data: ByteArray) {
                            audioDataCharacteristic?.value = data
                            if (audioDataCharacteristic != null) {
                                wrappedWriteCharacteristic(audioDataCharacteristic)
//                                Log.i("asdf", "Audio frame $audioFrame")
                                ++audioFrame
                            }
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
                pendingIntent =
                    Intent(this, CollarSettingsActivity::class.java).let { notificationIntent ->
                        notificationIntent.putExtra("collarName", device?.name!!)
                        notificationIntent.putExtra("collarDevice", device)
                        TaskStackBuilder.create(this)
                            .addNextIntentWithParentStack(notificationIntent)
                            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
                    }
            } else {
                pendingIntent =
                    Intent(this, DevVisualizerActivity::class.java).let { notificationIntent ->
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
