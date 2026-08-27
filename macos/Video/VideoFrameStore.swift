import AppKit
import DesktopProtocol

@MainActor
public final class VideoFrameStore: ObservableObject {
    @Published public private(set) var image: NSImage?
    @Published public private(set) var decodedFrames = 0
    @Published public private(set) var codec = "Waiting"
    private let h264 = H264Decoder()

    public init() {
        h264.onImage = { [weak self] image in
            Task { @MainActor in self?.set(image, codec: "H.264") }
        }
    }

    public nonisolated func consumeFromNetwork(_ packet: Packet) {
        Task { @MainActor [weak self] in self?.consume(packet) }
    }

    public func consume(_ packet: Packet) {
        guard packet.type == .videoFrame, packet.payload.count >= 4 else { return }
        let tag = String(data: packet.payload.prefix(4), encoding: .ascii)
        let body = packet.payload.dropFirst(4)
        if tag == "H264" { h264.decode(Data(body)); return }
        guard tag == "JPEG", let image = NSImage(data: body) else { return }
        set(image, codec: "JPEG")
    }

    private func set(_ image: NSImage, codec: String) {
        self.image = image
        self.codec = codec
        decodedFrames += 1
    }
}
