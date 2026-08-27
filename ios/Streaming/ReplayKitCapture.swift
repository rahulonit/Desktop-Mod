import CoreImage
import CoreMedia
import Foundation
import ReplayKit

public enum ReplayKitCaptureError: Error { case missingVideoImage }

public final class ReplayKitCapture: @unchecked Sendable {
    public var onFrame: (@Sendable (CGImage) -> Void)?
    public var onError: (@Sendable (Error) -> Void)?
    private let recorder = RPScreenRecorder.shared()
    private let context = CIContext(options: [.cacheIntermediates: false])
    private let frameLock = NSLock()
    private var lastFrameTime = CFAbsoluteTimeGetCurrent()

    public init() {}

    public func start(maxFramesPerSecond: Double = 30) {
        recorder.isMicrophoneEnabled = false
        recorder.startCapture(handler: { [weak self] sample, type, error in
            guard let self else { return }
            if let error { self.onError?(error); return }
            guard type == .video else { return }
            let now = CFAbsoluteTimeGetCurrent()
            self.frameLock.lock()
            let shouldRender = now - self.lastFrameTime >= 1 / maxFramesPerSecond
            if shouldRender { self.lastFrameTime = now }
            self.frameLock.unlock()
            guard shouldRender, let buffer = CMSampleBufferGetImageBuffer(sample) else { return }

            // ReplayKit may recycle the CVPixelBuffer as soon as this handler returns.
            // Rendering here creates an independently owned CGImage before that happens.
            let image = CIImage(cvPixelBuffer: buffer)
            guard let cgImage = self.context.createCGImage(image, from: image.extent) else {
                self.onError?(ReplayKitCaptureError.missingVideoImage); return
            }
            self.onFrame?(cgImage)
        }, completionHandler: { [weak self] error in if let error { self?.onError?(error) } })
    }

    public func stop() { recorder.stopCapture { [weak self] error in if let error { self?.onError?(error) } } }
}
