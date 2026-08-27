import AppKit
import DesktopProtocol
import Foundation
import MacInput
import MacTransport
import MacVideo
import SwiftUI

@main struct ReceiverApp: App {
    var body: some Scene { WindowGroup("Desktop Mod") { ReceiverView() }.defaultSize(width: 1240, height: 800).windowStyle(.hiddenTitleBar) }
}

private struct ReceiverView: View {
    @StateObject private var frames = VideoFrameStore()
    @State private var host = "", status = "Ready to discover your phone", details = "", pairingCode = "", lastClipboard = ""
    @State private var isConnected = false, isConnecting = false, showsConnect = false
    private let connection = ReceiverConnection(), input = InputCapture()

    var body: some View {
        ZStack { AtmosphericBackground(); VStack(spacing: 0) { header.padding(.horizontal, 24).padding(.top, 18); isConnected ? AnyView(sessionView) : AnyView(connectionHome); statusBar } }
            .frame(minWidth: 900, minHeight: 600).preferredColorScheme(.dark).sheet(isPresented: $showsConnect) { connectSheet }
            .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in syncClipboard() }
            .onAppear { detectAdb() }.onDisappear { disconnect() }
    }

    private var header: some View {
        HStack(spacing: 16) {
            Image("APP_icon", bundle: .module).resizable().scaledToFit().frame(width: 48, height: 48).shadow(color: .blue.opacity(0.35), radius: 16)
            Text("Desktop Mod").font(.system(size: 21, weight: .bold, design: .rounded))
            StatusPill(text: isConnected ? "Connected" : isConnecting ? "Connecting…" : status, kind: isConnected ? .success : isConnecting ? .working : .neutral)
            Spacer()
            Button { NSApp.keyWindow?.toggleFullScreen(nil) } label: { Label("Full Screen", systemImage: "arrow.up.left.and.arrow.down.right") }.buttonStyle(GlassButtonStyle())
            Button { showsConnect = true } label: { Label("Connect phone", systemImage: "iphone.gen3") }.buttonStyle(PrimaryButtonStyle())
        }.padding(.horizontal, 20).frame(height: 76).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22)).overlay(RoundedRectangle(cornerRadius: 22).stroke(.white.opacity(0.10))).shadow(color: .black.opacity(0.30), radius: 30, y: 14)
    }

    private var connectionHome: some View {
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 12) {
                    Image("APP_icon", bundle: .module).resizable().scaledToFit().frame(width: 96, height: 96).background(.blue.opacity(0.10), in: Circle()).shadow(color: .blue.opacity(0.30), radius: 20)
                    Text("Connect Desktop Mode").font(.system(size: 40, weight: .bold, design: .rounded))
                    Text("Choose a connection method to use your phone as a separate desktop.").font(.system(size: 17)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                ViewThatFits(in: .horizontal) { HStack(alignment: .top, spacing: 20) { methodCards }; VStack(spacing: 16) { methodCards } }
                Button { showsConnect = true } label: { Label("Connect phone", systemImage: "link").frame(minWidth: 150) }.buttonStyle(PrimaryButtonStyle())
                Label("Your phone stays usable. This is a separate desktop, not screen mirroring.", systemImage: "info.circle.fill").font(.callout).foregroundStyle(.blue).frame(maxWidth: .infinity, alignment: .leading).padding(16).background(.blue.opacity(0.09), in: RoundedRectangle(cornerRadius: 15)).overlay(RoundedRectangle(cornerRadius: 15).stroke(.blue.opacity(0.24)))
            }.padding(38).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 28)).overlay(RoundedRectangle(cornerRadius: 28).stroke(.white.opacity(0.10))).shadow(color: .black.opacity(0.32), radius: 38, y: 18).frame(maxWidth: 1080).padding(32)
        }
    }

    @ViewBuilder private var methodCards: some View {
        MethodCard(title: "USB Connection", image: "USB", accent: .blue, summary: "Fast and stable. Developer Mode is preferred; USB tethering remains available.", steps: ["Connect a USB data cable", "Open Desktop Mod", "Enable debugging or tethering", "Select this computer"], status: host == "127.0.0.1" ? "ADB device detected" : "Checking USB…") { showsConnect = true }
        MethodCard(title: "Wi-Fi Connection", image: "WIFI", accent: .indigo, summary: "Connect wirelessly when both devices use the same private network.", steps: ["Join the same network", "Open both apps", "Select this Mac", "Confirm pairing"], status: "Searching…") { showsConnect = true }
        MethodCard(title: "External Display", image: "HTMI", accent: .purple, summary: "Use USB-C, DisplayPort, or HDMI where independent output is supported.", steps: ["Connect the display", "Check capabilities", "Start when supported"], status: "Capability check") { status = "Display capability is reported by the phone." }
    }

    private var sessionView: some View {
        ZStack { Color.black; if let image = frames.image { Image(nsImage: image).resizable().scaledToFit() } else { ProgressView("Starting desktop…").controlSize(.large) }
            VStack { HStack(spacing: 12) { StatusPill(text: "Live", kind: .success); Text(frames.codec); Text("\(frames.decodedFrames) frames"); Spacer(); Label("Clipboard active", systemImage: "doc.on.clipboard"); Button("Disconnect", role: .destructive) { disconnect() } }.font(.caption.bold()).padding(12).background(.black.opacity(0.62), in: Capsule()); Spacer() }.padding(20)
        }.padding(24)
    }

    private var statusBar: some View {
        HStack(spacing: 10) { Circle().fill(isConnected ? Color.green : isConnecting ? .blue : .orange).frame(width: 8, height: 8); Text(isConnected ? "Desktop ready — secure local connection" : status).lineLimit(1); Spacer(); if !details.isEmpty { DisclosureGroup("Technical details") { Text(details).textSelection(.enabled) }.fixedSize() }; Text("Authenticated local connection").foregroundStyle(.secondary) }.font(.caption).padding(.horizontal, 24).frame(height: 46).background(.black.opacity(0.24))
    }

    private var connectSheet: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack { VStack(alignment: .leading, spacing: 5) { Text("Connect your phone").font(.title2.bold()); Text("USB is detected automatically. Enter a local address for Wi-Fi.").foregroundStyle(.secondary) }; Spacer(); Button { showsConnect = false } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain) }
            Picker("Connection", selection: Binding(get: { host == "127.0.0.1" ? 0 : 1 }, set: { if $0 == 0 { host = "127.0.0.1" } })) { Text("USB").tag(0); Text("Wi-Fi").tag(1) }.pickerStyle(.segmented)
            TextField("Phone IP address", text: $host).textFieldStyle(.roundedBorder); SecureField("6-digit pairing code", text: $pairingCode).textFieldStyle(.roundedBorder)
            HStack { Button("Cancel") { showsConnect = false }.buttonStyle(GlassButtonStyle()); Spacer(); Button { showsConnect = false; connect() } label: { Label("Connect securely", systemImage: "lock.shield") }.buttonStyle(PrimaryButtonStyle()).disabled(host.isEmpty || pairingCode.count != 6 || isConnecting) }
        }.padding(28).frame(width: 520).background(AtmosphericBackground())
    }

    private func connect() {
        isConnecting = true; status = "Creating a secure connection…"; details = ""
        connection.onPacket = { packet in
            if packet.type == .handshake { connection.send(Packet(type: .pairingResponse, payload: PairingSecurity.response(code: pairingCode, challenge: packet.payload))) }
            else if packet.type == .pairingResponse { Task { @MainActor in isConnecting = false; if packet.payload.first == 1 { status = "Desktop ready"; isConnected = true; input.start() } else { status = "Pairing rejected"; details = "The pairing response did not validate."; isConnected = false } } }
            else if packet.type == .clipboard, let text = String(data: packet.payload, encoding: .utf8) { Task { @MainActor in lastClipboard = text; NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string) } }
            else { frames.consumeFromNetwork(packet) }
        }
        connection.onStateChange = { state in Task { @MainActor in if !isConnected { let raw = String(describing: state); details = raw; if raw.contains("failed") || raw.contains("cancelled") { isConnecting = false; status = "Connection lost — The phone disconnected unexpectedly." } } } }
        input.onPacket = { packet in connection.send(packet) }; connection.connect(host: host)
    }
    private func disconnect() { input.stop(); connection.disconnect(); isConnected = false; isConnecting = false; status = "Ready to discover your phone" }
    private func detectAdb() { AdbDetector.configureForwarding { result in Task { @MainActor in if case .success(let address) = result { host = address; status = "Android Developer Mode detected — ready to pair" } } } }
    private func syncClipboard() { guard isConnected, let text = NSPasteboard.general.string(forType: .string), text != lastClipboard else { return }; lastClipboard = text; connection.send(Packet(type: .clipboard, payload: Data(text.utf8))) }
}

