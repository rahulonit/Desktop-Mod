import Foundation

public enum AdbDetector {
    public static func configureForwarding(completion: @escaping @Sendable (Result<String, Error>) -> Void) {
        DispatchQueue.global(qos: .utility).async {
            do {
                let devices = try run(["devices"])
                let authorized = devices.split(separator: "\n").dropFirst().filter { $0.hasSuffix("\tdevice") }
                guard authorized.count == 1 else {
                    throw NSError(domain: "DesktopModADB", code: 1, userInfo: [NSLocalizedDescriptionKey: authorized.isEmpty ? "No authorized Android device" : "More than one Android device is connected"])
                }
                _ = try run(["forward", "tcp:5000", "tcp:5000"])
                completion(.success("127.0.0.1"))
            } catch { completion(.failure(error)) }
        }
    }

    private static func run(_ arguments: [String]) throws -> String {
        let process = Process()
        let output = Pipe()
        let errors = Pipe()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        process.arguments = ["adb"] + arguments
        process.standardOutput = output
        process.standardError = errors
        try process.run(); process.waitUntilExit()
        let errorText = String(data: errors.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
        guard process.terminationStatus == 0 else { throw NSError(domain: "DesktopModADB", code: Int(process.terminationStatus), userInfo: [NSLocalizedDescriptionKey: errorText]) }
        return String(data: output.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
    }
}
