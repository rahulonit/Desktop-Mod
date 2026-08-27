package com.example.universaldesktopapp.usb

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.IBinder
import android.os.Build
import android.util.Log
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class UsbService : Service() {
    companion object {
        val isReceiverConnected = MutableStateFlow(false)
        val receiverTransport = MutableStateFlow<String?>(null)
        val nextTransportHint = MutableStateFlow<String?>(null)
        val pairingCode = MutableStateFlow("------")
        private var activeService: UsbService? = null

        fun disconnectReceiver() {
            activeService?.closeReceiverSession()
        }

        fun sendFile(file: java.io.File): Boolean = activeService?.queueFile(file) ?: false
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var clientSocket: Socket? = null
    private var activeSession: com.example.universaldesktopapp.engine.DesktopSession? = null
    private val clientLock = Any()
    @Volatile private var outgoingPacketSink: ((com.example.universaldesktopapp.protocol.Packet) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        pairingCode.value = PairingSecurity.newCode()
        val channelId = "desktop_transport"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Desktop connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, com.example.universaldesktopapp.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Desktop Mod is ready")
            .setContentText("Waiting for a desktop receiver")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        startForeground(5000, notification)
    }

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
            val transfers = FileTransferSession(this)
            var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
            try {
                val transportHint = nextTransportHint.value
                nextTransportHint.value = null
                val wirelessTransport = transportHint != "USB" && !socket.inetAddress.isLoopbackAddress
                socket.tcpNoDelay = true
                socket.sendBufferSize = if (wirelessTransport) 192 * 1024 else 512 * 1024
                val outputStream = java.io.DataOutputStream(socket.getOutputStream())
                val inputStream = java.io.DataInputStream(socket.getInputStream())
                val outputLock = Any()
                fun send(packet: com.example.universaldesktopapp.protocol.Packet) = synchronized(outputLock) {
                    packet.serialize(outputStream); outputStream.flush()
                }

                val challenge = PairingSecurity.newChallenge()
                send(com.example.universaldesktopapp.protocol.Packet(com.example.universaldesktopapp.protocol.PacketType.Handshake, challenge))
                val response = com.example.universaldesktopapp.protocol.Packet.deserialize(inputStream)
                val authenticated = response?.type == com.example.universaldesktopapp.protocol.PacketType.PairingResponse &&
                    PairingSecurity.verify(pairingCode.value, challenge, response.payload)
                send(com.example.universaldesktopapp.protocol.Packet(
                    com.example.universaldesktopapp.protocol.PacketType.PairingResponse,
                    byteArrayOf(if (authenticated) 1 else 0),
                ))
                if (!authenticated) throw SecurityException("Receiver pairing failed")
                outgoingPacketSink = ::send

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
                            "H264".toByteArray(Charsets.US_ASCII) + frameData
                        )
                        send(packet)
                    } catch (e: Exception) {
                        Log.e("UsbService", "Error writing frame packet", e)
                        socket.close()
                    }
                }
                
                val clipboard = getSystemService(ClipboardManager::class.java)
                val suppressClipboardEcho = AtomicBoolean(false)
                clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                    if (suppressClipboardEcho.compareAndSet(true, false)) return@OnPrimaryClipChangedListener
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
                    runCatching { send(com.example.universaldesktopapp.protocol.Packet(com.example.universaldesktopapp.protocol.PacketType.Clipboard, text.toByteArray())) }
                }.also(clipboard::addPrimaryClipChangedListener)
                while (!socket.isClosed) {
                    val packet = com.example.universaldesktopapp.protocol.Packet.deserialize(inputStream) ?: break
                    when (packet.type) {
                        com.example.universaldesktopapp.protocol.PacketType.Clipboard -> {
                            suppressClipboardEcho.set(true)
                            val text = packet.payload.toString(Charsets.UTF_8)
                            android.os.Handler(mainLooper).post { clipboard.setPrimaryClip(ClipData.newPlainText("Desktop Mod", text)) }
                        }
                        com.example.universaldesktopapp.protocol.PacketType.FileMetadata,
                        com.example.universaldesktopapp.protocol.PacketType.FileChunk,
                        com.example.universaldesktopapp.protocol.PacketType.FileComplete -> transfers.receive(packet)
                        else -> desktopSession.handleInput(packet)
                    }
                }
            } catch (e: Exception) {
                if (!socket.isClosed) Log.e("UsbService", "Client error", e)
            } finally {
                outgoingPacketSink = null
                clipboardListener?.let { getSystemService(ClipboardManager::class.java).removePrimaryClipChangedListener(it) }
                transfers.close()
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

    private fun queueFile(file: java.io.File): Boolean {
        val sink = outgoingPacketSink ?: return false
        thread(name = "DesktopModFileSend", isDaemon = true) {
            runCatching { FileTransferSession(this).use { transfer -> transfer.packets(file).forEach(sink) } }
                .onFailure { Log.e("UsbService", "File transfer failed", it) }
        }
        return true
    }
}
