import IOSDesktopEngine
import SwiftUI

public struct DesktopRootView: View {
    @StateObject private var session: DesktopSession
    @Environment(\.horizontalSizeClass) private var sizeClass
    public init(session: DesktopSession = DesktopSession()) { _session = StateObject(wrappedValue: session) }

    public var body: some View {
        ZStack {
            AtmosphericBackground()
            ScrollView {
                VStack(spacing: 18) { floatingHeader; hero; connectionMethods; pairingPanel; informationBanner }
                    .padding(.horizontal, sizeClass == .regular ? 28 : 16).padding(.vertical, 18)
            }
        }.preferredColorScheme(.dark)
    }

    private var floatingHeader: some View {
        GlassPanel(radius: 22) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 14) { identity; Spacer(); statusPill; sessionButton }
                VStack(alignment: .leading, spacing: 13) { identity; HStack { statusPill; Spacer(); sessionButton } }
            }.padding(16)
        }
    }
    private var identity: some View {
        HStack(spacing: 12) {
            Image("APP_icon", bundle: .module).resizable().scaledToFit().frame(width: 48, height: 48).shadow(color: .blue.opacity(0.45), radius: 14)
            VStack(alignment: .leading, spacing: 2) { Text("Desktop Mod").font(.system(size: 21, weight: .bold, design: .rounded)); Text("Your iPhone. A real desktop.").font(.caption).foregroundStyle(.secondary) }
        }
    }
    private var statusPill: some View {
        HStack(spacing: 8) { Circle().fill(statusColor).frame(width: 9, height: 9); Text(shortStatus).font(.caption.weight(.semibold)).lineLimit(1) }
            .padding(.horizontal, 14).frame(height: 40).background(Color.black.opacity(0.25), in: Capsule()).overlay(Capsule().stroke(statusColor.opacity(0.25)))
    }
    private var sessionButton: some View {
        Button { isRunning ? session.stop() : session.start() } label: { Label(isRunning ? "Stop" : "Start desktop", systemImage: isRunning ? "stop.fill" : "play.fill").font(.subheadline.bold()).padding(.horizontal, 16).frame(height: 46) }
            .buttonStyle(GradientButtonStyle(destructive: isRunning))
    }
    private var hero: some View {
        GlassPanel(radius: 28) {
            VStack(spacing: 14) {
                ZStack { Circle().fill(Color.blue.opacity(0.13)).frame(width: 108, height: 108); Circle().stroke(Color.cyan.opacity(0.20)).frame(width: 94, height: 94); Image("APP_icon", bundle: .module).resizable().scaledToFit().frame(width: 92, height: 92) }
                Text(isConnected ? "Desktop Ready" : "Connect Desktop Mode").font(.system(size: sizeClass == .regular ? 38 : 30, weight: .bold, design: .rounded)).multilineTextAlignment(.center)
                Text(statusText).font(.system(size: 16)).foregroundStyle(Color(red: 0.72, green: 0.80, blue: 0.92)).multilineTextAlignment(.center).frame(maxWidth: 620)
            }.frame(maxWidth: .infinity).padding(.horizontal, 24).padding(.vertical, 30)
        }
    }
    private var connectionMethods: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Connection methods").font(.title3.bold()).padding(.leading, 2)
            if sizeClass == .regular { HStack(alignment: .top, spacing: 14) { methodCards } } else { VStack(spacing: 12) { methodCards } }
        }
    }
    @ViewBuilder private var methodCards: some View {
        ConnectionMethodCard(image: "USB", title: "USB", status: "Fast and stable", accent: .blue, description: "Connect through a trusted USB data connection or the computer’s private tunnel.")
        ConnectionMethodCard(image: "WIFI", title: "Wi-Fi", status: isRunning ? "Discoverable" : "Ready to start", accent: .indigo, description: "Connect to Windows or macOS while both devices use the same private network.")
        ConnectionMethodCard(image: "HTMI", title: "External display", status: "Where supported", accent: .purple, description: "Use USB-C or HDMI output when the iPhone and adapter support it.")
    }
    private var pairingPanel: some View {
        GlassPanel(radius: 22) {
            VStack(alignment: .leading, spacing: 15) {
                HStack { Label("Secure pairing", systemImage: "lock.shield.fill").font(.headline); Spacer(); if isConnected { Label("Verified", systemImage: "checkmark.circle.fill").font(.caption.bold()).foregroundStyle(.green) } }
                Text(session.pairingCode).font(.system(size: 38, weight: .bold, design: .monospaced)).tracking(4).foregroundStyle(LinearGradient(colors: [.cyan, .blue, .purple], startPoint: .leading, endPoint: .trailing)).accessibilityLabel("Pairing code \(session.pairingCode)")
                Text("Enter this one-time code only on the Windows PC or Mac you trust. It changes whenever the sender restarts.").font(.footnote).foregroundStyle(.secondary)
                Label(session.connectionAddress, systemImage: "network").font(.footnote.monospaced()).foregroundStyle(Color(red: 0.66, green: 0.80, blue: 1)).textSelection(.enabled)
            }.padding(20)
        }
    }
    private var informationBanner: some View {
        Label("Your iPhone stays usable. Desktop Mod creates a separate workspace and follows ReplayKit privacy controls.", systemImage: "info.circle.fill").font(.footnote).foregroundStyle(Color(red: 0.67, green: 0.82, blue: 1)).padding(16).frame(maxWidth: .infinity, alignment: .leading).background(Color.blue.opacity(0.09), in: RoundedRectangle(cornerRadius: 17)).overlay(RoundedRectangle(cornerRadius: 17).stroke(Color.blue.opacity(0.28)))
    }
    private var isRunning: Bool { session.state != .idle }
    private var isConnected: Bool { if case .connected = session.state { return true }; return false }
    private var statusColor: Color { if isConnected { return .green }; if case .failed = session.state { return .red }; return isRunning ? .blue : .secondary }
    private var shortStatus: String { if isConnected { return "Connected" }; if case .failed = session.state { return "Connection failed" }; return isRunning ? "Waiting securely" : "Offline" }
    private var statusText: String { switch session.state { case .idle: return "Choose a connection method, then start a secure desktop session while keeping your iPhone usable."; case .advertising: return "Waiting for an authenticated Windows or macOS receiver."; case .connected(let name): return "Connected securely to \(name). \(session.framesSent) frames sent."; case .failed: return "The session could not start. Check permissions and try again." } }
}

