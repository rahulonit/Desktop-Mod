using System;
using System.Buffers.Binary;
using UniversalMobileDesktop.Protocol;

namespace UniversalMobileDesktop.Input
{
    public class InputCapture
    {
        public event Action<Packet>? OnInputPacketReady;

        public void StartCapture()
        {
            Console.WriteLine("Capturing mouse and keyboard input...");
        }

        public void SendMouseEvent(int x, int y, int buttonState)
        {
            // Serialize mouse state into byte array (e.g. 12 bytes: x(4), y(4), state(4))
            byte[] payload = new byte[12];
            BinaryPrimitives.WriteInt32BigEndian(payload.AsSpan(0, 4), x);
            BinaryPrimitives.WriteInt32BigEndian(payload.AsSpan(4, 4), y);
            BinaryPrimitives.WriteInt32BigEndian(payload.AsSpan(8, 4), buttonState);

            var packet = new Packet(PacketType.MouseEvent, payload);
            OnInputPacketReady?.Invoke(packet);
        }

        public void SendKeyEvent(int keyCode, bool isKeyDown)
        {
            byte[] payload = new byte[5];
            BinaryPrimitives.WriteInt32BigEndian(payload.AsSpan(0, 4), keyCode);
            payload[4] = (byte)(isKeyDown ? 1 : 0);

            var packet = new Packet(PacketType.KeyEvent, payload);
            OnInputPacketReady?.Invoke(packet);
        }
    }
}
