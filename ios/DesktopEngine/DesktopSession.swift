import DesktopProtocol
import CoreGraphics
import Foundation
import IOSStreaming
#if canImport(UIKit)
import UIKit
#endif

@MainActor
public final class DesktopSession: ObservableObject {
    public enum State: Equatable { case idle, advertising, connected(String), failed(String) }

    @Published public private(set) var state: State = .idle
    @Published public private(set) var framesSent = 0
    @Published public var selectedWorkspace = "Files"
    public var onInputPacket: ((Packet) -> Void)?
    private let server: PacketServer
    private let capture = ReplayKitCapture()
    private var clipboardTimer: Timer?
    private var lastClipboard = ""
    public var pairingCode: String { server.pairingCode }
    public var connectionAddress: String { ProcessInfo.processInfo.hostName }

    public init(server: PacketServer = PacketServer()) {
        self.server = server
        server.onPeerCountChanged = { [weak self] count in
            Task { @MainActor in
                guard let self else { return }
                self.state = count > 0 ? .connected("desktop receiver") : .advertising
            }
        }
        server.onPacket = { [weak self] packet in
            Task { @MainActor in
                if packet.type == .clipboard { self?.applyClipboard(packet) }
                else { self?.onInputPacket?(packet) }
            }
        }
        capture.onFrame = { [weak self] image in
            Task { @MainActor in _ = try? self?.videoPacket(for: image) }
        }
        capture.onError = { [weak self] error in Task { @MainActor in self?.markFailed(error) } }
    }

    public func start() {
        guard state == .idle else { return }
        do {
            try server.start()
            capture.start()
            let timer = Timer(timeInterval: 1, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.sendClipboardIfChanged() }
            }
            clipboardTimer = timer
            RunLoop.main.add(timer, forMode: .common)
            state = .advertising
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    public func stop() {
        server.stop()
        capture.stop()
        clipboardTimer?.invalidate(); clipboardTimer = nil
        state = .idle
        framesSent = 0
    }

    public func markConnected(to receiver: String) { state = .connected(receiver) }
    public func markFailed(_ error: Error) { state = .failed(error.localizedDescription) }

    public func videoPacket(for image: CGImage, encoder: FrameEncoder = FrameEncoder()) throws -> Packet {
        var payload = Data("JPEG".utf8)
        payload.append(try encoder.jpeg(image))
        let packet = Packet(type: .videoFrame, payload: payload)
        server.broadcast(packet)
        framesSent += 1
        return packet
    }

    private func sendClipboardIfChanged() {
#if canImport(UIKit)
        guard let value = UIPasteboard.general.string, value != lastClipboard else { return }
        lastClipboard = value
        server.broadcast(Packet(type: .clipboard, payload: Data(value.utf8)))
#endif
    }

    private func applyClipboard(_ packet: Packet) {
#if canImport(UIKit)
        guard packet.type == .clipboard, let value = String(data: packet.payload, encoding: .utf8) else { return }
        lastClipboard = value
        UIPasteboard.general.string = value
#else
        onInputPacket?(packet)
#endif
    }
}
