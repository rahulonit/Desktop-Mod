using System;
using UniversalMobileDesktop.Protocol;

namespace UniversalMobileDesktop.Video
{
    public class Decoder
    {
        public void InitializeDecoder()
        {
            Console.WriteLine("Initializing H.264 Video Decoder...");
            // TODO: Initialize MediaFoundation IMFTransform or FFmpeg decoder context
        }

        public void DecodeFrame(Packet packet)
        {
            if (packet.Type != PacketType.VideoFrame) return;

            // Console.WriteLine($"Received {packet.Payload.Length} bytes of H.264 NAL units");
            // TODO: Pass packet.Payload to the decoder and extract raw RGB/YUV frame
            // TODO: Raise an event to render the frame in the UI
        }
    }
}