private struct AtmosphericBackground: View { var body: some View { LinearGradient(colors: [Color(red: 0.02, green: 0.07, blue: 0.16), Color(red: 0.01, green: 0.03, blue: 0.08), .black.opacity(0.95)], startPoint: .topLeading, endPoint: .bottomTrailing).overlay(RadialGradient(colors: [.indigo.opacity(0.30), .clear], center: .topTrailing, startRadius: 10, endRadius: 520)).ignoresSafeArea() } }

private struct MethodCard: View {
    let title: String, image: String, accent: Color, summary: String, steps: [String], status: String, action: () -> Void
    var body: some View { Button(action: action) { VStack(alignment: .leading, spacing: 14) { Image(image, bundle: .module).resizable().scaledToFit().frame(width: 52, height: 52).padding(5).background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 14)); Text(title).font(.title3.bold()); Text(summary).font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true); VStack(alignment: .leading, spacing: 11) { ForEach(Array(steps.enumerated()), id: \.offset) { index, step in HStack(spacing: 10) { Text("\(index + 1)").font(.caption.bold()).frame(width: 28, height: 28).background(accent.gradient, in: Circle()); Text(step).font(.callout).fixedSize(horizontal: false, vertical: true) } } }; Spacer(minLength: 4); Label(status, systemImage: "circle.fill").font(.caption.bold()).foregroundStyle(accent) }.frame(maxWidth: .infinity, minHeight: 318, alignment: .topLeading).padding(22).contentShape(Rectangle()) }.buttonStyle(.plain).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20)).background(Color(red: 0.08, green: 0.15, blue: 0.30).opacity(0.44), in: RoundedRectangle(cornerRadius: 20)).overlay(RoundedRectangle(cornerRadius: 20).stroke(accent.opacity(0.30))).shadow(color: .black.opacity(0.22), radius: 18, y: 9) }
}

