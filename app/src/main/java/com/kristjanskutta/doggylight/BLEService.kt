package com.kristjanskutta.doggylight


import android.Manifest.permission.MODIFY_AUDIO_SETTINGS
import android.Manifest.permission.RECORD_AUDIO
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*


class BLEService : Service() {

    companion object {
        //        private var instance: BLEVisualizer? = null
//
//        fun getInstance(): BLEVisualizer {
//            if (instance == null) {
//                instance = BLEVisualizer()
//            }
//            return instance!!
//        }
//
        val COMMAND_REGISTER_DEVICE = 1000
        val COMMAND_UNREGISTER_DEVICE = 1001

        val INTENT_KEY_COMMAND = "cmd"
        val INTENT_KEY_DEVICE = "device"

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

//        fun moveToStartedState(context: Context) {
//            val intent: Intent = Intent(context, BLEService::class.java) //MyIntentBuilder(context).setCommand(Command.STOP).build()
//            intent.putExtra("cmd", 1)
////        val intent: Intent = MyIntentBuilder(this).setCommand(Command.START).build()
//            context.startForegroundService(intent)
//        }
    }

    //private var visualizer: Visualizer = Visualizer(0)
    private var notificationChannel: NotificationChannel? = null

//    fun start() {
//        if (!visualizer.enabled) {
//            visualizer.setCaptureSize(64)
//            visualizer.setDataCaptureListener(this, 20000, false, true)
//            visualizer.enabled = true
//        }
//    }
//
//    fun stop() {
//        if (visualizer.enabled) {
//            visualizer.enabled = false
//        }
//    }

