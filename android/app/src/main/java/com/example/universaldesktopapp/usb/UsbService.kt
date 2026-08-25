package com.example.universaldesktopapp.usb

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow

class UsbService : Service() {
    companion object {
        val isReceiverConnected = MutableStateFlow(false)
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var clientSocket: Socket? = null
    private var activeSession: com.example.universaldesktopapp.engine.DesktopSession? = null
    private val clientLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        thread {
            try {
                // Listen on a port that can be forwarded via ADB
                serverSocket = ServerSocket(5000)
                Log.i("UsbService", "Server listening on port 5000")

                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    Log.i("UsbService", "Client connected: ${client.inetAddress}")
                    synchronized(clientLock) {
                        clientSocket?.close()
                        activeSession?.stopSession()
                        clientSocket = client
                        activeSession = null
                    }
                    handleClient(client)
                }
            } catch (e: Exception) {
                Log.e("UsbService", "Server error", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        thread {
            var desktopSession: com.example.universaldesktopapp.engine.DesktopSession? = null
            try {
                val outputStream = java.io.DataOutputStream(socket.getOutputStream())
                val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                
                desktopSession = com.example.universaldesktopapp.engine.DesktopSession(this, displayManager)
                synchronized(clientLock) {
                    if (clientSocket === socket) activeSession = desktopSession
                }
                isReceiverConnected.value = true
                desktopSession.startSession { frameData ->
                    try {
                        val packet = com.example.universaldesktopapp.protocol.Packet(
                            com.example.universaldesktopapp.protocol.PacketType.VideoFrame,
                            frameData
                        )
                        packet.serialize(outputStream)
                    } catch (e: Exception) {
                        Log.e("UsbService", "Error writing frame packet", e)
                        socket.close()
                    }
                }
                
                socket.getInputStream().read()
            } catch (e: Exception) {
                if (!socket.isClosed) Log.e("UsbService", "Client error", e)
            } finally {
                desktopSession?.stopSession()
                socket.close()
                synchronized(clientLock) {
                    if (clientSocket === socket) {
                        clientSocket = null
                        activeSession = null
                        isReceiverConnected.value = false
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        serverSocket?.close()
        clientSocket?.close()
        activeSession?.stopSession()
        isReceiverConnected.value = false
        super.onDestroy()
    }
}
