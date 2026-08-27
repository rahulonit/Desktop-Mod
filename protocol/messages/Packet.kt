package com.example.universaldesktopapp.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

enum class PacketType(val value: Byte) {
    Handshake(0x01),
    PairingRequest(0x02),
    PairingResponse(0x03),
    VideoFrame(0x04),
    MouseEvent(0x05),
    KeyEvent(0x06),
    Clipboard(0x07),
    FileMetadata(0x08),
    FileChunk(0x09),
    FileComplete(0x0A);

    companion object {
        fun fromValue(value: Byte): PacketType? = entries.find { it.value == value }
    }
}

class Packet(val type: PacketType, val payload: ByteArray) {
    val length: Int = payload.size

    fun serialize(outputStream: DataOutputStream) {
        outputStream.writeInt(length)
        outputStream.writeByte(type.value.toInt())
        outputStream.write(payload)
    }

    companion object {
        private const val MAX_PAYLOAD_SIZE = 32 * 1024 * 1024

        fun deserialize(inputStream: DataInputStream): Packet? {
            val length = try { inputStream.readInt() } catch (_: EOFException) { return null }
            require(length in 0..MAX_PAYLOAD_SIZE) { "Invalid packet length: $length" }
            val typeByte = inputStream.readByte()
            val type = requireNotNull(PacketType.fromValue(typeByte)) { "Unknown packet type: $typeByte" }
            return Packet(type, ByteArray(length).also(inputStream::readFully))
        }
    }
}
