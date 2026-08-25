package com.example.universaldesktopapp.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.example.universaldesktopapp.protocol.Packet
import java.nio.ByteBuffer

class InputManager : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not required for injection
    }

    override fun onInterrupt() {}

    fun handlePacket(packet: Packet) {
        if (packet.type == com.example.universaldesktopapp.protocol.PacketType.MouseEvent) {
            val buffer = ByteBuffer.wrap(packet.payload)
            val x = buffer.getInt()
            val y = buffer.getInt()
            val buttonState = buffer.getInt()

            // Map buttonState to click gesture (simplified for MVP)
            if (buttonState == 1) { // Left click simulated as tap
                dispatchClick(x.toFloat(), y.toFloat())
            }
        }
    }

    private fun dispatchClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
            
        dispatchGesture(gesture, null, null)
    }
}
