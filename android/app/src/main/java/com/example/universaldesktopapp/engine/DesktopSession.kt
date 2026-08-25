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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.universaldesktopapp.protocol.Packet
import com.example.universaldesktopapp.protocol.PacketType
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream

class DesktopSession(
    private val context: Context,
    private val displayManager: DisplayManager,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val densityDpi: Int = 160,
    private val jpegQuality: Int = 94,
    private val maxFramesPerSecond: Int = 60,
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: DesktopPresentation? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    @Volatile private var isStreaming = false
    private var lastEncodedFrameAt = 0L

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
                if (!isStreaming) return@setOnImageAvailableListener
                val now = android.os.SystemClock.elapsedRealtime()
                val minimumFrameInterval = 1_000L / maxFramesPerSecond.coerceAtLeast(1)
                if (now - lastEncodedFrameAt < minimumFrameInterval) return@setOnImageAvailableListener
                lastEncodedFrameAt = now

                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val frame = if (paddedWidth == width) padded
                    else Bitmap.createBitmap(padded, 0, 0, width, height)

                val bytes = ByteArrayOutputStream().use { output ->
                    frame.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(55, 96), output)
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

        attachPresentationWhenReady()
    }

    private fun attachPresentationWhenReady(attempt: Int = 0) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isStreaming || presentation != null) return@postDelayed
            val display = virtualDisplay?.display
            if (display != null && display.isValid) {
                runCatching {
                    presentation = DesktopPresentation(context, display).also { it.show() }
                }.onFailure { error ->
                    Log.e("DesktopSession", "Could not show independent desktop", error)
                    presentation = null
                    if (attempt < 20) attachPresentationWhenReady(attempt + 1)
                }
            } else if (attempt < 20) {
                attachPresentationWhenReady(attempt + 1)
            } else {
                Log.e("DesktopSession", "Virtual display was not ready after 2 seconds")
            }
        }, if (attempt == 0) 0L else 100L)
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

    fun handleInput(packet: Packet) {
        when (packet.type) {
            PacketType.MouseEvent -> {
                if (packet.payload.size < 12) return
                val data = ByteBuffer.wrap(packet.payload)
                val x = data.int.coerceIn(0, width - 1).toFloat()
                val y = data.int.coerceIn(0, height - 1).toFloat()
                val action = data.int
                Handler(Looper.getMainLooper()).post { dispatchPointer(x, y, action) }
            }
            PacketType.KeyEvent -> {
                if (packet.payload.size < 5) return
                val data = ByteBuffer.wrap(packet.payload)
                val keyCode = data.int
                val isDown = data.get().toInt() != 0
                Handler(Looper.getMainLooper()).post {
                    val event = KeyEvent(if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode)
                    presentation?.window?.decorView?.dispatchKeyEvent(event)
                }
            }
            else -> Unit
        }
    }

    private fun dispatchPointer(x: Float, y: Float, action: Int) {
        val view = presentation?.window?.decorView ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (action == 5) {
            DesktopInputBus.showDesktopMenu(x.toInt(), y.toInt())
            return
        }
        if (action == 6) return
        if (action == 3 || action == 4) {
            val properties = arrayOf(MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            })
            val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                setAxisValue(MotionEvent.AXIS_VSCROLL, if (action == 3) 1f else -1f)
            })
            val scrollEvent = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL, 1, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
            )
            view.dispatchGenericMotionEvent(scrollEvent)
            scrollEvent.recycle()
            return
        }
        val androidAction = when (action) {
            0 -> MotionEvent.ACTION_MOVE
            1 -> MotionEvent.ACTION_DOWN
            2 -> MotionEvent.ACTION_UP
            else -> return
        }
        val event = MotionEvent.obtain(now, now, androidAction, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
