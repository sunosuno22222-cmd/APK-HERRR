package com.example.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val metrics = DisplayMetrics()
    
    init {
        windowManager.defaultDisplay.getRealMetrics(metrics)
    }

    fun onActivityResult(resultCode: Int, data: Intent) {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
    }

    fun captureContent(callback: (Bitmap?) -> Unit) {
        val mp = mediaProjection ?: return callback(null)
        
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mp.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop the bitmap to remove padding
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                
                image.close()
                stopCapture()
                callback(croppedBitmap)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader = null
    }

    fun isReady() = mediaProjection != null
}
