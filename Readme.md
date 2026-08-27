# Universal Mobile Desktop

> Turn your Android phone or iPhone into a desktop workspace on Windows, macOS, monitors, TVs, and supported external displays.

Universal Mobile Desktop is a cross-platform desktop-mode platform inspired by Samsung DeX, but designed to work across a much wider range of Android and iOS devices.

The goal is **not simple screen mirroring**.

Instead, the phone remains usable while the connected computer, monitor, or TV shows a separate desktop-style interface with windows, files, browser, taskbar, keyboard/mouse support, and other productivity features.

---

## Table of Contents

- [Project Vision](#project-vision)
- [Core Idea](#core-idea)
- [Target Platforms](#target-platforms)
- [Connection Modes](#connection-modes)
- [Supported Device Categories](#supported-device-categories)
- [Platform Limitations](#platform-limitations)
- [Core Features](#core-features)
- [Desktop Experience](#desktop-experience)
- [Phone Controller Mode](#phone-controller-mode)
- [Architecture](#architecture)
- [Recommended Technology Stack](#recommended-technology-stack)
- [System Requirements](#system-requirements)
- [Android Requirements](#android-requirements)
- [iPhone Requirements](#iphone-requirements)
- [Windows Requirements](#windows-requirements)
- [macOS Requirements](#macos-requirements)
- [External Display Requirements](#external-display-requirements)
- [Networking Requirements](#networking-requirements)
- [USB Requirements](#usb-requirements)
- [Desktop Streaming Pipeline](#desktop-streaming-pipeline)
- [Input Pipeline](#input-pipeline)
- [File Management](#file-management)
- [Clipboard](#clipboard)
- [Third-Party Apps](#third-party-apps)
- [Security](#security)
- [Permissions](#permissions)
- [Compatibility Detection](#compatibility-detection)
- [MVP Scope](#mvp-scope)
- [Development Roadmap](#development-roadmap)
- [Suggested Repository Structure](#suggested-repository-structure)
- [Build Strategy](#build-strategy)
- [Performance Targets](#performance-targets)
- [Testing Matrix](#testing-matrix)
- [Known Limitations](#known-limitations)
- [Future Hardware Dock](#future-hardware-dock)
- [Distribution](#distribution)
- [Contribution Guidelines](#contribution-guidelines)
- [License](#license)

---

# Project Vision

Modern smartphones already contain powerful CPUs, GPUs, storage, internet connectivity, cameras, secure authentication, and productivity apps.

Universal Mobile Desktop aims to make that computing power usable as a desktop workstation.

The product vision is:

```text
Android / iPhone
       |
       v
Universal Mobile Desktop
       |
       +----------------+----------------+----------------+
       |                |                |                |
       v                v                v                v
    Windows           macOS          Monitor / TV      Wireless
       |                |                |                |
       +----------------+----------------+----------------+
                            |
                            v
                     Desktop Workspace
```

Main product promise:

> **Connect your phone. Get a desktop.**

Possible positioning:

> **Your Phone. Your Desktop. Anywhere.**

---

# Core Idea

Traditional screen mirroring works like this:

```text
PHONE
+------------------+
| Mobile UI        |
+------------------+
        |
        | Mirror
        v
MONITOR
+------------------+
| Same Mobile UI   |
+------------------+
```

Universal Mobile Desktop should work like this:

```text
PHONE                              EXTERNAL SCREEN

+------------------+              +------------------------------+
| Normal Phone UI  |              | Desktop UI                   |
|                  |              |                              |
| Trackpad         |              | Files   Browser   Apps       |
| Keyboard         |              |                              |
| Controls         |              | Floating Windows             |
+------------------+              +------------------------------+
```

The phone interface and desktop interface are separate whenever the hardware and operating system allow it.

---

# Target Platforms

## Mobile Platforms

### Android

Primary target platform.

Target device families include:

- Samsung Galaxy
- Google Pixel
- Motorola
- OnePlus
- OPPO
- Vivo
- Realme
- Xiaomi
- Redmi
- Nothing
- ASUS
- Sony
- Other Android manufacturers

Support level depends on:

- Android version
- USB hardware
- external display capability
- OEM restrictions
- secondary display support
- available codecs
- USB OTG support
- network capabilities

### iPhone

Supported through a separate iOS implementation.

Primary use cases:

- iPhone -> Windows
- iPhone -> macOS
- iPhone -> supported external display
- iPhone -> wireless receiver

iOS has stricter application sandboxing than Android, so third-party native app windowing is more limited.

---

## Desktop Platforms

### Windows

Primary desktop receiver platform for MVP.

Planned support:

- USB connection
- Wi-Fi/LAN connection
- desktop viewer
- mouse input
- keyboard input
- file transfer
- clipboard sync
- resolution controls
- device pairing

### macOS

Planned after Windows MVP.

Planned support:

- USB/network desktop session
- desktop viewer
- mouse and keyboard input
- file transfer
- clipboard
- pairing
- external display management

---

## External Displays

Potential targets:

- HDMI monitors
- USB-C monitors
- TVs
- projectors
- smart TVs
- Android TV devices
- receiver dongles
- future Universal Desktop Dock

---

# Connection Modes

Universal Mobile Desktop should support multiple transport modes.

---

## Mode 1 - USB to Windows

Recommended first MVP.

```text
Android Phone
      |
      | USB
      v
Windows Receiver
      |
      v
Desktop Mode
```

Advantages:

- low latency
- no Wi-Fi required
- reliable connection
- works with phones without native HDMI output
- suitable for devices such as OPPO F19 Pro
- easier first prototype

---

## Mode 2 - USB to macOS

```text
Phone
  |
  | USB
  v
Mac
  |
  v
Universal Desktop Receiver
```

The macOS receiver performs the same basic role as the Windows application.

---

## Mode 3 - Wireless Desktop

```text
Phone
  |
  | Wi-Fi / LAN
  v
Receiver
  |
  +---- Windows
  +---- macOS
  +---- Android TV
  +---- Smart TV receiver
```

Possible transport technologies:

- WebRTC
- QUIC
- TCP
- UDP
- WebSocket for control messages
- mDNS / Bonjour for local discovery

---

## Mode 4 - Native External Display

For devices that expose a real secondary display.

```text
Phone
  |
  | USB-C / DisplayPort / HDMI
  v
Monitor
```

Preferred behavior:

```text
Display 0 = Phone
Display 1 = Desktop
```

The phone remains usable while the monitor shows the desktop workspace.

---

## Mode 5 - Mirror Desktop Fallback

For devices that can only mirror.

```text
Phone launches Desktop UI full-screen
                |
                v
TV / monitor mirrors Desktop UI
```

Limit:

The phone cannot remain independent in this mode.

---

## Mode 6 - Future USB Graphics / Dock Mode

Future hardware path:

```text
Phone
  |
  | USB
  v
Universal Desktop Dock
  |
  | HDMI
  v
Monitor
```

This could help phones that do not support DisplayPort Alt Mode.

---

# Supported Device Categories

Compatibility should be detected automatically rather than relying only on a hard-coded phone list.

---

## Full Support

Requirements may include:

- real secondary display detected
- multi-display support
- external activity support
- supported input path
- supported rendering pipeline

Experience:

```text
Phone = normal mobile UI
Monitor = desktop UI
```

---

## Standard Support

For phones without native video output but with USB/network connectivity.

Experience:

```text
Phone
  |
  | USB
  v
Windows / macOS
  |
  v
Desktop
```

Example target:

- OPPO F19 Pro

---

## Wireless Support

For phones without usable wired display support.

Experience:

```text
Phone
  |
  | Wi-Fi
  v
PC / TV Receiver
  |
  v
Desktop
```

---

## Fallback Support

For mirror-only devices.

Experience:

```text
Phone shows desktop full-screen
       |
       v
TV mirrors desktop
```

---

# Platform Limitations

## Android

Android provides more flexibility, but capabilities differ between manufacturers.

A normal application cannot always:

- control the hardware display compositor
- force DisplayPort Alt Mode
- create physical HDMI output where hardware does not support it
- freely embed every third-party Android application
- bypass OEM multi-display restrictions
- control other apps without system privileges

---

## iPhone

iOS is more restrictive.

A normal App Store application cannot:

- replace iOS with a desktop shell
- embed arbitrary third-party iOS apps inside custom windows
- control other apps freely
- bypass app sandboxing
- expose unrestricted system internals

Therefore the iPhone desktop should focus on:

- built-in desktop modules
- browser
- files
- media
- cloud documents
- web applications
- approved document providers
- desktop streaming
- phone-as-controller

---

# Core Features

## Desktop Shell

- desktop home
- taskbar
- start/app menu
- desktop icons
- wallpapers
- system tray
- clock
- battery status
- connection status
- quick settings

---

## Window Manager

For built-in applications:

- move
- resize
- minimize
- maximize
- close
- snap left
- snap right
- tiled layout
- multiple windows
- app switching
- focus management
- z-index management

---

## Built-In Applications

Initial built-in desktop apps:

- File Manager
- Browser
- Gallery
- Media Player
- Notes
- Settings
- Downloads
- Device Manager
- Connection Manager

Future possibilities:

- PDF viewer
- terminal-like developer tools
- cloud drive browser
- calendar
- mail web app launcher
- media center
- office web apps

---

# Desktop Experience

Example layout:

```text
+----------------------------------------------------------+
|                                                          |
|   Files        Browser        Photos        Media        |
|                                                          |
|          +-----------------------------------+           |
|          | Browser                       _ [] X|          |
|          +-----------------------------------+           |
|          |                                   |           |
|          |          Desktop Website          |           |
|          |                                   |           |
|          +-----------------------------------+           |
|                                                          |
+----------------------------------------------------------+
| Start  Search  Files  Browser  Apps      Wi-Fi Vol Batt |
+----------------------------------------------------------+
```

---

# Phone Controller Mode

When the desktop is active, the phone can become a controller.

Example:

```text
+----------------------+
| Desktop Connected    |
|                      |
|      TRACKPAD        |
|                      |
|                      |
+----------+-----------+
| Left     | Right     |
| Click    | Click     |
+----------+-----------+
| Keyboard | Settings  |
+----------------------+
```

Suggested gestures:

- one finger move -> cursor
- tap -> left click
- two-finger tap -> right click
- two-finger swipe -> scroll
- long press + move -> drag
- pinch -> zoom
- three-finger swipe -> switch apps
- keyboard button -> open virtual keyboard

---

# Architecture

High-level architecture:

```text
                    UNIVERSAL MOBILE DESKTOP
                              |
                +-------------+-------------+
                |                           |
           Android Core                  iOS Core
                |                           |
                +-------------+-------------+
                              |
                       Desktop Protocol
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
   Windows Receiver      macOS Receiver      External Display
```

---

## Mobile Side Components

```text
Mobile App
|
+-- Desktop Engine
+-- Desktop Shell
+-- Window Manager
+-- Display Manager
+-- Input Manager
+-- Storage Manager
+-- Streaming Encoder
+-- USB Transport
+-- Network Transport
+-- Device Pairing
+-- Permissions Manager
+-- Compatibility Checker
+-- Trackpad Controller
```

---

## Receiver Side Components

```text
Desktop Receiver
|
+-- Device Discovery
+-- Pairing Manager
+-- USB Transport
+-- Network Transport
+-- Video Decoder
+-- Desktop Renderer
+-- Mouse Capture
+-- Keyboard Capture
+-- Clipboard Manager
+-- File Transfer Manager
+-- Settings
+-- Logging
```

---

# Recommended Technology Stack

## Android

Recommended:

- Kotlin
- Jetpack Compose
- Android SDK
- Coroutines
- Flow
- Room
- DataStore

Useful Android APIs:

- DisplayManager
- Presentation
- VirtualDisplay
- ActivityOptions
- MediaCodec
- MediaProjection where appropriate
- Storage Access Framework
- USB APIs
- Bluetooth APIs
- InputDevice
- Surface / SurfaceView
- Network APIs

---

## iPhone

Recommended:

- Swift
- SwiftUI
- UIKit where required
- Network.framework
- VideoToolbox
- AVFoundation
- FileProvider / document APIs where appropriate
- External display scene APIs

---

## Windows Receiver

Possible options:

### Preferred

- C++
- C#
- WinUI
- .NET
- FFmpeg / native codecs where needed

Alternative:

- Qt
- Flutter Desktop
- Rust + native UI
- Electron only if rapid prototyping is more important than memory footprint

For low-latency production streaming, native technologies are preferred.

---

## macOS Receiver

Recommended:

- Swift
- AppKit
- SwiftUI
- VideoToolbox
- Network.framework

Alternative:

- Qt
- Rust
- shared cross-platform receiver core

---

## Shared Protocol

Potential technologies:

- Protocol Buffers
- FlatBuffers
- MessagePack
- JSON for early prototype only

Transport:

- TCP for reliability
- UDP/QUIC for low latency
- WebRTC for wireless streaming
- WebSocket/DataChannel for controls

---

# System Requirements

These are initial development targets and may change during testing.

---

# Android Requirements

## Minimum Prototype Target

Recommended development baseline:

- Android 10 or newer
- USB data support
- USB debugging only if required during development
- at least 4 GB RAM recommended
- hardware H.264 encoder recommended
- Wi-Fi for wireless mode
- USB OTG recommended
- USB-C preferred

## Preferred Devices

Preferred:

- Android 12+
- 6 GB+ RAM
- hardware H.264 / HEVC encoding
- USB 3.x preferred for high-bandwidth wired use
- Wi-Fi 5 or newer
- secondary display support for native external-display mode

## HDMI / USB-C External Display

For a passive USB-C -> HDMI adapter, the device must support native video output such as:

- DisplayPort Alt Mode
- OEM-supported USB-C display output

If the phone does not support hardware video output, software alone cannot make a passive HDMI adapter work.

---

# iPhone Requirements

Initial target:

- modern iPhone capable of running the supported iOS version
- Wi-Fi for wireless mode
- USB or USB-C/Lightning connection depending on model
- supported external display adapter for direct monitor mode
- hardware video encoding support

Recommended:

- USB-C iPhone models for easier accessory compatibility
- recent iOS version
- Wi-Fi 5/6
- sufficient free storage for cache and temporary files

---

# Windows Requirements

Initial MVP target:

- Windows 10 64-bit or newer
- Windows 11 recommended
- USB 2.0 minimum
- USB 3.x recommended
- Wi-Fi or Ethernet for wireless mode
- hardware H.264 decoding preferred
- 4 GB RAM minimum
- 8 GB RAM recommended
- modern integrated or discrete GPU recommended

Optional:

- Bluetooth
- gamepad
- multi-monitor setup

---

# macOS Requirements

Initial target:

- modern Intel or Apple Silicon Mac
- recent macOS version
- USB connection
- Wi-Fi or Ethernet
- hardware video decode support
- 8 GB RAM recommended

Apple Silicon is preferred for performance and media efficiency.

---

# External Display Requirements

## Native Mode

Display device:

- HDMI monitor
- DisplayPort monitor
- USB-C monitor
- TV
- projector

Phone must support:

- physical external display output
- or compatible external display technology

---

## Receiver Mode

The screen itself does not need direct phone compatibility.

Example:

```text
Phone -> USB -> Windows PC -> PC Monitor
```

The PC becomes the receiver.

---

# Networking Requirements

Wireless mode should support:

- local Wi-Fi
- local Ethernet network
- peer-to-peer where available

Recommended:

- 5 GHz Wi-Fi
- Wi-Fi 5 minimum recommended
- Wi-Fi 6 preferred
- low-latency local network
- same LAN for discovery

Internet should not be required for local desktop mode.

---

# USB Requirements

## Android -> Windows / macOS

Required:

- data-capable USB cable
- not charge-only cable
- compatible USB driver / transport
- user authorization

Preferred:

- USB 3.x

USB 2.0 should remain a target for broader compatibility.

---

## Example: OPPO F19 Pro

Target support:

```text
OPPO F19 Pro
      |
      | USB data cable
      v
Windows Receiver
      |
      v
Desktop Workspace
```

The F19 Pro is useful as a reference device because it does not provide a Samsung-DeX-style experience.

---

# Desktop Streaming Pipeline

Recommended architecture:

```text
Desktop Renderer
      |
      v
Render Surface
      |
      v
Hardware Encoder
      |
      +-- H.264
      +-- HEVC
      +-- AV1 future
      |
      v
USB / Network Transport
      |
      v
Desktop Receiver
      |
      v
Hardware Decoder
      |
      v
Desktop Window
```

---

# Input Pipeline

```text
Mouse / Keyboard / Gamepad
          |
          v
Desktop Receiver
          |
          v
Input Protocol
          |
          v
USB / Network
          |
          v
Mobile Desktop Engine
```

Input events:

- mouse movement
- clicks
- right click
- scroll
- keyboard keys
- shortcuts
- text input
- touch gestures
- gamepad buttons

---

# File Management

Desktop file manager goals:

```text
Phone Storage
|
+-- Downloads
+-- Documents
+-- Photos
+-- Videos
+-- Music
+-- App-accessible Files
+-- External USB Storage
+-- Cloud Providers
```

Features:

- browse
- open
- rename
- copy
- move
- delete
- upload
- download
- drag and drop
- desktop shortcuts
- recent files

All access must follow platform storage permissions.

---

# Clipboard

Possible features:

- copy phone -> desktop
- copy desktop -> phone
- text sync
- URL sync

Future:

- image clipboard
- file clipboard

Clipboard operations must respect Android and iOS privacy restrictions.

---

# Third-Party Apps

This is a major platform constraint.

---

## Android

Possible behavior varies by device.

Potential capabilities:

- launch installed apps
- launch apps on a secondary display on supported devices
- use Android native freeform/multi-window when available

Not guaranteed:

- embedding arbitrary apps inside our custom windows
- full external display support on every OEM
- controlling third-party app layout

---

## iPhone

More restricted.

Not supported for a normal App Store app:

- embedding arbitrary native third-party apps
- controlling other apps
- running iPhone apps inside our own desktop window manager

Recommended approach:

- built-in desktop apps
- web apps
- browser tabs
- cloud services
- Files/document providers

Possible web app examples:

- Gmail
- Google Docs
- YouTube
- Reddit
- LinkedIn
- ChatGPT
- supported messaging web apps

---

# Security

Security must be part of the architecture from the first prototype.

## Pairing

First connection:

```text
New computer detected

Device:
Rahul-PC

[Cancel]
[Allow Once]
[Always Allow]
```

Recommended:

- cryptographic device pairing
- public/private key pair
- trusted device list
- session keys
- manual revoke option

---

## Encryption

All communication should be encrypted.

Recommended:

- TLS 1.3
- authenticated local sessions
- secure key storage
- Android Keystore
- iOS Keychain

---

## No Unnecessary Cloud Relay

Local mode should work locally.

Preferred:

```text
Phone <-> PC
```

not:

```text
Phone -> Cloud -> PC
```

Cloud relay can be considered later for remote-access features.

---

## Sensitive Data

Never expose without permission:

- passwords
- banking information
- private app data
- notifications
- photos
- documents
- clipboard
- microphone
- camera

---

# Permissions

## Android

Possible permissions depending on implementation:

- local network
- Bluetooth
- media/files
- notifications
- nearby devices
- USB device access
- display/media projection when explicitly required

Avoid depending on:

- root
- permanent ADB
- accessibility abuse
- hidden APIs
- Shizuku
- bootloader unlock
- modified ROM

---

## iOS

Potential permissions:

- local network
- photos
- files/documents
- Bluetooth
- media library where required

Use the minimum permissions required.

---

# Compatibility Detection

The app should run an automatic compatibility test.

Example:

```text
DEVICE COMPATIBILITY

Device: OPPO F19 Pro

USB Data                PASS
USB OTG                 PASS
Wi-Fi                   PASS
Hardware Encoder        PASS
Native HDMI             NOT AVAILABLE
Secondary Display       NOT AVAILABLE
Windows Receiver        SUPPORTED
Wireless Receiver       SUPPORTED

Overall:
STANDARD SUPPORT
```

---

## Suggested Compatibility Levels

### Level A - Full Desktop

- secondary display
- native external video
- desktop-capable multi-display

### Level B - USB Desktop

- USB data
- desktop receiver supported

### Level C - Wireless Desktop

- Wi-Fi/network support

### Level D - Mirror Fallback

- mirroring only

---

# MVP Scope

The recommended first MVP:

> **Android -> USB -> Windows -> Independent Desktop UI**

Reference phone:

> OPPO F19 Pro

---

## MVP Android Features

- desktop shell
- taskbar
- start menu
- basic window manager
- file manager
- browser
- settings
- phone trackpad
- USB transport
- video encoder
- input receiver
- pairing
- compatibility checker

---

## MVP Windows Features

- detect connected phone
- pair with phone
- receive video stream
- decode stream
- render desktop
- capture mouse
- capture keyboard
- send input events
- connection status
- resolution selector
- basic file transfer

---

## MVP Exclusions

Do not include in first build:

- iPhone
- macOS
- custom HDMI hardware
- every Android application
- remote internet desktop
- complex cloud accounts
- multi-monitor
- 4K
- advanced gaming mode

---

# Development Roadmap

## Phase 1 - UX Prototype

Build the desktop interface without external streaming.

Deliverables:

- desktop home
- taskbar
- start menu
- window states
- browser mockup
- file manager mockup
- phone controller UI

---

## Phase 2 - Android Desktop Engine

Build:

- desktop renderer
- window manager
- app lifecycle
- resolution manager
- input layer
- session manager

---

## Phase 3 - Android -> Windows USB

Build:

- USB communication
- video encoding
- receiver
- mouse/keyboard input
- pairing

Goal:

> Connect OPPO F19 Pro to Windows and open the independent desktop.

---

## Phase 4 - File and Clipboard

Add:

- file browser
- upload/download
- drag and drop
- clipboard sync

---

## Phase 5 - Wireless Desktop

Add:

- local discovery
- QR pairing
- Wi-Fi streaming
- adaptive bitrate
- connection recovery

---

## Phase 6 - macOS Receiver

Build macOS receiver using the same desktop protocol.

---

## Phase 7 - Native Android External Displays

Add optimized mode for phones that expose Display 1.

---

## Phase 8 - iPhone

Create:

- iOS desktop engine
- controller
- network transport
- Windows receiver support
- macOS receiver support

---

## Phase 9 - External Display iPhone Mode

Add compatible wired/wireless external display presentation.

---

## Phase 10 - Universal Desktop Dock

Research custom USB graphics hardware.

---

# Suggested Repository Structure

```text
universal-mobile-desktop/
|
+-- android/
|   +-- app/
|   +-- desktop-engine/
|   +-- desktop-ui/
|   +-- transport-usb/
|   +-- transport-network/
|   +-- streaming/
|   +-- input/
|   +-- storage/
|
+-- ios/
|   +-- App/
|   +-- DesktopEngine/
|   +-- DesktopUI/
|   +-- Transport/
|   +-- Streaming/
|
+-- windows/
|   +-- Receiver/
|   +-- Transport/
|   +-- Video/
|   +-- Input/
|   +-- FileTransfer/
|
+-- macos/
|   +-- Receiver/
|   +-- Transport/
|   +-- Video/
|   +-- Input/
|
+-- protocol/
|   +-- messages/
|   +-- pairing/
|   +-- input/
|   +-- file-transfer/
|
+-- docs/
|   +-- architecture/
|   +-- compatibility/
|   +-- security/
|   +-- ui/
|
+-- tests/
|
+-- assets/
|
+-- README.md
+-- CONTRIBUTING.md
+-- LICENSE
```

---

# Build Strategy

Recommended approach:

## Stage 1

Prototype all desktop UI locally on Android.

## Stage 2

Render desktop to a dedicated surface.

## Stage 3

Encode that surface.

## Stage 4

Send encoded stream through USB.

## Stage 5

Build Windows receiver.

## Stage 6

Send mouse/keyboard events back.

## Stage 7

Add files, clipboard, and wireless transport.

This keeps the engineering risk manageable.

---

# Performance Targets

Initial targets:

## MVP

- resolution: 1280x720 or 1920x1080
- frame rate: 30 FPS minimum
- target: 60 FPS where supported
- latency: below 100 ms desirable
- input latency: as low as practical
- USB 2.0 compatibility
- hardware H.264 encoding

---

## Production Targets

- 1080p 60 FPS
- 1440p
- 4K on supported hardware
- adaptive bitrate
- dynamic resolution
- hardware encode/decode
- low-power mode
- high-performance mode

---

# Testing Matrix

Test across:

## Android Vendors

- Samsung
- OPPO
- Vivo
- Realme
- Xiaomi
- Redmi
- Pixel
- Motorola
- OnePlus
- Nothing

---

## Android Versions

At minimum:

- Android 10
- Android 11
- Android 12
- Android 13
- Android 14
- Android 15
- Android 16+

---

## USB Types

- USB 2.0 Type-C
- USB 3.x Type-C
- USB-C to USB-A
- USB-C to USB-C

---

## Desktop Platforms

- Windows 10
- Windows 11
- Intel Mac
- Apple Silicon Mac

---

## Networks

- Wi-Fi 4
- Wi-Fi 5
- Wi-Fi 6
- Ethernet
- congested network
- hotspot mode

---

# Known Limitations

1. A normal application cannot add DisplayPort output to hardware that does not have it.

2. Passive USB-C -> HDMI adapters will not work on phones without compatible video output.

3. Android OEMs may restrict external activities and multi-display behavior.

4. Arbitrary third-party Android apps cannot always be embedded inside our own custom desktop windows.

5. iOS does not allow arbitrary third-party iPhone apps to be embedded inside the desktop.

6. Some storage locations will remain inaccessible due to platform sandboxing.

7. Banking, DRM, streaming, and secure apps may block capture or external display behavior.

8. USB bandwidth varies significantly between devices.

9. Wireless latency depends on network quality.

10. Native external display behavior differs between devices.

---

# Future Hardware Dock

Potential future product:

```text
Phone
  |
  v
+------------------------+
| Universal Desktop Dock |
|                        |
| USB Graphics           |
| HDMI                   |
| USB-A                  |
| Ethernet               |
| Audio                  |
| Power Delivery         |
+-----------+------------+
            |
            v
         Monitor
```

Possible features:

- HDMI
- USB graphics controller
- keyboard/mouse hub
- Ethernet
- storage
- audio
- charging
- firmware update support

Purpose:

Enable a more consistent monitor experience across devices that lack native desktop output.

---

# Distribution

## Android

Target:

- Google Play Store
- direct APK for development/testing
- enterprise distribution if needed

The production version should avoid root-only or policy-sensitive hacks.

---

## iOS

Target:

- Apple App Store
- TestFlight for beta testing

The app must remain within iOS sandbox and external-display policies.

---

## Windows

Possible distribution:

- Microsoft Store
- signed installer
- MSIX

---

## macOS

Possible distribution:

- Mac App Store
- signed/notarized installer

---

# Contribution Guidelines

Before contributing:

1. Create an issue describing the feature or bug.
2. Use a feature branch.
3. Keep mobile, receiver, and protocol layers separated.
4. Add tests where possible.
5. Do not introduce root-only dependencies into the main production path.
6. Do not use hidden or undocumented OS APIs without clearly marking them experimental.
7. Follow platform privacy rules.
8. Document device-specific compatibility findings.

---

# Development Principles

The project should follow these principles:

- universal before OEM-specific
- local-first
- privacy-first
- low-latency
- modular architecture
- no mandatory cloud
- no root requirement
- graceful fallback
- automatic compatibility detection
- simple connection UX

---

# Product UX Goal

The user experience should eventually be:

```text
1. Install Universal Mobile Desktop
2. Install receiver on PC/Mac
3. Connect phone
4. Approve connection
5. Desktop appears
```

For supported native external displays:

```text
1. Connect monitor
2. Desktop detected
3. Desktop launches automatically
```

---

# Final Product Direction

```text
                   UNIVERSAL MOBILE DESKTOP

                           Phone
                             |
                   +---------+---------+
                   |                   |
                Android              iPhone
                   |                   |
                   +---------+---------+
                             |
                      Desktop Engine
                             |
           +-----------------+-----------------+
           |                 |                 |
          USB              Native           Wireless
           |               Display             |
           v                 v                 v
     Windows / macOS       Monitor         PC / TV
           |                 |                 |
           +-----------------+-----------------+
                             |
                             v
                     Desktop Workspace
```

The recommended first development milestone is:

> **OPPO F19 Pro -> USB -> Windows -> Independent Desktop**

If this works smoothly, the same platform can then expand to:

- other Android devices
- macOS
- iPhone
- native external displays
- wireless TV receivers
- future Universal Desktop Dock

---

# Status

**Project Stage:** Concept / Architecture / MVP Planning

## Apple platform source

The repository now includes a Swift Package for iOS and macOS. Open `Package.swift`
in Xcode to build the shared packet protocol, integrate the `UniversalDesktopIOS`
library product into an iOS App target, or run the `UniversalDesktopReceiver`
macOS executable. Platform-specific setup notes live in `ios/README.md` and
`macos/README.md`.

**Recommended First Platform Pair:** Android + Windows

**Recommended First Reference Device:** OPPO F19 Pro

**Primary Goal:** Deliver a real desktop-style workspace instead of mobile screen mirroring.

---

# License

License not yet selected.

Recommended options:

- MIT for open-source core
- Apache 2.0 for broader patent protection
- Proprietary license for commercial development
- Dual-license model for open-source + commercial components

Choose the final license before public release.
