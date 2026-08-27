# macOS receiver

Open `Package.swift` in Xcode and run the `UniversalDesktopReceiver` scheme on macOS 13 or later. Enter the Android phone or iPhone address and its six-digit code to complete the HMAC challenge-response handshake. The receiver accepts Android H.264 through VideoToolbox and iOS ReplayKit JPEG frames, and sends local keyboard/mouse and clipboard packets using the shared protocol.

The receiver installs both local and global event monitors. macOS will prompt for Accessibility/Input Monitoring permission when capture starts; grant it in System Settings to keep keyboard and pointer forwarding active when the receiver window is not focused.
