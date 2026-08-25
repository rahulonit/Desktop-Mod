# Universal Desktop Receiver

Windows receiver prototype for Universal Mobile Desktop.

## Run

Install the .NET 8 SDK, then run:

```powershell
dotnet run --project UniversalDesktopReceiver.csproj
```

The current build provides the receiver UI, session lifecycle, telemetry, and input/video protocol boundaries. The **Start demo session** button exercises the receiver shell without claiming that USB video transport is complete.

## Next transport milestone

1. Define a versioned session handshake and framed message format.
2. Add trusted-device pairing and authentication.
3. Implement Android H.264 encoder output.
4. Implement Windows Media Foundation decoding.
5. Route pointer and keyboard messages back to Android.
