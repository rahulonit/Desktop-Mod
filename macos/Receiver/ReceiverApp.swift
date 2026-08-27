import AppKit
import DesktopProtocol
import Foundation
import MacInput
import MacTransport
import MacVideo
import SwiftUI

@main
struct ReceiverApp: App {
    var body: some Scene {
        WindowGroup("Desktop Mod Receiver") { ReceiverView() }
            .defaultSize(width: 1180, height: 760)
    }
}

private struct ReceiverView: View {
    @StateObject private var frames = VideoFrameStore()
    @State private var host = ""
    @State private var status = "Disconnected"
    @State private var pairingCode = ""
    @State private var lastClipboard = ""
    @State private var isConnected = false
    private let connection = ReceiverConnection()
    private let input = InputCapture()

    var body: some View {
        NavigationSplitView {
            VStack(alignment: .leading, spacing: 20) {
                Label("Desktop Mod", systemImage: "display.2").font(.title2.bold())
                connectionCard
                Divider()
                capability("Android", "H.264 • Files • Clipboard", "candybarphone")
                capability("iPhone", "ReplayKit • Input • Clipboard", "iphone")
                Spacer()
                Label("Authenticated local connection", systemImage: "lock.shield.fill")
                    .font(.caption).foregroundStyle(.secondary)
            }
            .padding(20).navigationSplitViewColumnWidth(min: 270, ideal: 300, max: 340)
        } detail: {
            ZStack {
                Color.black
                if let image = frames.image {
                    Image(nsImage: image).resizable().scaledToFit()
                } else { emptyState }
                VStack { streamToolbar; Spacer() }.padding(16)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in syncClipboard() }
        .onDisappear { disconnect() }
    }

    private var connectionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Connect to phone").font(.headline)
            TextField("Phone IP address", text: $host).textFieldStyle(.roundedBorder)
            SecureField("6-digit pairing code", text: $pairingCode).textFieldStyle(.roundedBorder)
            Button { isConnected ? disconnect() : connect() } label: {
                Label(isConnected ? "Disconnect" : "Connect securely", systemImage: isConnected ? "xmark" : "link")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).controlSize(.large).disabled(!isConnected && (host.isEmpty || pairingCode.count != 6))
            HStack { Circle().fill(isConnected ? .green : .orange).frame(width: 8, height: 8); Text(status).lineLimit(1) }
                .font(.caption).foregroundStyle(.secondary)
        }
        .padding(16).background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 16))
    }

    private var streamToolbar: some View {
        HStack(spacing: 14) {
            Label(isConnected ? "Live" : "Not connected", systemImage: isConnected ? "dot.radiowaves.left.and.right" : "wifi.slash")
            Divider().frame(height: 16)
            Text(frames.codec)
            Text("\(frames.decodedFrames) frames")
            Spacer()
            Label("Clipboard sync active", systemImage: "doc.on.clipboard")
        }
        .font(.caption.bold()).foregroundStyle(.white)
        .padding(.horizontal, 14).padding(.vertical, 10).background(.black.opacity(0.65), in: Capsule())
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "display.trianglebadge.exclamationmark").font(.system(size: 58)).foregroundStyle(.blue)
            Text("Ready for your mobile desktop").font(.title.bold()).foregroundStyle(.white)
            Text("Start Desktop Mod on Android or iPhone, then enter its address and pairing code.")
                .foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 430)
        }
    }

    private func capability(_ title: String, _ detail: String, _ icon: String) -> some View {
        HStack { Image(systemName: icon).frame(width: 26).foregroundStyle(.blue); VStack(alignment: .leading) { Text(title).font(.headline); Text(detail).font(.caption).foregroundStyle(.secondary) } }
    }

    private func connect() {
        status = "Connecting…"
        connection.onPacket = { packet in
            if packet.type == .handshake {
                connection.send(Packet(type: .pairingResponse, payload: PairingSecurity.response(code: pairingCode, challenge: packet.payload)))
            } else if packet.type == .pairingResponse {
                Task { @MainActor in
                    if packet.payload.first == 1 { status = "Paired securely"; isConnected = true; input.start() }
                    else { status = "Pairing rejected"; isConnected = false }
                }
            } else if packet.type == .clipboard, let text = String(data: packet.payload, encoding: .utf8) {
                Task { @MainActor in lastClipboard = text; NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string) }
            } else { frames.consumeFromNetwork(packet) }
        }
        connection.onStateChange = { state in Task { @MainActor in if !isConnected { status = String(describing: state) } } }
        input.onPacket = { packet in connection.send(packet) }
        connection.connect(host: host)
    }

    private func disconnect() { input.stop(); connection.disconnect(); isConnected = false; status = "Disconnected" }
    private func syncClipboard() {
        guard isConnected, let text = NSPasteboard.general.string(forType: .string), text != lastClipboard else { return }
        lastClipboard = text; connection.send(Packet(type: .clipboard, payload: Data(text.utf8)))
    }
}
