package com.example.universaldesktopapp.ui.apps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImagePreviewApp(initialFile: File? = null) {
    val context = LocalContext.current
    var source by remember(initialFile) { mutableStateOf(initialFile?.let(Uri::fromFile)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(source) {
        bitmap = null; error = null; scale = 1f; offsetX = 0f; offsetY = 0f
        source?.let { uri ->
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read image")
                    decodeImage(bytes) ?: error("Unsupported or damaged image")
                }
            }
            result.onSuccess { bitmap = it }.onFailure { error = it.message }
        }
    }
    val displayedBitmap = bitmap
    DisposableEffect(displayedBitmap) {
        onDispose { displayedBitmap?.takeUnless { it.isRecycled }?.recycle() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(source?.lastPathSegment ?: "Image Preview", Modifier.padding(start = 12.dp).weight(1f), maxLines = 1)
            TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) { Text("Reset") }
            Text("${(scale * 100).toInt()}%")
        }
        HorizontalDivider()
        Box(Modifier.fillMaxSize().clipToBounds().background(Color(0xFF202124)), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxSize().pointerInput(it) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(.25f, 8f); offsetX += pan.x; offsetY += pan.y
                        }
                    }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                    contentScale = ContentScale.Fit,
                )
            }
            if (source == null) Text("Open an image from File Explorer", color = Color.White)
            error?.let { Text(it, color = Color(0xFFFFB4AB)) }
        }
    }
}

private fun decodeImage(bytes: ByteArray): Bitmap? {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
    if (bytes.size < 22 || bytes[0].toInt() != 0 || bytes[1].toInt() != 0 || bytes[2].toInt() != 1) return null
    val count = (bytes[4].toInt() and 255) or ((bytes[5].toInt() and 255) shl 8)
    var bestOffset = 0; var bestSize = 0; var bestArea = -1
    repeat(count) { index ->
        val entry = 6 + index * 16
        if (entry + 16 <= bytes.size) {
            val width = if (bytes[entry].toInt() == 0) 256 else bytes[entry].toInt() and 255
            val height = if (bytes[entry + 1].toInt() == 0) 256 else bytes[entry + 1].toInt() and 255
            fun int32(at: Int) = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8) or ((bytes[at + 2].toInt() and 255) shl 16) or ((bytes[at + 3].toInt() and 255) shl 24)
            val size = int32(entry + 8); val offset = int32(entry + 12)
            if (width * height > bestArea && offset >= 0 && size > 0 && offset + size <= bytes.size) { bestArea = width * height; bestOffset = offset; bestSize = size }
        }
    }
    return if (bestSize > 0) BitmapFactory.decodeByteArray(bytes, bestOffset, bestSize) else null
}
