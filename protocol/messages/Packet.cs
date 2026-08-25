using System;
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
        KeyEvent = 0x06
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
            
            writer.Write(Length);
            writer.Write((byte)Type);
            writer.Write(Payload);
            
            return ms.ToArray();
        }

        public static Packet? Deserialize(BinaryReader reader)
        {
            try
            {
                int length = reader.ReadInt32();
                byte typeByte = reader.ReadByte();
                byte[] payload = reader.ReadBytes(length);
                
                return new Packet((PacketType)typeByte, payload);
            }
            catch (EndOfStreamException)
            {
                return null;
            }
        }
    }
}
