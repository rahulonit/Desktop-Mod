using System;
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
            BitConverter.GetBytes(x).CopyTo(payload, 0);
            BitConverter.GetBytes(y).CopyTo(payload, 4);
            BitConverter.GetBytes(buttonState).CopyTo(payload, 8);

            var packet = new Packet(PacketType.MouseEvent, payload);
            OnInputPacketReady?.Invoke(packet);
        }

        public void SendKeyEvent(int keyCode, bool isKeyDown)
        {
            byte[] payload = new byte[5];
            BitConverter.GetBytes(keyCode).CopyTo(payload, 0);
            payload[4] = (byte)(isKeyDown ? 1 : 0);

            var packet = new Packet(PacketType.KeyEvent, payload);
            OnInputPacketReady?.Invoke(packet);
        }
    }
}
