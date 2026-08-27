import IOSDesktopEngine
import SwiftUI
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

public struct DesktopRootView: View {
    @StateObject private var session: DesktopSession

    public init(session: DesktopSession = DesktopSession()) { _session = StateObject(wrappedValue: session) }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    hero
                    pairingCard
                    destinations
                    privacyCard
                }
                .padding(18)
            }
            .background(pageBackground)
            .navigationTitle("Desktop Mod")
            .toolbar { ToolbarItem(placement: .primaryAction) { statusBadge } }
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                Image(systemName: "macbook.and.iphone").font(.system(size: 48, weight: .semibold))
                Spacer()
                Label("Secure", systemImage: "lock.shield.fill").font(.caption.bold()).padding(9)
                    .background(.white.opacity(0.18), in: Capsule())
            }
            Text(isRunning ? "Your workspace is ready" : "Turn your iPhone into a desktop")
                .font(.system(.largeTitle, design: .rounded, weight: .bold))
            Text(statusText).foregroundStyle(.white.opacity(0.82))
            Button { isRunning ? session.stop() : session.start() } label: {
                Label(buttonTitle, systemImage: isRunning ? "stop.fill" : "play.fill")
                    .frame(maxWidth: .infinity).padding(.vertical, 5)
            }
            .buttonStyle(.borderedProminent).tint(.white).foregroundStyle(.blue).controlSize(.large)
        }
        .foregroundStyle(.white).padding(24)
        .background(LinearGradient(colors: [.blue, .indigo], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 28))
    }

    private var pairingCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Pair a trusted computer", systemImage: "key.fill").font(.headline)
            HStack {
                Text(session.pairingCode).font(.system(size: 34, weight: .bold, design: .monospaced))
                Spacer()
                if case .connected = session.state { Image(systemName: "checkmark.circle.fill").font(.title).foregroundStyle(.green) }
            }
            Text("Enter this one-time code on Windows or macOS. It changes whenever the sender restarts.")
                .font(.footnote).foregroundStyle(.secondary)
            Label(session.connectionAddress, systemImage: "network")
                .font(.footnote.monospaced()).textSelection(.enabled)
        }
        .padding(20).background(.background, in: RoundedRectangle(cornerRadius: 22))
    }

    private var destinations: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Works with").font(.title2.bold())
            HStack(spacing: 12) {
                destination("macOS", "macbook", "Video • Input • Clipboard")
                destination("Windows", "pc", "Video • Input • Clipboard")
            }
        }
    }

    private func destination(_ title: String, _ icon: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Image(systemName: icon).font(.title).foregroundStyle(.blue)
            Text(title).font(.headline)
            Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(16)
        .background(.background, in: RoundedRectangle(cornerRadius: 18))
    }

    private var privacyCard: some View {
        Label("ReplayKit always shows the system recording indicator. Connections must pass the pairing challenge before any frames are sent.", systemImage: "hand.raised.fill")
            .font(.footnote).foregroundStyle(.secondary).padding(16)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }

    private var statusBadge: some View {
        HStack(spacing: 6) { Circle().fill(statusColor).frame(width: 8, height: 8); Text(shortStatus) }
            .font(.caption.bold()).padding(.horizontal, 10).padding(.vertical, 7).background(.thinMaterial, in: Capsule())
    }

    private var isRunning: Bool { session.state != .idle }
    private var pageBackground: Color {
#if os(iOS)
        Color(uiColor: .systemGroupedBackground)
#else
        Color(nsColor: .windowBackgroundColor)
#endif
    }
    private var buttonTitle: String { isRunning ? "Stop desktop session" : "Start desktop session" }
    private var statusColor: Color { if case .connected = session.state { return .green }; return isRunning ? .orange : .secondary }
    private var shortStatus: String { if case .connected = session.state { return "Connected" }; return isRunning ? "Waiting" : "Offline" }
    private var statusText: String {
        switch session.state {
        case .idle: return "Stream a private workspace to a Mac or Windows PC while keeping your phone usable."
        case .advertising: return "Waiting for an authenticated receiver on your local network."
        case .connected(let name): return "Connected securely to \(name). \(session.framesSent) frames sent."
        case .failed(let message): return message
        }
    }
}
