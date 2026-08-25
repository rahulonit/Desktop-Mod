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
        val receiverTransport = MutableStateFlow<String?>(null)
        val nextTransportHint = MutableStateFlow<String?>(null)
        private var activeService: UsbService? = null

        fun disconnectReceiver() {
            activeService?.closeReceiverSession()
        }
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var clientSocket: Socket? = null
    private var activeSession: com.example.universaldesktopapp.engine.DesktopSession? = null
    private val clientLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeService = this
        if (!isRunning) {
            isRunning = true
            startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        thread {
            try {
                // Listen on all interfaces for direct USB-tethering and Wi-Fi receiver sessions.
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
                val transportHint = nextTransportHint.value
                nextTransportHint.value = null
                val wirelessTransport = transportHint != "USB" && !socket.inetAddress.isLoopbackAddress
                socket.tcpNoDelay = true
                socket.sendBufferSize = if (wirelessTransport) 192 * 1024 else 512 * 1024
                val outputStream = java.io.DataOutputStream(socket.getOutputStream())
                val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                
                desktopSession = com.example.universaldesktopapp.engine.DesktopSession(
                    context = this,
                    displayManager = displayManager,
                    jpegQuality = if (wirelessTransport) 76 else 94,
                    maxFramesPerSecond = if (wirelessTransport) 24 else 60,
                )
                synchronized(clientLock) {
                    if (clientSocket === socket) activeSession = desktopSession
                }
                isReceiverConnected.value = true
                receiverTransport.value = transportHint ?: if (wirelessTransport) "Wireless" else "USB"
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
                
                val inputStream = java.io.DataInputStream(socket.getInputStream())
                while (!socket.isClosed) {
                    val packet = com.example.universaldesktopapp.protocol.Packet.deserialize(inputStream) ?: break
                    desktopSession.handleInput(packet)
                }
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
                        receiverTransport.value = null
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        isRunning = false
        serverSocket?.close()
        clientSocket?.close()
        activeSession?.stopSession()
        isReceiverConnected.value = false
        receiverTransport.value = null
        super.onDestroy()
    }

    private fun closeReceiverSession() {
        synchronized(clientLock) {
            clientSocket?.close()
            activeSession?.stopSession()
            clientSocket = null
            activeSession = null
            isReceiverConnected.value = false
            receiverTransport.value = null
        }
    }
}