    fun finalize() {
//        stop()
//        visualizer.release()
    }

//    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
//        val fftv0 = Math.abs(fft!![0].toInt())
//        val count = fft.size
//        Log.i("AUDIOVIS", "$count fft $fftv0")
//    }
//
//    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
//    }


    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null
    //    private val deviceLock: Lock = ReentrantLock()
    private val devices: Deque<BluetoothDevice> = LinkedList<BluetoothDevice>()

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper, context: Context) : Handler(looper) {
        var context: Context = context

        override fun handleMessage(msg: Message) {
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
//            try {
////                Toast.makeText(this@BLEService, "receive msg: " + msg.arg1, Toast.LENGTH_SHORT).show()
//               // Thread.sleep(5000)
//            } catch (e: InterruptedException) {
//                // Restore interrupt status.
//                Thread.currentThread().interrupt()
//            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            //stopSelf(msg.arg1)

            val device: BluetoothDevice = msg.obj as BluetoothDevice
            when (msg.arg1) {
                COMMAND_REGISTER_DEVICE -> {
                    devices.add(device)
                    device?.connectGatt(context, false, gattWrapper, BluetoothDevice.TRANSPORT_LE)
                }

                COMMAND_UNREGISTER_DEVICE -> {
                    devices.remove(device)
                    if (devices.isEmpty()) {
                        gattWrapper.close()
                        stopSelf()
                    }
                }
            }
        }

        var gattWrapper: BluetoothGattWrapper = object : BluetoothGattWrapper(), Visualizer.OnDataCaptureListener {
            val FFT_FX_SIZE = 5

            var ledService: BluetoothGattService? = null
            var audioDataCharacteristic: BluetoothGattCharacteristic? = null

            private var visualizer: Visualizer = Visualizer(0)
            private var visualizerRange: Int = 1
            private var fftFXBoundsLow = FloatArray(FFT_FX_SIZE, { i -> 0.0f })
            private var fftFXBoundsHigh = FloatArray(FFT_FX_SIZE, { i -> 0.0f })
            private var fftFXLastTime: Long = 0
            private var fftFXFrametime: Float = 0.0f

            override fun onWrappedServicesDiscovered(wrapper: BluetoothGattWrapper?, status: Int) {
                connectedGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                ledService = wrapper?.wrappedGetService(BLEConstants.LEDService)
                audioDataCharacteristic = ledService?.getCharacteristic(BLEConstants.AudioDataCharacteristic)
                if (audioDataCharacteristic != null &&
                    !visualizer.enabled
                ) {
                    val range = Visualizer.getCaptureSizeRange()
                    visualizer.captureSize = Math.max(range[0], Math.min(range[1], 1024))
                    visualizerRange = visualizer.captureSize
                    visualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate(), false, true)
                    visualizer.enabled = true
                    fftFXLastTime = System.currentTimeMillis()
                }
            }

            override fun close() {
                visualizer.enabled = false
                visualizer.release()
                audioDataCharacteristic = null
                ledService = null
                super.close()
            }

            private fun sampleFFT(fft: FloatArray, boundsIndex: Int, start: Int, end: Int): Float {
                var sum = 0.0f
                var min = 1.0f
                var max = 0.0f

                for (i in start until end) {
                    sum += fft[i]
                    min = Math.min(fft[i], min)
                    max = Math.max(fft[i], max)
                }

                sum /= (end - start).toFloat()

                if (min < fftFXBoundsLow[boundsIndex]) {
                    fftFXBoundsLow[boundsIndex] = min
                } else {
                    fftFXBoundsLow[boundsIndex] += (min - fftFXBoundsLow[boundsIndex]) * Math.min(1.0f, fftFXFrametime * 20)
                }

                if (max > fftFXBoundsHigh[boundsIndex]) {
                    fftFXBoundsHigh[boundsIndex] = max
                } else {
                    fftFXBoundsHigh[boundsIndex] += (max - fftFXBoundsHigh[boundsIndex]) * Math.min(1.0f, fftFXFrametime * 20)
                }

//                Log.i("vis", "ar: " + boundsIndex + ", min: " + fftFXBoundsLow[boundsIndex] + ", max: " + fftFXBoundsHigh[boundsIndex])

                if (sum >= (fftFXBoundsHigh[boundsIndex] - fftFXBoundsLow[boundsIndex]) * 0.3333 + fftFXBoundsLow[boundsIndex]) {
                    return 1.0f
                } else {
                    return 0.0f
                }

                //return Math.sin(sum / (end - start) * Math.PI * 0.5).toFloat()
            }

            override fun onFftDataCapture(visualizer: Visualizer?, bytes: ByteArray?, samplingRate: Int) {
                if (audioDataCharacteristic != null) {

                    val fft = FloatArray(bytes!!.size / 2)
                    for (i in fft.indices) {
                        val real = bytes!![i * 2 + 0].toFloat() / 128.0f
                        val imag = bytes[i * 2 + 1].toFloat() / 128.0f
                        fft[i] = (real * real + imag * imag)
                    }
//                    Log.i("asdf", "flft " + fft[0])

                    val currentMillis: Long = System.currentTimeMillis()
                    fftFXFrametime = (currentMillis - fftFXLastTime).toFloat() / 1000.0f
                    fftFXLastTime = currentMillis

                    audioDataCharacteristic?.value = byteArrayOf(
                        (sampleFFT(fft, 0, 0, 32).toDouble() * 255).toByte(),
                        (sampleFFT(fft, 1, 32, 64).toDouble() * 255).toByte(),
                        (sampleFFT(fft, 2, 64, 192).toDouble() * 255).toByte(),
                        (sampleFFT(fft, 3, 192, 256).toDouble() * 255).toByte(),
                        (sampleFFT(fft, 4, 256, 512).toDouble() * 255).toByte()
                    )
                    wrappedWriteCharacteristic(audioDataCharacteristic)

//                    var maxAmp = 0.0
//                    for (i in 0..4) {
//                        maxAmp = Math.max(maxAmp, fftFXBoundsHigh[i].toDouble())
//                    }
//                    fftFXBoundsHigh = FloatArray(FFT_FX_SIZE, { i -> maxAmp.toFloat() })
                }
            }

            override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
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
        val shouldRegister = intent.extras?.getInt(INTENT_KEY_COMMAND) == COMMAND_REGISTER_DEVICE
//        val device: BluetoothDevice = intent.extras?.get(INTENT_KEY_DEVICE)!! as BluetoothDevice
//        deviceLock.lock()
//        val hasDevice = devices.contains(device)
//        if (hasDevice != shouldRegister) {
//
//        }
//        deviceLock.unlock()

//        if (!shouldRegister) {
//            // If we want to unregister, don't bother doing anything unless we know the device
//            if (!hasDevice) {
//                return START_NOT_STICKY
//            }
//        }

//        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        serviceHandler?.obtainMessage()?.also { msg ->
            msg.arg1 = intent.extras?.getInt(INTENT_KEY_COMMAND)!!
            msg.obj = intent.extras?.get(INTENT_KEY_DEVICE)
            serviceHandler?.sendMessage(msg)
        }

