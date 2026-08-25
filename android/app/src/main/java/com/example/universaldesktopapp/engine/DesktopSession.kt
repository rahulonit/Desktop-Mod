package com.example.universaldesktopapp.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream

class DesktopSession(
    private val context: Context,
    private val displayManager: DisplayManager,
    private val width: Int = 1280,
    private val height: Int = 720,
    private val densityDpi: Int = 240
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: DesktopPresentation? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    @Volatile private var isStreaming = false
    private var lastFrameAt = 0L

    fun startSession(onFrameReady: (ByteArray) -> Unit) {
        if (isStreaming) return
        isStreaming = true

        val thread = HandlerThread("DesktopFrameCapture").also { it.start() }
        captureThread = thread
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (!isStreaming || now - lastFrameAt < 66L) return@setOnImageAvailableListener
                lastFrameAt = now

                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val frame = if (paddedWidth == width) padded
                    else Bitmap.createBitmap(padded, 0, 0, width, height)

                val bytes = ByteArrayOutputStream().use { output ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 75, output)
                    output.toByteArray()
                }
                if (frame !== padded) frame.recycle()
                padded.recycle()
                if (isStreaming && bytes.isNotEmpty()) onFrameReady(bytes)
            } catch (error: Exception) {
                if (isStreaming) Log.e("DesktopSession", "Frame capture failed", error)
            } finally {
                image.close()
            }
        }, Handler(thread.looper))

        virtualDisplay = displayManager.createVirtualDisplay(
            "DesktopMod",
            width,
            height,
            densityDpi,
            reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )

        virtualDisplay?.display?.let { display ->
            Handler(Looper.getMainLooper()).post {
                if (isStreaming) {
                    presentation = DesktopPresentation(context, display)
                    presentation?.show()
                }
            }
        }
    }

    fun stopSession() {
        isStreaming = false
        Handler(Looper.getMainLooper()).post {
            presentation?.dismiss()
            presentation = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        captureThread?.quitSafely()
        captureThread = null
    }
}
