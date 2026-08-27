import Foundation

public enum PacketType: UInt8, Sendable {
    case handshake = 0x01
    case pairingRequest = 0x02
    case pairingResponse = 0x03
    case videoFrame = 0x04
    case mouseEvent = 0x05
    case keyEvent = 0x06
    case clipboard = 0x07
    case fileMetadata = 0x08
    case fileChunk = 0x09
    case fileComplete = 0x0A
}

public struct Packet: Equatable, Sendable {
    public static let headerSize = 5
    public static let maximumPayloadSize = 32 * 1_024 * 1_024

    public let type: PacketType
    public let payload: Data

    public init(type: PacketType, payload: Data = Data()) {
        self.type = type
        self.payload = payload
    }

    /// Encodes the Android-compatible wire format: a big-endian UInt32 length,
    /// one packet-type byte, then `length` payload bytes.
    public func encoded() throws -> Data {
        guard payload.count <= Self.maximumPayloadSize else { throw PacketError.payloadTooLarge(payload.count) }
        var length = UInt32(payload.count).bigEndian
        var data = Data(bytes: &length, count: MemoryLayout<UInt32>.size)
        data.append(type.rawValue)
        data.append(payload)
        return data
    }
}

public enum PacketError: Error, Equatable {
    case payloadTooLarge(Int)
    case unknownType(UInt8)
}

public struct PacketDecoder: Sendable {
    private var buffer = Data()

    public init() {}

    public mutating func append(_ data: Data) throws -> [Packet] {
        buffer.append(data)
        var packets: [Packet] = []

        while buffer.count >= Packet.headerSize {
            let length = buffer.prefix(4).reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
            guard length <= Packet.maximumPayloadSize else { throw PacketError.payloadTooLarge(Int(length)) }
            let packetSize = Packet.headerSize + Int(length)
            guard buffer.count >= packetSize else { break }
            let typeByte = buffer[buffer.index(buffer.startIndex, offsetBy: 4)]
            guard let type = PacketType(rawValue: typeByte) else { throw PacketError.unknownType(typeByte) }
            let payloadStart = buffer.index(buffer.startIndex, offsetBy: Packet.headerSize)
            let payloadEnd = buffer.index(payloadStart, offsetBy: Int(length))
            packets.append(Packet(type: type, payload: buffer[payloadStart..<payloadEnd]))
            buffer.removeFirst(packetSize)
        }
        return packets
    }
}
