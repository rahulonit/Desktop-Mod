# Desktop Mod protocol v2

Every packet is a 4-byte big-endian payload length, a 1-byte type, and the payload. Payloads are capped at 32 MiB.

Before desktop data is sent, the sender transmits a 32-byte random `Handshake` challenge. The receiver displays a code prompt and replies with `PairingResponse = HMAC-SHA256(UTF8(six-digit code), challenge)`. The sender compares it in constant time and replies with one status byte. Failed peers are disconnected. The code is regenerated whenever the sender service starts.

Packet type 0x04 starts with a four-byte codec tag (`H264` or `JPEG`) followed by the encoded frame/access unit. This lets Android and iOS interoperate with both desktop receivers. Types 0x05–0x06 carry big-endian input events. Type 0x07 carries UTF-8 clipboard text. File transfer uses metadata (0x08), 64 KiB chunks (0x09), and completion (0x0A), each prefixed by a 16-byte transfer identifier. Metadata additionally contains a big-endian UInt16 UTF-8 filename length, filename, and big-endian Int64 file size.

Pairing authenticates a local peer but does not encrypt traffic. Use USB tethering or a trusted private network; transport encryption is a future protocol version.

## Compatibility

| Sender | Windows receiver | macOS receiver |
|---|---|---|
| Android | H.264/FFmpeg, input, clipboard, files | H.264/VideoToolbox, input, clipboard |
| iOS | ReplayKit/JPEG, input, clipboard | ReplayKit/JPEG, input, clipboard |

All four pairings use the same packet framing and HMAC pairing flow. Windows accepts a manually entered phone address in addition to Android's receiver-discovery flow; macOS accepts the phone address directly.
