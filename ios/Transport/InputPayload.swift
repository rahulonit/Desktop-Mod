import Foundation

public enum InputPayload {
    public static func mouse(x: Int32, y: Int32, action: Int32) -> Data {
        words([x, y, action])
    }

    public static func key(code: Int32, isDown: Bool) -> Data {
        var data = words([code])
        data.append(isDown ? 1 : 0)
        return data
    }

    private static func words(_ values: [Int32]) -> Data {
        values.reduce(into: Data()) { result, value in
            var encoded = value.bigEndian
            result.append(Data(bytes: &encoded, count: 4))
        }
    }
}
