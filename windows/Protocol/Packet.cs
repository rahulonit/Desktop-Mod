using System;
using System.Buffers.Binary;
using System.IO;

namespace UniversalMobileDesktop.Protocol
{
    public enum PacketType : byte
    {
        Handshake = 0x01,
        PairingRequest = 0x02,
        PairingResponse = 0x03,
        VideoFrame = 0x04,
        MouseEvent = 0x05,
        KeyEvent = 0x06,
        Clipboard = 0x07,
        FileMetadata = 0x08,
        FileChunk = 0x09,
        FileComplete = 0x0A
    }

    public class Packet
    {
        public int Length { get; set; }
        public PacketType Type { get; set; }
        public byte[] Payload { get; set; }

        public Packet(PacketType type, byte[] payload)
        {
            Type = type;
            Payload = payload ?? Array.Empty<byte>();
            Length = Payload.Length;
        }

        public byte[] Serialize()
        {
            using var ms = new MemoryStream();
            using var writer = new BinaryWriter(ms);

            Span<byte> lengthBytes = stackalloc byte[sizeof(int)];
            BinaryPrimitives.WriteInt32BigEndian(lengthBytes, Length);
            writer.Write(lengthBytes);
            writer.Write((byte)Type);
            writer.Write(Payload);
            
            return ms.ToArray();
        }

        public static Packet? Deserialize(BinaryReader reader)
        {
            try
            {
                var lengthBytes = reader.ReadBytes(sizeof(int));
                if (lengthBytes.Length != sizeof(int)) return null;
                int length = BinaryPrimitives.ReadInt32BigEndian(lengthBytes);
                if (length < 0 || length > 16 * 1024 * 1024)
                    throw new InvalidDataException($"Invalid packet length: {length}");
                byte typeByte = reader.ReadByte();
                if (!Enum.IsDefined(typeof(PacketType), typeByte))
                    throw new InvalidDataException($"Unknown packet type: 0x{typeByte:X2}");
                byte[] payload = reader.ReadBytes(length);
                if (payload.Length != length) return null;
                
                return new Packet((PacketType)typeByte, payload);
            }
            catch (EndOfStreamException)
            {
                return null;
            }
        }
    }
}