private struct ConnectionMethodCard: View {
    let image: String, title: String, status: String, accent: Color, description: String
    var body: some View { GlassPanel(radius: 20) { VStack(alignment: .leading, spacing: 12) { HStack(spacing: 13) { Image(image, bundle: .module).resizable().scaledToFit().frame(width: 50, height: 50).padding(5).background(accent.opacity(0.13), in: RoundedRectangle(cornerRadius: 15)); VStack(alignment: .leading, spacing: 3) { Text(title).font(.headline); Text(status).font(.caption.bold()).foregroundStyle(accent) } }; Divider().overlay(Color.white.opacity(0.09)); Text(description).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }.frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading).padding(18) } }
}
private struct GlassPanel<Content: View>: View {
    let radius: CGFloat; let content: Content
    init(radius: CGFloat, @ViewBuilder content: () -> Content) { self.radius = radius; self.content = content() }
    var body: some View { content.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: radius)).background(LinearGradient(colors: [Color.blue.opacity(0.16), Color.indigo.opacity(0.08)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: radius)).overlay(RoundedRectangle(cornerRadius: radius).stroke(LinearGradient(colors: [Color.white.opacity(0.18), Color.blue.opacity(0.24), .clear], startPoint: .topLeading, endPoint: .bottomTrailing))).shadow(color: .black.opacity(0.28), radius: 28, y: 14) }
}
private struct AtmosphericBackground: View { var body: some View { LinearGradient(colors: [Color(red: 0.02, green: 0.08, blue: 0.18), Color(red: 0.01, green: 0.025, blue: 0.075), Color(red: 0.005, green: 0.012, blue: 0.035)], startPoint: .topLeading, endPoint: .bottomTrailing).overlay(RadialGradient(colors: [Color.blue.opacity(0.34), .clear], center: .topLeading, startRadius: 5, endRadius: 520)).overlay(RadialGradient(colors: [Color.purple.opacity(0.22), .clear], center: .topTrailing, startRadius: 10, endRadius: 480)).ignoresSafeArea() } }
private struct GradientButtonStyle: ButtonStyle { let destructive: Bool; func makeBody(configuration: Configuration) -> some View { configuration.label.foregroundStyle(.white).background(LinearGradient(colors: destructive ? [Color.red.opacity(0.85), Color.pink.opacity(0.72)] : [.indigo, .blue], startPoint: .leading, endPoint: .trailing).opacity(configuration.isPressed ? 0.76 : 1), in: RoundedRectangle(cornerRadius: 13)).scaleEffect(configuration.isPressed ? 0.98 : 1).shadow(color: (destructive ? Color.red : .blue).opacity(0.24), radius: 14, y: 7).animation(.easeOut(duration: 0.14), value: configuration.isPressed) } }
