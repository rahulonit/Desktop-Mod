import DesktopProtocol
import Foundation
import Network

public final class ReceiverConnection: @unchecked Sendable {
    public var onPacket: (@Sendable (Packet) -> Void)?
    public var onStateChange: (@Sendable (NWConnection.State) -> Void)?
    private let queue = DispatchQueue(label: "dev.desktopmod.receiver.transport")
    private var connection: NWConnection?
    private var decoder = PacketDecoder()

    public init() {}

    public func connect(host: String, port: UInt16 = 5000) {
        guard let endpointPort = NWEndpoint.Port(rawValue: port) else { return }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: endpointPort, using: .tcp)
        self.connection = connection
        connection.stateUpdateHandler = { [weak self] state in self?.onStateChange?(state) }
        connection.start(queue: queue)
        receiveNext()
    }

    public func send(_ packet: Packet) {
        guard let data = try? packet.encoded() else { return }
        connection?.send(content: data, completion: .contentProcessed { _ in })
    }

    public func disconnect() { connection?.cancel(); connection = nil }

    private func receiveNext() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 1_048_576) { [weak self] data, _, complete, error in
            guard let self else { return }
            if let data {
                do { try self.decoder.append(data).forEach { self.onPacket?($0) } }
                catch { self.connection?.cancel(); return }
            }
            if !complete && error == nil { self.receiveNext() }
        }
    }
}
