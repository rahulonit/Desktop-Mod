package com.example.universaldesktopapp.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import android.util.Log
import kotlin.concurrent.thread

class DesktopSession(
    private val context: Context,
    private val displayManager: DisplayManager,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val densityDpi: Int = 320
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: DesktopPresentation? = null
    private var mediaCodec: MediaCodec? = null
    private var isStreaming = false

    fun startSession(onFrameReady: (ByteArray) -> Unit) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        val inputSurface = mediaCodec?.createInputSurface() ?: return
        mediaCodec?.start()

        virtualDisplay = displayManager.createVirtualDisplay(
            "UniversalDesktop",
            width,
            height,
            densityDpi,
            inputSurface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )

        virtualDisplay?.display?.let {
            // Need to run on UI thread
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                presentation = DesktopPresentation(context, it)
                presentation?.show()
            }
        }

        isStreaming = true
        startEncodingLoop(onFrameReady)
    }

    private fun startEncodingLoop(onFrameReady: (ByteArray) -> Unit) {
        thread {
            val bufferInfo = MediaCodec.BufferInfo()
            try {
                while (isStreaming) {
                    val codec = mediaCodec ?: break
                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferId >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val frameData = ByteArray(bufferInfo.size)
                            outputBuffer.get(frameData)
                            onFrameReady(frameData)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            } catch (error: IllegalStateException) {
                if (isStreaming) Log.e("DesktopSession", "Encoder stopped unexpectedly", error)
            }
        }
    }

    fun stopSession() {
        isStreaming = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            presentation?.dismiss()
            presentation = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null
    }
}
