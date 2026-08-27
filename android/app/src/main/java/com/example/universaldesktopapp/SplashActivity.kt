package com.example.universaldesktopapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout

class SplashActivity : Activity(), TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private var desktopStarted = false
    private var player: MediaPlayer? = null
    private lateinit var textureView: TextureView
    private val fallback = Runnable { openDesktop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textureView = TextureView(this).apply {
            surfaceTextureListener = this@SplashActivity
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isOpaque = true
            setOnClickListener { openDesktop() }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(textureView)
        })
        handler.postDelayed(fallback, 10_000L)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val descriptor = resources.openRawResourceFd(R.raw.splash_screen)
        player = MediaPlayer().apply {
            setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            descriptor.close()
            setSurface(Surface(surfaceTexture))
            isLooping = false
            setVolume(1f, 1f)
            setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                applyCenterCrop(videoWidth, videoHeight)
            }
            setOnPreparedListener { start() }
            setOnCompletionListener { openDesktop() }
            setOnErrorListener { _, _, _ ->
                openDesktop()
                true
            }
            prepareAsync()
        }
    }

    private fun applyCenterCrop(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || textureView.width <= 0 || textureView.height <= 0) return
        val videoAspect = videoWidth.toFloat() / videoHeight
        val viewAspect = textureView.width.toFloat() / textureView.height
        textureView.pivotX = textureView.width / 2f
        textureView.pivotY = textureView.height / 2f
        if (videoAspect > viewAspect) {
            textureView.scaleX = videoAspect / viewAspect
            textureView.scaleY = 1f
        } else {
            textureView.scaleX = 1f
            textureView.scaleY = viewAspect / videoAspect
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        player?.let { applyCenterCrop(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun openDesktop() {
        if (desktopStarted || isFinishing) return
        desktopStarted = true
        handler.removeCallbacks(fallback)
        releasePlayer()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun releasePlayer() {
        player?.runCatching { release() }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(fallback)
        releasePlayer()
        super.onDestroy()
    }
}
