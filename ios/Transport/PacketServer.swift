import Foundation
import Network

/// A small TCP server used by the iOS sender. It accepts one or more receivers,
/// decodes their input packets, and can broadcast encoded video packets.
public final class PacketServer: @unchecked Sendable {
    public var onPacket: (@Sendable (Packet) -> Void)?
    public var onPeerCountChanged: (@Sendable (Int) -> Void)?
    public let pairingCode: String

    private let queue = DispatchQueue(label: "dev.desktopmod.sender.transport")
    private var listener: NWListener?
    private var peers: [ObjectIdentifier: NWConnection] = [:]
    private var decoders: [ObjectIdentifier: PacketDecoder] = [:]
    private var challenges: [ObjectIdentifier: Data] = [:]
    private var authenticated = Set<ObjectIdentifier>()

    public init() { pairingCode = String(format: "%06d", Int.random(in: 100_000...999_999)) }

    public func start(port: UInt16 = 5000) throws {
        guard listener == nil, let endpointPort = NWEndpoint.Port(rawValue: port) else { return }
        let listener = try NWListener(using: .tcp, on: endpointPort)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] in self?.accept($0) }
        listener.start(queue: queue)
    }

    public func broadcast(_ packet: Packet) {
        guard let data = try? packet.encoded() else { return }
        queue.async { [weak self] in
            guard let self else { return }
            for (id, peer) in self.peers where self.authenticated.contains(id) {
                peer.send(content: data, completion: .contentProcessed { _ in })
            }
        }
    }

    public func stop() {
        queue.async { [weak self] in
            guard let self else { return }
            self.listener?.cancel()
            self.listener = nil
            self.peers.values.forEach { $0.cancel() }
            self.peers.removeAll()
            self.decoders.removeAll()
            self.challenges.removeAll(); self.authenticated.removeAll()
            self.onPeerCountChanged?(0)
        }
    }

    private func accept(_ connection: NWConnection) {
        let id = ObjectIdentifier(connection)
        peers[id] = connection
        decoders[id] = PacketDecoder()
        let challenge = Data((0..<32).map { _ in UInt8.random(in: .min ... .max) })
        challenges[id] = challenge
        connection.stateUpdateHandler = { [weak self, weak connection] state in
            if case .failed = state { if let connection { self?.remove(connection) } }
            if case .cancelled = state { if let connection { self?.remove(connection) } }
        }
        connection.start(queue: queue)
        send(Packet(type: .handshake, payload: challenge), to: connection)
        receive(from: connection)
    }

    private func receive(from connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self, weak connection] data, _, complete, error in
            guard let self, let connection else { return }
            let id = ObjectIdentifier(connection)
            if let data {
                do {
                    var decoder = self.decoders[id] ?? PacketDecoder()
                    let packets = try decoder.append(data)
                    self.decoders[id] = decoder
                    for packet in packets {
                        if !self.authenticated.contains(id) {
                            guard packet.type == .pairingResponse,
                                  let challenge = self.challenges[id],
                                  PairingSecurity.verify(code: self.pairingCode, challenge: challenge, response: packet.payload) else {
                                self.send(Packet(type: .pairingResponse, payload: Data([0])), to: connection) { [weak self, weak connection] in
                                    if let connection { self?.remove(connection) }
                                }
                                return
                            }
                            self.authenticated.insert(id); self.challenges[id] = nil
                            self.send(Packet(type: .pairingResponse, payload: Data([1])), to: connection)
                            self.onPeerCountChanged?(self.authenticated.count)
                        } else { self.onPacket?(packet) }
                    }
                } catch { self.remove(connection); return }
            }
            if complete || error != nil { self.remove(connection) } else { self.receive(from: connection) }
        }
    }

    private func remove(_ connection: NWConnection) {
        let id = ObjectIdentifier(connection)
        connection.cancel()
        peers[id] = nil
        decoders[id] = nil
        challenges[id] = nil; authenticated.remove(id)
        onPeerCountChanged?(authenticated.count)
    }

    private func send(_ packet: Packet, to connection: NWConnection, completion: (@Sendable () -> Void)? = nil) {
        guard let bytes = try? packet.encoded() else { return }
        connection.send(content: bytes, completion: .contentProcessed { _ in completion?() })
    }
}