//        val pendingIntent: PendingIntent =
//            Intent(this, MainActivity::class.java).let { notificationIntent ->
//                PendingIntent.getActivity(this, 0, notificationIntent, 0)
//            }

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
//            .setContentIntent(pendingIntent)
                .build()

            startForeground(10, notification)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
//        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
    }


//    private fun commandStart() {
//        if (!mServiceIsStarted) {
//            moveToStartedState()
//            return
//        }
//        if (mExecutor == null) {
//            mTimeRunning_sec = 0
//            if (isPreAndroidO()) {
//                HandleNotifications.PreO.createNotification(this)
//            } else {
//                HandleNotifications.O.createNotification(this)
//            }
//            acquireWakeLock()
//            mExecutor = Executors.newSingleThreadScheduledExecutor()
//            val runnable = Runnable { recurringTask() }
//            mExecutor.scheduleWithFixedDelay(runnable, DELAY_INITIAL, DELAY_RECURRING, DELAY_UNIT)
//            d(TAG, "commandStart: starting executor")
//        } else {
//            d(TAG, "commandStart: do nothing")
//        }
//    }

//    private var mExecutor: ScheduledExecutorService? = null
//    private var mServiceIsStarted = false
//
//    inner class LocalBinder : Binder() {
//        val service: BLEService
//            get() = this@BLEService
//    }
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return mBinder
//    }
//
//    private val mBinder: IBinder = LocalBinder()
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        var cmd = intent?.extras?.getInt("cmd")
//        mServiceIsStarted = true;
//        when (cmd) {
//            1 ->
//                ServiceN.createNotification(this);
//            2 ->
//                mServiceIsStarted = false;
//        }
//        return START_STICKY
////        return super.onStartCommand(intent, flags, startId)
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//    }


}


//object ServiceN {
//
//    /** PendingIntent to stop the service.  */
//    private fun getStopServicePI(context: Service): PendingIntent? {
//        var piStopService: PendingIntent?
//            val iStopService: Intent = Intent(context, BLEService::class.java) //MyIntentBuilder(context).setCommand(Command.STOP).build()
//            iStopService.putExtra("cmd", 2)
//            piStopService = PendingIntent.getService(context, 0, iStopService, 0)
//        return piStopService
//    }
//
//    /** Get pending intent to launch the activity.  */
//    private fun getLaunchActivityPI(context: Service): PendingIntent? {
//        var piLaunchMainActivity: PendingIntent?
//            val iLaunchMainActivity = Intent(context, MainActivity::class.java)
//            piLaunchMainActivity = PendingIntent.getActivity(context, 0, iLaunchMainActivity, 0)
//        return piLaunchMainActivity
//    }
//
//    val CHANNEL_ID: String = "AV"
//
//    fun createNotification(context: Service) {
//        val channelId = createChannel(context)
//        val notification: Notification = buildNotification(context, channelId)
//        context.startForeground(
//            0, notification
//        )
//    }
//
//    private fun buildNotification(
//        context: Service, channelId: String
//    ): Notification { // Create Pending Intents.
//        val piLaunchMainActivity: PendingIntent? = getLaunchActivityPI(context)
//        val piStopService: PendingIntent? = getStopServicePI(context)
//        // Action to stop the service.
//        val stopAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
//            R.drawable.ic_pause_circle_filled_24px,
//            context.getString(R.string.service_stop),
//            piStopService
//        )
//            .build()
//        // Create a notification.
//        return NotificationCompat.Builder(context, channelId)
//            .setContentTitle(context.getString(R.string.service_title))
//            .setContentText(context.getString(R.string.service_content))
//            .setSmallIcon(R.drawable.ic_equalizer_24px)
//            .setContentIntent(piLaunchMainActivity)
//            .addAction(stopAction)
//            .setStyle(NotificationCompat.BigTextStyle())
//            .build()
//    }
//
//    private fun createChannel(ctx: Service): String { // Create a channel.
//        val notificationManager =
//            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val channelName: CharSequence = "Playback channel"
//        val importance = NotificationManager.IMPORTANCE_DEFAULT
//        val notificationChannel = NotificationChannel(
//            CHANNEL_ID, channelName, importance
//        )
//        notificationManager.createNotificationChannel(
//            notificationChannel
//        )
//        return CHANNEL_ID
//    }
//}

