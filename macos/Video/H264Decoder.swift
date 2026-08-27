import AppKit
import CoreImage
import CoreMedia
import Foundation
import VideoToolbox

public final class H264Decoder: @unchecked Sendable {
    public var onImage: (@Sendable (NSImage) -> Void)?
    private var session: VTDecompressionSession?
    private var format: CMVideoFormatDescription?
    private var sps: Data?
    private var pps: Data?
    private let imageContext = CIContext(options: [.cacheIntermediates: false])

    public init() {}

    public func decode(_ accessUnit: Data) {
        let units = Self.annexBUnits(accessUnit)
        guard !units.isEmpty else { return }
        var frameUnits: [Data] = []
        var parameterSetsChanged = false
        for unit in units where !unit.isEmpty {
            switch unit[unit.startIndex] & 0x1F {
            case 7:
                if sps != unit { sps = unit; parameterSetsChanged = true }
            case 8:
                if pps != unit { pps = unit; parameterSetsChanged = true }
            default: frameUnits.append(unit)
            }
        }
        if parameterSetsChanged { rebuildSession() }
        if !frameUnits.isEmpty { decodeUnits(frameUnits) }
    }

    private func rebuildSession() {
        guard let sps, let pps else { return }
        if let session { VTDecompressionSessionInvalidate(session) }
        var description: CMFormatDescription?
        let status = sps.withUnsafeBytes { spsBytes in
            pps.withUnsafeBytes { ppsBytes in
                let pointers = [spsBytes.baseAddress!.assumingMemoryBound(to: UInt8.self), ppsBytes.baseAddress!.assumingMemoryBound(to: UInt8.self)]
                let sizes = [sps.count, pps.count]
                return CMVideoFormatDescriptionCreateFromH264ParameterSets(
                    allocator: kCFAllocatorDefault, parameterSetCount: 2,
                    parameterSetPointers: pointers, parameterSetSizes: sizes,
                    nalUnitHeaderLength: 4, formatDescriptionOut: &description
                )
            }
        }
        guard status == noErr, let videoDescription = description else { return }
        format = videoDescription
        var callback = VTDecompressionOutputCallbackRecord(
            decompressionOutputCallback: { refCon, _, status, _, imageBuffer, _, _ in
                guard status == noErr, let refCon, let imageBuffer else { return }
                Unmanaged<H264Decoder>.fromOpaque(refCon).takeUnretainedValue().emit(imageBuffer)
            },
            decompressionOutputRefCon: Unmanaged.passUnretained(self).toOpaque()
        )
        let imageAttributes: [CFString: Any] = [
            kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_32BGRA,
            kCVPixelBufferIOSurfacePropertiesKey: [:] as CFDictionary,
        ]
        VTDecompressionSessionCreate(
            allocator: kCFAllocatorDefault, formatDescription: videoDescription,
            decoderSpecification: nil, imageBufferAttributes: imageAttributes as CFDictionary,
            outputCallback: &callback, decompressionSessionOut: &session
        )
    }

    private func decodeUnits(_ units: [Data]) {
        guard let session, let format else { return }
        var avcc = Data()
        for unit in units {
            var length = UInt32(unit.count).bigEndian
            avcc.append(Data(bytes: &length, count: 4)); avcc.append(unit)
        }
        var block: CMBlockBuffer?
        guard CMBlockBufferCreateWithMemoryBlock(
            allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: avcc.count,
            blockAllocator: kCFAllocatorDefault, customBlockSource: nil,
            offsetToData: 0, dataLength: avcc.count, flags: 0, blockBufferOut: &block
        ) == kCMBlockBufferNoErr, let block else { return }
        avcc.withUnsafeBytes { bytes in
            _ = CMBlockBufferReplaceDataBytes(with: bytes.baseAddress!, blockBuffer: block, offsetIntoDestination: 0, dataLength: avcc.count)
        }
        var sample: CMSampleBuffer?
        var size = avcc.count
        guard CMSampleBufferCreateReady(
            allocator: kCFAllocatorDefault, dataBuffer: block, formatDescription: format,
            sampleCount: 1, sampleTimingEntryCount: 0, sampleTimingArray: nil,
            sampleSizeEntryCount: 1, sampleSizeArray: &size, sampleBufferOut: &sample
        ) == noErr, let sample else { return }
        VTDecompressionSessionDecodeFrame(session, sampleBuffer: sample, flags: [.enableAsynchronousDecompression], frameRefcon: nil, infoFlagsOut: nil)
    }

    private func emit(_ buffer: CVImageBuffer) {
        let ciImage = CIImage(cvPixelBuffer: buffer)
        guard let cgImage = imageContext.createCGImage(ciImage, from: ciImage.extent) else { return }
        onImage?(NSImage(cgImage: cgImage, size: .zero))
    }

    private static func annexBUnits(_ data: Data) -> [Data] {
        let bytes = [UInt8](data)
        var starts: [(Int, Int)] = []
        var index = 0
        while index + 3 < bytes.count {
            if bytes[index] == 0 && bytes[index + 1] == 0 && bytes[index + 2] == 1 { starts.append((index, 3)); index += 3 }
            else if index + 4 <= bytes.count && bytes[index] == 0 && bytes[index + 1] == 0 && bytes[index + 2] == 0 && bytes[index + 3] == 1 { starts.append((index, 4)); index += 4 }
            else { index += 1 }
        }
        guard !starts.isEmpty else { return [data] }
        return starts.enumerated().map { item in
            let start = item.element.0 + item.element.1
            let end = item.offset + 1 < starts.count ? starts[item.offset + 1].0 : bytes.count
            return Data(bytes[start..<end])
        }
    }

    deinit { if let session { VTDecompressionSessionInvalidate(session) } }
}
