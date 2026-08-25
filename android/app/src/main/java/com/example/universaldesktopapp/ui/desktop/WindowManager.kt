package com.example.universaldesktopapp.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import java.util.UUID
import kotlin.math.roundToInt

data class WindowState(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val icon: String,
    val offsetX: Float = 36f,
    val offsetY: Float = 64f,
    val width: Float = 520f,
    val height: Float = 360f,
    val zIndex: Int = 0,
    val minimized: Boolean = false,
    val maximized: Boolean = false,
    val content: @Composable () -> Unit,
)

@Composable
fun WindowManager(
    windows: List<WindowState>,
    onUpdate: (WindowState) -> Unit,
    onClose: (WindowState) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 56.dp)) {
    val density = LocalDensity.current
    val areaWidthPx = with(density) { maxWidth.toPx() }
    val areaHeightPx = with(density) { maxHeight.toPx() }
    windows.filterNot { it.minimized }.sortedBy { it.zIndex }.forEach { window ->
        var dragX by remember(window.id, window.maximized) { mutableFloatStateOf(window.offsetX) }
        var dragY by remember(window.id, window.maximized) { mutableFloatStateOf(window.offsetY) }
        var frameWidth by remember(window.id, window.width) { mutableFloatStateOf(window.width) }
        var frameHeight by remember(window.id, window.height) { mutableFloatStateOf(window.height) }
        val frameModifier = if (window.maximized) {
            Modifier.fillMaxSize()
        } else {
            Modifier.offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .size(frameWidth.dp, frameHeight.dp)
        }

        Box(modifier = frameModifier.zIndex(window.zIndex.toFloat())
                .shadow(24.dp, MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(window.id, window.maximized) {
                        if (!window.maximized) detectDragGestures(
                            onDragStart = { onUpdate(window) },
                            onDragEnd = {
                                val snap = when {
                                    dragY <= 12f -> window.copy(offsetX = 0f, offsetY = 0f, maximized = true)
                                    dragX <= 12f -> window.copy(offsetX = 0f, offsetY = 0f, width = with(density) { (areaWidthPx / 2).toDp().value }, height = with(density) { areaHeightPx.toDp().value })
                                    dragX + with(density) { frameWidth.dp.toPx() } >= areaWidthPx - 12f -> window.copy(offsetX = areaWidthPx / 2, offsetY = 0f, width = with(density) { (areaWidthPx / 2).toDp().value }, height = with(density) { areaHeightPx.toDp().value })
                                    else -> window.copy(offsetX = dragX, offsetY = dragY, width = frameWidth, height = frameHeight)
                                }
                                onUpdate(snap)
                            },
                        ) { change, amount ->
                            change.consume()
                            dragX = (dragX + amount.x).coerceAtLeast(0f)
                            dragY = (dragY + amount.y).coerceAtLeast(0f)
                        }
                    }.padding(start = 12.dp).zIndex(2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ShellIcon(window.icon, Modifier.size(24.dp))
                Text(window.title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                WindowButton("—", false) { onUpdate(window.copy(minimized = true)) }
                WindowButton(if (window.maximized) "❐" else "□", false) { onUpdate(window.copy(maximized = !window.maximized)) }
                WindowButton("×", true) { onClose(window) }
            }
            Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background)) { window.content() }
        }
        if (!window.maximized) Box(
            Modifier.align(Alignment.BottomEnd).size(20.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f))
                .pointerInput(window.id) {
                    detectDragGestures(onDragEnd = { onUpdate(window.copy(width = frameWidth, height = frameHeight)) }) { change, amount ->
                        change.consume()
                        frameWidth = (frameWidth + amount.x / density.density).coerceAtLeast(320f)
                        frameHeight = (frameHeight + amount.y / density.density).coerceAtLeast(220f)
                    }
                }
        )
        }
    }
    }
}

@Composable
private fun WindowButton(label: String, destructive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Text(label, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
    }
}
