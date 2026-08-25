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
    KeyEvent(0x06);

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
        fun deserialize(inputStream: DataInputStream): Packet? {
            return try {
                val length = inputStream.readInt()
                val typeByte = inputStream.readByte()
                val payload = ByteArray(length)
                inputStream.readFully(payload)
                
                val type = PacketType.fromValue(typeByte) ?: return null
                Packet(type, payload)
            } catch (e: EOFException) {
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