private enum StatusKind: Equatable { case neutral, working, success }
private struct StatusPill: View { let text: String, kind: StatusKind; private var color: Color { kind == .success ? .green : kind == .working ? .blue : .secondary }; var body: some View { Label(text, systemImage: kind == .working ? "circle.dotted" : "circle.fill").lineLimit(1).font(.caption.bold()).foregroundStyle(color).padding(.horizontal, 13).padding(.vertical, 9).background(color.opacity(0.10), in: Capsule()) } }
private struct PrimaryButtonStyle: ButtonStyle { func makeBody(configuration: Configuration) -> some View { configuration.label.font(.body.bold()).padding(.horizontal, 20).frame(height: 44).foregroundStyle(.white).background(LinearGradient(colors: [.indigo, .blue], startPoint: .leading, endPoint: .trailing).opacity(configuration.isPressed ? 0.78 : 1), in: RoundedRectangle(cornerRadius: 12)).scaleEffect(configuration.isPressed ? 0.98 : 1).shadow(color: .blue.opacity(0.25), radius: 14, y: 7).animation(.easeOut(duration: 0.14), value: configuration.isPressed) } }
private struct GlassButtonStyle: ButtonStyle { func makeBody(configuration: Configuration) -> some View { configuration.label.font(.body.weight(.medium)).padding(.horizontal, 16).frame(height: 44).background(.white.opacity(configuration.isPressed ? 0.11 : 0.055), in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(.white.opacity(0.10))) } }
