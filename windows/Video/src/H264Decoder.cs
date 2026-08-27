using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Threading;
using FFmpeg.AutoGen.Bindings.DynamicallyLoaded;
using FFmpeg.AutoGen.Abstractions;

namespace UniversalMobileDesktop.Video
{
    public unsafe class H264Decoder : IDisposable
    {
        private AVCodecContext* _codecContext;
        private AVFrame* _decodedFrame;
        private SwsContext* _swsContext;
        private AVPacket* _packet;
        private bool _initialized;
        private int _lastWidth;
        private int _lastHeight;
        private static bool _bindingsInitialized;
        private readonly SynchronizationContext? _callbackContext;

        public event Action<Bitmap>? OnFrameDecoded;

        public H264Decoder(SynchronizationContext? callbackContext = null)
        {
            _callbackContext = callbackContext ?? SynchronizationContext.Current;
        }

        public static void SetFFmpegPath(string path)
        {
            DynamicallyLoadedBindings.LibrariesPath = path;
            DynamicallyLoadedBindings.Initialize();
            _bindingsInitialized = true;
        }

        public void Initialize()
        {
            if (!_bindingsInitialized)
                throw new InvalidOperationException("Call H264Decoder.SetFFmpegPath() before Initialize().");
            if (_initialized) return;
            var codec = ffmpeg.avcodec_find_decoder(AVCodecID.AV_CODEC_ID_H264);
            if (codec == null)
                throw new Exception("H.264 codec not found");

            _codecContext = ffmpeg.avcodec_alloc_context3(codec);
            if (_codecContext == null)
                throw new Exception("Could not allocate codec context");

            _codecContext->flags2 |= ffmpeg.AV_CODEC_FLAG2_FAST;

            if (ffmpeg.avcodec_open2(_codecContext, codec, null) < 0)
                throw new Exception("Could not open codec");

            _decodedFrame = ffmpeg.av_frame_alloc();
            _packet = ffmpeg.av_packet_alloc();
            _initialized = true;
        }

        public void DecodeNalUnit(byte[] nalData)
        {
            if (!_initialized) return;

            fixed (byte* pData = nalData)
            {
                _packet->data = pData;
                _packet->size = nalData.Length;

                int ret = ffmpeg.avcodec_send_packet(_codecContext, _packet);
                if (ret < 0) return;

                while (true)
                {
                    ret = ffmpeg.avcodec_receive_frame(_codecContext, _decodedFrame);
                    if (ret < 0) break;

                    ConvertAndEmit();
                }
            }
        }

        private void ConvertAndEmit()
        {
            int width = _decodedFrame->width;
            int height = _decodedFrame->height;

            if (width <= 0 || height <= 0) return;

            // Initialize or reinitialize SwsContext for YUV->BGR conversion
            if (_swsContext == null || width != _lastWidth || height != _lastHeight)
            {
                if (_swsContext != null)
                    ffmpeg.sws_freeContext(_swsContext);

                _swsContext = ffmpeg.sws_getContext(
                    width, height, (AVPixelFormat)_decodedFrame->format,
                    width, height, AVPixelFormat.AV_PIX_FMT_BGR24,
                    2, null, null, null); // SWS_BILINEAR (binding 8.x omits the generated constant)

                _lastWidth = width;
                _lastHeight = height;
            }

            if (_swsContext == null) return;

            // Create bitmap and convert directly into it
            var bitmap = new Bitmap(width, height, PixelFormat.Format24bppRgb);
            var bmpData = bitmap.LockBits(
                new Rectangle(0, 0, width, height),
                ImageLockMode.WriteOnly,
                PixelFormat.Format24bppRgb);

            var dstData = new byte*[] { (byte*)bmpData.Scan0, null, null, null };
            var dstLinesize = new int[] { bmpData.Stride, 0, 0, 0 };

            ffmpeg.sws_scale(_swsContext,
                _decodedFrame->data, _decodedFrame->linesize,
                0, height,
                dstData, dstLinesize);

            bitmap.UnlockBits(bmpData);
            var handler = OnFrameDecoded;
            if (handler is null) { bitmap.Dispose(); return; }
            if (_callbackContext is null) handler(bitmap);
            else _callbackContext.Post(_ => handler(bitmap), null);
        }

        public void Dispose()
        {
            if (_swsContext != null)
            {
                ffmpeg.sws_freeContext(_swsContext);
                _swsContext = null;
            }

            if (_packet != null)
            {
                var packet = _packet;
                ffmpeg.av_packet_free(&packet);
                _packet = null;
            }

            if (_decodedFrame != null)
            {
                var frame = _decodedFrame;
                ffmpeg.av_frame_free(&frame);
                _decodedFrame = null;
            }

            if (_codecContext != null)
            {
                var context = _codecContext;
                ffmpeg.avcodec_free_context(&context);
                _codecContext = null;
            }
            _initialized = false;
        }
    }
}
