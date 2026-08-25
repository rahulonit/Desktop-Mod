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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
    windows.filterNot { it.minimized }.sortedBy { it.zIndex }.forEach { window ->
        var dragX by remember(window.id, window.maximized) { mutableFloatStateOf(window.offsetX) }
        var dragY by remember(window.id, window.maximized) { mutableFloatStateOf(window.offsetY) }
        val frameModifier = if (window.maximized) {
            Modifier.fillMaxSize().padding(bottom = 56.dp)
        } else {
            Modifier.offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .size(window.width.dp, window.height.dp)
        }

        Column(
            modifier = frameModifier.zIndex(window.zIndex.toFloat())
                .clip(MaterialTheme.shapes.medium).background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFF475569), MaterialTheme.shapes.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF172033))
                    .pointerInput(window.id, window.maximized) {
                        if (!window.maximized) detectDragGestures(
                            onDragStart = { onUpdate(window) },
                            onDragEnd = { onUpdate(window.copy(offsetX = dragX, offsetY = dragY)) },
                        ) { change, amount ->
                            change.consume()
                            dragX = (dragX + amount.x).coerceAtLeast(0f)
                            dragY = (dragY + amount.y).coerceAtLeast(0f)
                        }
                    }.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("${window.icon}  ${window.title}", color = Color.White, modifier = Modifier.weight(1f))
                WindowButton("—") { onUpdate(window.copy(minimized = true)) }
                WindowButton(if (window.maximized) "❐" else "□") { onUpdate(window.copy(maximized = !window.maximized)) }
                WindowButton("×") { onClose(window) }
            }
            Box(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) { window.content() }
        }
    }
}

@Composable
private fun WindowButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
