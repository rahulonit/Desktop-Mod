package com.example.universaldesktopapp.usb

import android.content.Context
import com.example.universaldesktopapp.protocol.Packet
import com.example.universaldesktopapp.protocol.PacketType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class FileTransferSession(private val context: Context) : AutoCloseable {
    private val incoming = ConcurrentHashMap<UUID, FileOutputStream>()

    fun receive(packet: Packet) {
        when (packet.type) {
            PacketType.FileMetadata -> receiveMetadata(packet.payload)
            PacketType.FileChunk -> withId(packet.payload) { id, input -> incoming[id]?.write(input.readBytes()) }
            PacketType.FileComplete -> withId(packet.payload) { id, _ -> incoming.remove(id)?.close() }
            else -> Unit
        }
    }

    fun packets(file: File, chunkSize: Int = 64 * 1024): Sequence<Packet> = sequence {
        val id = UUID.randomUUID()
        val name = file.name.toByteArray(Charsets.UTF_8)
        val metadata = java.io.ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeLong(id.mostSignificantBits); out.writeLong(id.leastSignificantBits)
                out.writeShort(name.size); out.write(name); out.writeLong(file.length())
            }
        }.toByteArray()
        yield(Packet(PacketType.FileMetadata, metadata))
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val payload = java.io.ByteArrayOutputStream(16 + count).also { bytes ->
                    DataOutputStream(bytes).use { out ->
                        out.writeLong(id.mostSignificantBits); out.writeLong(id.leastSignificantBits)
                        out.write(buffer, 0, count)
                    }
                }.toByteArray()
                yield(Packet(PacketType.FileChunk, payload))
            }
        }
        val done = java.io.ByteArrayOutputStream(16).also { bytes ->
            DataOutputStream(bytes).use { it.writeLong(id.mostSignificantBits); it.writeLong(id.leastSignificantBits) }
        }.toByteArray()
        yield(Packet(PacketType.FileComplete, done))
    }

    private fun receiveMetadata(payload: ByteArray) = withId(payload) { id, input ->
        val nameLength = input.readUnsignedShort()
        require(nameLength in 1..4096)
        val rawName = ByteArray(nameLength).also(input::readFully).toString(Charsets.UTF_8)
        input.readLong() // declared size, retained for protocol validation/telemetry
        val safeName = File(rawName).name.ifBlank { "received-file" }
        val directory = File(context.getExternalFilesDir(null), "Received").apply { mkdirs() }
        incoming.remove(id)?.close()
        incoming[id] = FileOutputStream(uniqueFile(directory, safeName))
    }

    private inline fun withId(payload: ByteArray, block: (UUID, DataInputStream) -> Unit) {
        require(payload.size >= 16)
        DataInputStream(payload.inputStream()).use { input -> block(UUID(input.readLong(), input.readLong()), input) }
    }

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        var suffix = 1
        while (candidate.exists()) candidate = File(directory, "${candidate.nameWithoutExtension} ($suffix).${candidate.extension}").also { suffix++ }
        return candidate
    }

    override fun close() { incoming.values.forEach { runCatching { it.close() } }; incoming.clear() }
}
