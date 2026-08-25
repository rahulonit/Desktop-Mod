using System;
using System.Net.Sockets;
using System.Threading.Tasks;

namespace UniversalMobileDesktop.Transport
{
    public class ConnectionManager
    {
        private TcpClient? _client;

        public async Task StartConnectingAsync()
        {
            Console.WriteLine("Connecting to device via ADB forwarded port 5000...");
            try
            {
                _client = new TcpClient();
                await _client.ConnectAsync("127.0.0.1", 5000);
                Console.WriteLine("Connected to device!");
                
                var stream = _client.GetStream();
                using var reader = new System.IO.BinaryReader(stream);
                var decoder = new UniversalMobileDesktop.Video.Decoder();
                decoder.InitializeDecoder();

                _ = Task.Run(() =>
                {
                    while (true)
                    {
                        var packet = UniversalMobileDesktop.Protocol.Packet.Deserialize(reader);
                        if (packet == null) break;
                        
                        if (packet.Type == UniversalMobileDesktop.Protocol.PacketType.VideoFrame)
                        {
                            decoder.DecodeFrame(packet);
                        }
                    }
                });
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Connection failed: {ex.Message}");
            }
        }
    }
}
