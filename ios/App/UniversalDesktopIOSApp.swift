import IOSDesktopUI
import SwiftUI

/// Use this scene from an iOS App target in Xcode. Swift Package Manager exposes
/// it as a library so the shared Apple code can be compiled and tested in CI.
public struct UniversalDesktopIOSScene: Scene {
    public init() {}
    public var body: some Scene { WindowGroup { DesktopRootView() } }
}
