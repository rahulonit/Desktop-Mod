package com.example.universaldesktopapp.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
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

class DesktopSession(
    private val context: Context,
    private val displayManager: DisplayManager,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val densityDpi: Int = 160,
    private val jpegQuality: Int = 94,
    private val maxFramesPerSecond: Int = 60,
) {
    private object MouseAction {
        const val MOVE = 0
        const val DOWN = 1
        const val UP = 2
        const val SCROLL_UP = 3
        const val SCROLL_DOWN = 4
        const val CONTEXT_MENU = 5
        const val CONTEXT_MENU_RELEASE = 6
    }
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: DesktopPresentation? = null
    private var encoder: MediaCodec? = null
    private var captureThread: HandlerThread? = null
    @Volatile private var isStreaming = false

    fun startSession(onFrameReady: (ByteArray) -> Unit) {
        if (isStreaming) return
        isStreaming = true

        val thread = HandlerThread("DesktopH264Encoder").also { it.start() }
        captureThread = thread
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder = codec
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (jpegQuality >= 90) 12_000_000 else 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, maxFramesPerSecond.coerceIn(15, 60))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        virtualDisplay = displayManager.createVirtualDisplay(
            "DesktopMod",
            width,
            height,
            densityDpi,
            inputSurface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )

        val handler = Handler(thread.looper)
        val drain = object : Runnable {
            override fun run() {
                val activeCodec = encoder ?: return
                val info = MediaCodec.BufferInfo()
                try {
                    while (isStreaming) {
                        val index = activeCodec.dequeueOutputBuffer(info, 0)
                        if (index < 0) break
                        activeCodec.getOutputBuffer(index)?.let { buffer ->
                            if (info.size > 0) {
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size); buffer.get(bytes)
                                onFrameReady(bytes)
                            }
                        }
                        activeCodec.releaseOutputBuffer(index, false)
                    }
                } catch (error: Exception) {
                    if (isStreaming) Log.e("DesktopSession", "H.264 encoding failed", error)
                }
                if (isStreaming) handler.postDelayed(this, 4)
            }
        }
        handler.post(drain)

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
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
            presentation?.dismiss()
            presentation = null
            releaseCaptureResources()
        }
    }

    private fun releaseCaptureResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        encoder?.runCatching { signalEndOfInputStream(); stop(); release() }
        encoder = null
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
        if (action == MouseAction.CONTEXT_MENU) {
            DesktopInputBus.showDesktopMenu(x.toInt(), y.toInt())
            return
        }
        if (action == MouseAction.CONTEXT_MENU_RELEASE) return
        if (action == MouseAction.SCROLL_UP || action == MouseAction.SCROLL_DOWN) {
            val properties = arrayOf(MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            })
            val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                setAxisValue(MotionEvent.AXIS_VSCROLL, if (action == MouseAction.SCROLL_UP) 1f else -1f)
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
            MouseAction.MOVE -> MotionEvent.ACTION_MOVE
            MouseAction.DOWN -> MotionEvent.ACTION_DOWN
            MouseAction.UP -> MotionEvent.ACTION_UP
            else -> return
        }
        val event = MotionEvent.obtain(now, now, androidAction, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
