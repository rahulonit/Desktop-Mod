import AppKit
import ApplicationServices
import DesktopProtocol

public final class InputCapture {
    public var onPacket: ((Packet) -> Void)?
    private var monitors: [Any] = []

    public init() {}

    public func start() {
        guard monitors.isEmpty else { return }
        let mask: NSEvent.EventTypeMask = [.mouseMoved, .leftMouseDown, .leftMouseUp, .keyDown, .keyUp, .scrollWheel]
        let promptKey = kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String
        _ = AXIsProcessTrustedWithOptions([promptKey: true] as CFDictionary)
        if let local = NSEvent.addLocalMonitorForEvents(matching: mask, handler: { [weak self] event in
            self?.handle(event); return event
        }) { monitors.append(local) }
        if let global = NSEvent.addGlobalMonitorForEvents(matching: mask, handler: { [weak self] event in
            self?.handle(event)
        }) { monitors.append(global) }
    }

    public func stop() { monitors.forEach(NSEvent.removeMonitor); monitors.removeAll() }

    private func handle(_ event: NSEvent) {
        switch event.type {
        case .keyDown, .keyUp:
            onPacket?(Packet(type: .keyEvent, payload: InputPayload.key(code: Int32(event.keyCode), isDown: event.type == .keyDown)))
        default:
            if event.type == .scrollWheel && event.scrollingDeltaY == 0 { return }
            let action: Int32 = event.type == .leftMouseDown ? 1 : event.type == .leftMouseUp ? 2 : event.type == .scrollWheel ? (event.scrollingDeltaY > 0 ? 3 : 4) : 0
            let point = event.locationInWindow
            onPacket?(Packet(type: .mouseEvent, payload: InputPayload.mouse(x: Int32(point.x), y: Int32(point.y), action: action)))
        }
    }

    deinit { stop() }
}
