package com.kristjanskutta.gizmoled

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class VisualizerTestView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    var audioData: ByteArray? = null
    private var rectangle = Rect()
    private val paint = Paint()

    override fun onDraw(canvas: Canvas?) {
        canvas?.drawColor(Color.WHITE)

        if (audioData == null ||
            audioData!!.isEmpty()
        ) {
            return
        }

        val vAudioData = audioData!!

        val padding = (width * 0.1).toFloat()
        val elementWidth = (width - (padding * 2)) / vAudioData.size.toFloat()
        var posX = padding
        var colorStep = 1.0f / (vAudioData.size.toFloat() - 1)
        var colorBlend = 0.0f

        vAudioData.forEach { v ->
            val fValue = v.toUByte().toFloat() / 255.0f
            rectangle.left = posX.toInt()
            rectangle.top = padding.toInt()
            rectangle.right = (posX + elementWidth).toInt()
            rectangle.bottom = (padding + elementWidth).toInt()

            paint.color = Color.argb(
                fValue,
                1.0f,
                colorBlend,
                0.0f
            )

            canvas?.drawRect(rectangle, paint)
            posX += elementWidth
            colorBlend += colorStep
        }
    }
}

class DevVisualizerActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_RECORD_AUDIO = 6001
    private val CALLBACK_TAG = "devtest"
    private var visualizerView: VisualizerTestView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_visualizer_activty)
        visualizerView = findViewById(R.id.view_visualizer)
    }

    override fun onResume() {
        super.onResume()

        if (BLEService.needsPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, BLEService.getPermissionList(), PERMISSION_REQUEST_RECORD_AUDIO
            )
        } else {
            startVisualizer()
        }
    }

    override fun onPause() {
        super.onPause()
        // Disable stream
        Intent(this, BLEService::class.java).also { intent ->
            intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_UNREGISTER_CALLBACK)
            intent.putExtra(BLEService.INTENT_KEY_CALLBACK, CALLBACK_TAG)
            startService(intent)
        }
    }

    private fun startVisualizer() {
        BLEService.addDirectCallback(CALLBACK_TAG, audioListener)
        // Enable stream
        Intent(this, BLEService::class.java).also { intent ->
            intent.putExtra(BLEService.INTENT_KEY_COMMAND, BLEService.COMMAND_REGISTER_CALLBACK)
            intent.putExtra(BLEService.INTENT_KEY_CALLBACK, CALLBACK_TAG)
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            startVisualizer()
        }
    }

    private val audioListener = object : AudioListener {
        override fun receiveProcessedAudio(data: ByteArray) {
            runOnUiThread {
                visualizerView?.audioData = data
                visualizerView?.invalidate()
            }
        }
    }
}
