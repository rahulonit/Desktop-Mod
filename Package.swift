// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "UniversalMobileDesktopApple",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "UniversalDesktopIOS", targets: ["IOSApp"]),
        .executable(name: "UniversalDesktopReceiver", targets: ["MacReceiver"]),
    ],
    targets: [
        .target(name: "DesktopProtocol", path: "ios/Transport"),
        .target(name: "IOSStreaming", dependencies: ["DesktopProtocol"], path: "ios/Streaming"),
        .target(name: "IOSDesktopEngine", dependencies: ["DesktopProtocol", "IOSStreaming"], path: "ios/DesktopEngine"),
        .target(name: "IOSDesktopUI", dependencies: ["IOSDesktopEngine"], path: "ios/DesktopUI", resources: [.process("Resources")]),
        .target(name: "IOSApp", dependencies: ["IOSDesktopUI"], path: "ios/App"),
        .target(name: "MacTransport", dependencies: ["DesktopProtocol"], path: "macos/Transport"),
        .target(name: "MacInput", dependencies: ["DesktopProtocol"], path: "macos/Input"),
        .target(name: "MacVideo", dependencies: ["DesktopProtocol"], path: "macos/Video"),
        .executableTarget(
            name: "MacReceiver",
            dependencies: ["DesktopProtocol", "MacTransport", "MacInput", "MacVideo"],
            path: "macos/Receiver",
            resources: [.process("Resources")]
        ),
        .testTarget(name: "DesktopProtocolTests", dependencies: ["DesktopProtocol"], path: "tests/apple"),
    ]
)