//class HelloService : Service() {
//
//    private var serviceLooper: Looper? = null
//    private var serviceHandler: ServiceHandler? = null
//
//    // Handler that receives messages from the thread
//    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
//
//        override fun handleMessage(msg: Message) {
//            // Normally we would do some work here, like download a file.
//            // For our sample, we just sleep for 5 seconds.
//            try {
//                Thread.sleep(5000)
//            } catch (e: InterruptedException) {
//                // Restore interrupt status.
//                Thread.currentThread().interrupt()
//            }
//
//            // Stop the service using the startId, so that we don't stop
//            // the service in the middle of handling another job
//            stopSelf(msg.arg1)
//        }
//    }
//
//    override fun onCreate() {
//        // Start up the thread running the service.  Note that we create a
//        // separate thread because the service normally runs in the process's
//        // main thread, which we don't want to block.  We also make it
//        // background priority so CPU-intensive work will not disrupt our UI.
//        HandlerThread("ServiceStartArguments", THREAD_PRIORITY_BACKGROUND).apply {
//            start()
//
//            // Get the HandlerThread's Looper and use it for our Handler
//            serviceLooper = looper
//            serviceHandler = ServiceHandler(looper)
//        }
//    }
//
//    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
//        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()
//
//        // For each start request, send a message to start a job and deliver the
//        // start ID so we know which request we're stopping when we finish the job
//        serviceHandler?.obtainMessage()?.also { msg ->
//            msg.arg1 = startId
//            serviceHandler?.sendMessage(msg)
//        }
//
////        val pendingIntent: PendingIntent =
////            Intent(this, MainActivity::class.java).let { notificationIntent ->
////                PendingIntent.getActivity(this, 0, notificationIntent, 0)
////            }
//
//        val androidChannel = NotificationChannel(
//            "CHANNEL_DEFAULT_IMPORTANCE",
//            "CHANNEL_DEFAULT_IMPORTANCE", NotificationManager.IMPORTANCE_DEFAULT
//        )
//
//        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        service.createNotificationChannel(androidChannel)
//
//        val notification: Notification = Notification.Builder(this, "CHANNEL_DEFAULT_IMPORTANCE")
//            .setContentTitle(getText(R.string.service_title))
//            .setContentText(getText(R.string.service_content))
//            .setSmallIcon(R.drawable.ic_equalizer_24px)
////            .setContentIntent(pendingIntent)
//            .build()
//
//        startForeground(10, notification)
//
//        // If we get killed, after returning from here, restart
//        return START_STICKY
//    }
//
//    override fun onBind(intent: Intent): IBinder? {
//        // We don't provide binding, so return null
//        return null
//    }
//
//    override fun onDestroy() {
//        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
//    }
//}
