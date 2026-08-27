import DesktopProtocol
import XCTest

final class PacketTests: XCTestCase {
    func testPacketRoundTripAcrossPartialReads() throws {
        let packet = Packet(type: .videoFrame, payload: Data([0x10, 0x20, 0x30]))
        let encoded = try packet.encoded()
        var decoder = PacketDecoder()
        XCTAssertTrue(try decoder.append(encoded.prefix(2)).isEmpty)
        XCTAssertEqual(try decoder.append(encoded.dropFirst(2)), [packet])
    }

    func testAndroidCompatibleHeaderIsBigEndian() throws {
        let encoded = try Packet(type: .handshake, payload: Data([1, 2])).encoded()
        XCTAssertEqual(Array(encoded.prefix(5)), [0, 0, 0, 2, PacketType.handshake.rawValue])
    }

    func testRejectsOversizedPayloadHeader() {
        var decoder = PacketDecoder()
        XCTAssertThrowsError(try decoder.append(Data([0x02, 0x00, 0x00, 0x01, 0x04])))
    }
}
