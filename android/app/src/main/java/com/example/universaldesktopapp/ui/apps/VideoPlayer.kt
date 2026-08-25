package com.example.universaldesktopapp.ui.apps

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun VideoPlayerApp(initialFile: File? = null) {
    val context = LocalContext.current
    var source by remember(initialFile) { mutableStateOf(initialFile?.let(Uri::fromFile)) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoView?.stopPlayback() }
            videoView = null
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { viewContext ->
                VideoView(viewContext).apply {
                    videoView = this
                    val controls = MediaController(viewContext)
                    controls.setAnchorView(this)
                    setMediaController(controls)
                    setOnErrorListener { _, what, extra -> error = "Video codec/container is not supported ($what/$extra)"; true }
                }
            }, update = { view -> source?.let { if (view.tag != it) { view.tag = it; view.setVideoURI(it); view.start() } } }, modifier = Modifier.fillMaxSize())
            if (source == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Open a video from File Explorer", color = Color.White)
                }
            }
            error?.let { Text(it, color = Color(0xFFFFB4AB)) }
    }
}
