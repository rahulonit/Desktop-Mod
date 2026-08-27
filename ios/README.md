# iOS client

The iOS implementation is exposed by the `UniversalDesktopIOS` Swift package product. In Xcode, create an iOS App target, add the repository as a local package, import `IOSApp`, and use `UniversalDesktopIOSScene` as the app scene.

The sender now uses ReplayKit's user-approved in-app capture API, shows a per-launch six-digit pairing code, authenticates receivers with an HMAC-SHA256 challenge, and streams codec-tagged JPEG frames through the Network.framework listener on port 5000. Both the macOS receiver and Windows receiver accept this stream. Add `NSLocalNetworkUsageDescription` to the host app's Info.plist. ReplayKit respects iOS privacy controls and does not permit silently capturing or embedding arbitrary third-party apps.
