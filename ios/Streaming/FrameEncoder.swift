import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

public enum FrameEncoderError: Error { case destinationCreationFailed, encodingFailed }

public struct FrameEncoder: Sendable {
    public init() {}

    public func jpeg(_ image: CGImage, quality: Double = 0.82) throws -> Data {
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output, UTType.jpeg.identifier as CFString, 1, nil
        ) else { throw FrameEncoderError.destinationCreationFailed }
        let options = [kCGImageDestinationLossyCompressionQuality: min(max(quality, 0), 1)] as CFDictionary
        CGImageDestinationAddImage(destination, image, options)
        guard CGImageDestinationFinalize(destination) else { throw FrameEncoderError.encodingFailed }
        return output as Data
    }
}
