package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureManager private constructor() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopCapture()
        }
    }

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection?.stop()
        mediaProjection = projection
        mediaProjection?.registerCallback(projectionCallback, handler)
        isProjectionActive = true
    }

    fun startCapture(
        context: Context,
        targetWidth: Int = 720,
        targetHeight: Int = 1600,
        fps: Int = 30
    ): Boolean {
        val proj = mediaProjection ?: return false

        try {
            stopVirtualDisplay()

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = if (targetWidth > 0) targetWidth else metrics.widthPixels
            val height = if (targetHeight > 0) targetHeight else metrics.heightPixels
            val dpi = metrics.densityDpi

            // Using ImageReader for low-latency frame extraction
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val surface: Surface = imageReader!!.surface

            virtualDisplay = proj.createVirtualDisplay(
                "UbaidVirtualDisplay",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                handler
            )

            _isStreaming.value = true
            return true
        } catch (e: Exception) {
            _isStreaming.value = false
            return false
        }
    }

    fun stopCapture() {
        stopVirtualDisplay()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        isProjectionActive = false
        _isStreaming.value = false
    }

    private fun stopVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    companion object {
        val instance by lazy { ScreenCaptureManager() }
        var isProjectionActive: Boolean = false
            private set
    }
}
