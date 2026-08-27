using System.Drawing;
using System.Diagnostics;
using System.Buffers.Binary;
using System.Net.Sockets;
using System.Net;
using System.Text;
using System.Runtime.InteropServices;
using UniversalMobileDesktop.Protocol;
using UniversalMobileDesktop.Video;
using UniversalMobileDesktop.FileTransfer;

namespace UniversalMobileDesktop.Receiver;

public sealed class ReceiverForm : Form
{
    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
    private readonly StatusPill state = new();
    private readonly Label telemetry = new();
    private readonly RoundedButton connect = new() { Primary = true, Icon = AccentIcon.Phone };
    private readonly RoundedButton fullscreen = new() { Icon = AccentIcon.Fullscreen };
    private readonly PictureBox viewport = new();
    private readonly DesktopModDashboard dashboard = new();
    private readonly GlassPanel header = new() { Radius = 20, GlassOpacity = 118 };
    private readonly GlassPanel footer = new() { Radius = 0, GlassOpacity = 92 };
    private readonly Panel headerHost = new();
    private readonly Label fullscreenHint = new();
    private readonly System.Windows.Forms.Timer hintTimer = new() { Interval = 3500 };
    private readonly System.Windows.Forms.Timer sessionTimer = new() { Interval = 1000 };
    private int seconds;
    private volatile bool isConnected;
    private volatile bool connectionAttemptActive;
    private TcpClient? tcpClient;
    private Thread? receiveThread;
    private int frameCount;
    private readonly object sendLock = new();
    private const int DesktopWidth = 1920;
    private const int DesktopHeight = 1080;
    private bool isFullscreen;
    private FormWindowState previousWindowState;
    private FormBorderStyle previousBorderStyle;
    private UdpClient? discoverySocket;
    private Thread? discoveryThread;
    private volatile bool discoveryRunning;
    private const int DiscoveryPort = 50505;
    private readonly object frameLock = new();
    private Bitmap? latestFrame;
    private int renderPending;
    private H264Decoder? h264Decoder;
    private readonly FileTransferManager fileTransfers = new();
    private readonly System.Windows.Forms.Timer clipboardTimer = new() { Interval = 750 };
    private string lastClipboardText = string.Empty;
    private bool applyingRemoteClipboard;

    public ReceiverForm()
    {
        Text = "Desktop Mod";
        MinimumSize = new Size(1024, 720);
        Size = new Size(1280, 800);
        BackColor = Color.FromArgb(3, 7, 18);
        ForeColor = Color.White;
        StartPosition = FormStartPosition.CenterScreen;
        WindowState = FormWindowState.Maximized;
        KeyPreview = true;

        // Header
        headerHost.Dock = DockStyle.Top;
        headerHost.Height = 108;
        headerHost.BackColor = Color.FromArgb(3, 10, 29);
        header.Height = 78;
        header.Location = new Point(28, 15);
        header.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        header.Padding = new Padding(20, 12, 20, 12);
        var title = new Label { Text = "◆  Desktop Mod", AutoSize = true, Font = new Font("Segoe UI", 17, FontStyle.Bold), Location = new Point(20, 18) };
        state.Text = "● Waiting for phone";
        title.Text = "Desktop Mod";
        title.Font = new Font("Segoe UI", 17, FontStyle.Bold);
        title.Location = new Point(76, 22);
        state.Text = "●  Waiting for phone";
        state.Size = new Size(178, 40);
        state.ForeColor = Color.FromArgb(148, 163, 184);
        state.Location = new Point(280, 19);
        connect.Text = "Connect phone";
        connect.Size = new Size(216, 48);
        connect.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        connect.Location = new Point(Width - 250, 15);
        connect.Click += (_, _) => ToggleSession();
        fullscreen.Text = "Full screen";
        fullscreen.Size = new Size(202, 48);
        fullscreen.Click += (_, _) => EnterFullscreen();
        header.Resize += (_, _) => PositionHeaderButtons();
        var headerLogo = new AppLogoBadge { Location = new Point(20, 17), Size = new Size(44, 44) };
        header.Controls.AddRange([headerLogo, title, state, fullscreen, connect]);
        headerHost.Controls.Add(header);
        headerHost.Resize += (_, _) => { header.Width = Math.Max(1, headerHost.ClientSize.Width - 56); PositionHeaderButtons(); };

        // Viewport (PictureBox for rendering decoded frames)
        viewport.Dock = DockStyle.Fill;
        viewport.BackColor = Color.Black;
        viewport.SizeMode = PictureBoxSizeMode.Zoom;
        viewport.TabStop = true;
        viewport.AllowDrop = true;
        viewport.DragEnter += (_, e) => e.Effect = e.Data?.GetDataPresent(DataFormats.FileDrop) == true ? DragDropEffects.Copy : DragDropEffects.None;
        viewport.DragDrop += (_, e) => SendDroppedFiles((string[]?)e.Data?.GetData(DataFormats.FileDrop));
        viewport.MouseDown += (_, eventArgs) => SendPointer(eventArgs.Location, eventArgs.Button == MouseButtons.Right ? 5 : 1);
        viewport.MouseUp += (_, eventArgs) => SendPointer(eventArgs.Location, eventArgs.Button == MouseButtons.Right ? 6 : 2);
        viewport.MouseMove += (_, eventArgs) => SendPointer(eventArgs.Location, 0);
        viewport.MouseWheel += (_, eventArgs) => SendPointer(eventArgs.Location, eventArgs.Delta > 0 ? 3 : 4);
        viewport.MouseEnter += (_, _) => viewport.Focus();
        viewport.KeyDown += (_, eventArgs) => SendKey(eventArgs.KeyCode, true);
        viewport.KeyUp += (_, eventArgs) => SendKey(eventArgs.KeyCode, false);

        // Footer
        footer.Dock = DockStyle.Bottom;
        footer.Height = 54;
        footer.Padding = new Padding(24, 16, 24, 8);
        telemetry.Text = "Waiting for Desktop Mod on USB tethering or Wi-Fi";
        telemetry.AutoSize = true;
        telemetry.ForeColor = Color.FromArgb(148, 163, 184);
        var footerIcon = new IconBadge { Icon = AccentIcon.Shield, Accent = Color.FromArgb(45, 184, 154), Circular = true, Size = new Size(26, 26), Location = new Point(20, 13) };
        telemetry.Location = new Point(56, 17);
        footer.Controls.Add(telemetry); footer.Controls.Add(footerIcon);

        dashboard.Dock = DockStyle.Fill;
        dashboard.ConnectRequested += (_, _) => ToggleSession();
        dashboard.StartDesktopRequested += (_, _) =>
        {
            dashboard.Visible = false;
            viewport.Focus();
        };
        Controls.Add(viewport);
        Controls.Add(dashboard);
        Controls.Add(footer);
        Controls.Add(headerHost);
        dashboard.BringToFront();
        footer.BringToFront();
        headerHost.BringToFront();

        fullscreenHint.Text = "Full screen  •  Press Esc to exit";
        fullscreenHint.AutoSize = true;
        fullscreenHint.Font = new Font("Segoe UI", 11, FontStyle.Bold);
        fullscreenHint.BackColor = Color.FromArgb(220, 15, 23, 42);
        fullscreenHint.ForeColor = Color.White;
        fullscreenHint.Padding = new Padding(18, 10, 18, 10);
        fullscreenHint.Visible = false;
        Controls.Add(fullscreenHint);
        Resize += (_, _) => PositionFullscreenHint();
        hintTimer.Tick += (_, _) => { hintTimer.Stop(); fullscreenHint.Visible = false; };
        KeyDown += (_, eventArgs) =>
        {
            if (eventArgs.KeyCode == Keys.Escape && isFullscreen)
            {
                ExitFullscreen();
                eventArgs.Handled = true;
                eventArgs.SuppressKeyPress = true;
            }
        };

        sessionTimer.Tick += (_, _) =>
        {
            seconds++;
            telemetry.Text = $"Transport active • {frameCount} encoded frames received • {seconds}s • {(frameCount > 0 ? frameCount / seconds : 0)} FPS";
        };
        clipboardTimer.Tick += (_, _) => SyncLocalClipboard();
        fileTransfers.FileReceived += path => PostToUi(() => telemetry.Text = $"Received {Path.GetFileName(path)}");

        // Draw initial placeholder text
        viewport.Paint += PaintPlaceholder;
        Shown += (_, _) =>
        {
            StartDiscoveryResponder();
            TryStartAdbSession();
        };
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        if (OperatingSystem.IsWindowsVersionAtLeast(10, 0, 17763))
        {
            var enabled = 1;
            _ = DwmSetWindowAttribute(Handle, 20, ref enabled, sizeof(int));
        }
    }

    private void PositionHeaderButtons()
    {
        connect.Left = header.ClientSize.Width - connect.Width - 20;
        connect.Top = 18;
        fullscreen.Left = connect.Left - fullscreen.Width - 10;
        fullscreen.Top = 18;
    }

    private void EnterFullscreen()
    {
        if (isFullscreen) return;
        isFullscreen = true;
        previousWindowState = WindowState;
        previousBorderStyle = FormBorderStyle;
        headerHost.Visible = false;
        footer.Visible = false;
        FormBorderStyle = FormBorderStyle.None;
        WindowState = FormWindowState.Normal;
        Bounds = Screen.FromControl(this).Bounds;
        TopMost = true;
        fullscreenHint.Visible = true;
        fullscreenHint.BringToFront();
        PositionFullscreenHint();
        hintTimer.Stop();
        hintTimer.Start();
        viewport.Focus();
    }

    private void ExitFullscreen()
    {
        if (!isFullscreen) return;
        isFullscreen = false;
        hintTimer.Stop();
        fullscreenHint.Visible = false;
        TopMost = false;
        FormBorderStyle = previousBorderStyle;
        headerHost.Visible = true;
        footer.Visible = true;
        WindowState = previousWindowState;
        PositionHeaderButtons();
    }

    private void PositionFullscreenHint()
    {
        fullscreenHint.Left = Math.Max(0, (ClientSize.Width - fullscreenHint.Width) / 2);
        fullscreenHint.Top = 22;
    }

    private void PaintPlaceholder(object? sender, PaintEventArgs e)
    {
        if (!isConnected) e.Graphics.Clear(Color.FromArgb(3, 7, 18));
    }

    private void ToggleSession()
    {
        if (isConnected) {
            connectionAttemptActive = false;
            Disconnect();
        }
        else
        {
            var host = PromptForPhoneAddress();
            if (!string.IsNullOrWhiteSpace(host)) ConnectToDevice(host, "Local network");
        }
    }

    private void ConnectToDevice(string host, string transportName)
    {
        connectionAttemptActive = true;
        dashboard.SetState(DashboardState.Connecting);
        state.Text = "● Connecting...";
        state.ForeColor = Color.FromArgb(250, 204, 21);
        connect.Enabled = false;

        receiveThread = new Thread(() =>
        {
            try
            {
                // Establish the phone session before loading the comparatively heavy decoder runtime.
                var client = new TcpClient {
                    NoDelay = true,
                    ReceiveBufferSize = transportName == "USB" ? 512 * 1024 : 192 * 1024
                };
                tcpClient = client;
                client.ConnectAsync(host, 5000).Wait(TimeSpan.FromSeconds(5));
                if (!client.Connected)
                    throw new IOException("The phone did not accept the desktop connection within 5 seconds.");
                var stream = client.GetStream();
                using var reader = new BinaryReader(stream, Encoding.UTF8, leaveOpen: true);
                var challengePacket = Packet.Deserialize(reader);
                if (challengePacket?.Type != PacketType.Handshake || challengePacket.Payload.Length != 32)
                    throw new InvalidDataException("The phone did not provide a valid pairing challenge.");
                var code = PromptForPairingCode();
                if (code is null) throw new OperationCanceledException("Pairing was cancelled.");
                SendPacketTo(client, new Packet(PacketType.PairingResponse, PairingSecurity.CreateResponse(code, challengePacket.Payload)));
                var pairingResult = Packet.Deserialize(reader);
                if (pairingResult?.Type != PacketType.PairingResponse || pairingResult.Payload.Length != 1 || pairingResult.Payload[0] != 1)
                    throw new UnauthorizedAccessException("The pairing code was rejected.");

                isConnected = true;
                PostToUi(() =>
                {
                    state.Text = $"● Phone connected • {transportName}";
                    state.ForeColor = Color.FromArgb(74, 222, 128);
                    telemetry.Text = $"{transportName} transport connected • {host}";
                });

                PostToUi(() =>
                {
                    isConnected = true;
                    dashboard.SetState(DashboardState.Connected);
                    dashboard.Visible = true;
                    dashboard.BringToFront();
                    header.BringToFront();
                    footer.BringToFront();
                    state.Text = "● Phone connected";
                    state.ForeColor = Color.FromArgb(74, 222, 128);
                    connect.Text = "Disconnect";
                    connect.BackColor = Color.FromArgb(220, 38, 38);
                    connect.Enabled = true;
                    seconds = 0;
                    frameCount = 0;
                    sessionTimer.Start();
                    clipboardTimer.Start();
                    viewport.Paint -= PaintPlaceholder;
                    viewport.Invalidate();
                });

                while (isConnected)
                {
                    var packet = Packet.Deserialize(reader);
                    if (packet == null) throw new IOException("The phone closed the desktop session.");

                    if (packet.Type == PacketType.VideoFrame)
                    {
                        frameCount++;
                        if (packet.Payload.AsSpan().StartsWith("JPEG"u8)) RenderFrame(packet.Payload[4..]);
                        else if (packet.Payload.AsSpan().StartsWith("H264"u8)) EnsureH264Decoder().DecodeNalUnit(packet.Payload[4..]);
                        else if (packet.Payload.Length >= 2 && packet.Payload[0] == 0xFF && packet.Payload[1] == 0xD8) RenderFrame(packet.Payload);
                        else EnsureH264Decoder().DecodeNalUnit(packet.Payload);
                    }
                    else if (packet.Type == PacketType.Clipboard)
                    {
                        ApplyRemoteClipboard(Encoding.UTF8.GetString(packet.Payload));
                    }
                    else if (packet.Type is PacketType.FileMetadata or PacketType.FileChunk or PacketType.FileComplete)
                    {
                        fileTransfers.Receive(packet);
                    }
                }
            }
            catch (Exception ex)
            {
                if (!connectionAttemptActive) return;
                var reason = ex.GetBaseException().Message;
                PostToUi(() =>
                {
                    Disconnect();
                    state.Text = "● Connection failed";
                    state.ForeColor = Color.FromArgb(248, 113, 113);
                    dashboard.Visible = true;
                    dashboard.SetState(DashboardState.Failed, "The phone disconnected unexpectedly.");
                    telemetry.Text = "Connection lost — The phone disconnected unexpectedly. Technical details are available in the error dialog.";
                    MessageBox.Show(
                        this,
                        $"Could not connect to the phone.\n\nReason: {reason}\n\n" +
                        "Keep Desktop Mod open on the phone. For USB, verify USB tethering is enabled. For Wi-Fi, verify both devices are on the same private network.",
                        "Connection failed",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error);
                });
            }
        });
        receiveThread.IsBackground = true;
        receiveThread.Start();
    }

    private void RenderFrame(byte[] jpegData)
    {
        try
        {
            using var stream = new MemoryStream(jpegData, writable: false);
            using var decoded = Image.FromStream(stream);
            var frame = new Bitmap(decoded);
            lock (frameLock)
            {
                latestFrame?.Dispose();
                latestFrame = frame;
            }
            if (Interlocked.Exchange(ref renderPending, 1) != 0) return;
            PostToUi(RenderLatestFrame);
        }
        catch (Exception error)
        {
            PostToUi(() => telemetry.Text = $"Frame decode failed: {error.Message}");
        }
    }

    private void QueueDecodedFrame(Bitmap frame)
    {
        lock (frameLock) { latestFrame?.Dispose(); latestFrame = frame; }
        if (Interlocked.Exchange(ref renderPending, 1) == 0) PostToUi(RenderLatestFrame);
    }

    private H264Decoder EnsureH264Decoder()
    {
        if (h264Decoder is not null) return h264Decoder;
        var ffmpegPath = Path.Combine(AppContext.BaseDirectory, "ffmpeg");
        if (!Directory.Exists(ffmpegPath)) throw new DirectoryNotFoundException("The FFmpeg runtime is required for Android H.264 streams.");
        H264Decoder.SetFFmpegPath(ffmpegPath);
        var decoder = new H264Decoder();
        decoder.OnFrameDecoded += QueueDecodedFrame;
        decoder.Initialize();
        h264Decoder = decoder;
        return decoder;
    }

    private string? PromptForPairingCode()
    {
        string? result = null;
        if (IsDisposed || !IsHandleCreated) return null;
        Invoke(() =>
        {
            using var dialog = new Form { Text = "Pair with phone", Width = 360, Height = 180, StartPosition = FormStartPosition.CenterParent, FormBorderStyle = FormBorderStyle.FixedDialog, MaximizeBox = false, MinimizeBox = false };
            var label = new Label { Text = "Enter the 6-digit code shown on the phone:", AutoSize = true, Left = 18, Top = 18 };
            var input = new TextBox { Left = 18, Top = 48, Width = 305, MaxLength = 6 };
            var ok = new Button { Text = "Pair", DialogResult = DialogResult.OK, Left = 228, Top = 84, Width = 95 };
            dialog.Controls.AddRange([label, input, ok]); dialog.AcceptButton = ok;
            if (dialog.ShowDialog(this) == DialogResult.OK) result = input.Text.Trim();
        });
        return result;
    }

    private string? PromptForPhoneAddress()
    {
        string? result = null;
        using var dialog = new Form { Text = "Connect a phone", Width = 430, Height = 220, StartPosition = FormStartPosition.CenterParent, FormBorderStyle = FormBorderStyle.FixedDialog, MaximizeBox = false, MinimizeBox = false, BackColor = Color.FromArgb(17, 24, 39), ForeColor = Color.White };
        var heading = new Label { Text = "Android or iPhone address", AutoSize = true, Left = 20, Top = 20, Font = new Font("Segoe UI", 12, FontStyle.Bold) };
        var help = new Label { Text = "Enter the phone's local IP. Both devices must be on the same trusted network.", AutoSize = true, Left = 20, Top = 52, ForeColor = Color.LightSlateGray };
        var input = new TextBox { Left = 20, Top = 82, Width = 370, PlaceholderText = "192.168.1.25" };
        var cancel = new Button { Text = "Cancel", DialogResult = DialogResult.Cancel, Left = 210, Top = 122, Width = 85 };
        var ok = new Button { Text = "Continue", DialogResult = DialogResult.OK, Left = 305, Top = 122, Width = 85 };
        dialog.Controls.AddRange([heading, help, input, cancel, ok]); dialog.AcceptButton = ok; dialog.CancelButton = cancel;
        if (dialog.ShowDialog(this) == DialogResult.OK) result = input.Text.Trim();
        return result;
    }

    private static void SendPacketTo(TcpClient client, Packet packet)
    {
        var bytes = packet.Serialize(); client.GetStream().Write(bytes, 0, bytes.Length);
    }

    private void SyncLocalClipboard()
    {
        if (!isConnected || applyingRemoteClipboard || !Clipboard.ContainsText()) return;
        var text = Clipboard.GetText();
        if (text == lastClipboardText) return;
        lastClipboardText = text;
        SendPacket(new Packet(PacketType.Clipboard, Encoding.UTF8.GetBytes(text)));
    }

    private void ApplyRemoteClipboard(string text) => PostToUi(() =>
    {
        applyingRemoteClipboard = true;
        try { Clipboard.SetText(text); lastClipboardText = text; }
        finally { applyingRemoteClipboard = false; }
    });

    private void SendDroppedFiles(string[]? paths)
    {
        if (!isConnected || paths is null) return;
        Task.Run(() =>
        {
            foreach (var path in paths.Where(File.Exists))
                foreach (var packet in fileTransfers.CreatePackets(path)) SendPacket(packet);
        });
    }

    private void RenderLatestFrame()
    {
        Bitmap? frame;
        lock (frameLock)
        {
            frame = latestFrame;
            latestFrame = null;
        }
        var previous = viewport.Image;
        viewport.Image = frame;
        previous?.Dispose();
        Interlocked.Exchange(ref renderPending, 0);
        lock (frameLock)
        {
            if (latestFrame is not null && Interlocked.Exchange(ref renderPending, 1) == 0)
                PostToUi(RenderLatestFrame);
        }
    }

    private void SendPointer(Point clientPoint, int action)
    {
        if (!isConnected || viewport.ClientSize.Width <= 0 || viewport.ClientSize.Height <= 0) return;

        var scale = Math.Min(
            viewport.ClientSize.Width / (double)DesktopWidth,
            viewport.ClientSize.Height / (double)DesktopHeight);
        var renderedWidth = DesktopWidth * scale;
        var renderedHeight = DesktopHeight * scale;
        var left = (viewport.ClientSize.Width - renderedWidth) / 2.0;
        var top = (viewport.ClientSize.Height - renderedHeight) / 2.0;
        if (clientPoint.X < left || clientPoint.X >= left + renderedWidth ||
            clientPoint.Y < top || clientPoint.Y >= top + renderedHeight) return;

        var x = Math.Clamp((int)((clientPoint.X - left) / scale), 0, DesktopWidth - 1);
        var y = Math.Clamp((int)((clientPoint.Y - top) / scale), 0, DesktopHeight - 1);
        Span<byte> payload = stackalloc byte[12];
        BinaryPrimitives.WriteInt32BigEndian(payload[0..4], x);
        BinaryPrimitives.WriteInt32BigEndian(payload[4..8], y);
        BinaryPrimitives.WriteInt32BigEndian(payload[8..12], action);
        SendPacket(new Packet(PacketType.MouseEvent, payload.ToArray()));
    }

    private void SendKey(Keys key, bool isDown)
    {
        if (!isConnected) return;
        var androidKeyCode = ToAndroidKeyCode(key);
        if (androidKeyCode < 0) return;
        Span<byte> payload = stackalloc byte[5];
        BinaryPrimitives.WriteInt32BigEndian(payload[0..4], androidKeyCode);
        payload[4] = isDown ? (byte)1 : (byte)0;
        SendPacket(new Packet(PacketType.KeyEvent, payload.ToArray()));
    }

    private void SendPacket(Packet packet)
    {
        try
        {
            var bytes = packet.Serialize();
            lock (sendLock)
            {
                var client = tcpClient;
                if (client is null || !client.Connected) return;
                client.GetStream().Write(bytes, 0, bytes.Length);
            }
        }
        catch (Exception error)
        {
            PostToUi(() => telemetry.Text = $"Input send failed: {error.Message}");
        }
    }

    private static int ToAndroidKeyCode(Keys key) => key switch
    {
        Keys.Back => 67, Keys.Tab => 61, Keys.Enter => 66, Keys.Escape => 111,
        Keys.Space => 62, Keys.Left => 21, Keys.Up => 19, Keys.Right => 22, Keys.Down => 20,
        Keys.Delete => 112, Keys.Home => 3, Keys.End => 123, Keys.PageUp => 92, Keys.PageDown => 93,
        >= Keys.A and <= Keys.Z => 29 + (key - Keys.A),
        >= Keys.D0 and <= Keys.D9 => 7 + (key - Keys.D0),
        _ => -1
    };

    private void StartDiscoveryResponder()
    {
        if (discoveryRunning) return;
        discoveryRunning = true;
        discoveryThread = new Thread(() =>
        {
            try
            {
                discoverySocket = new UdpClient(DiscoveryPort) { EnableBroadcast = true };
                while (discoveryRunning)
                {
                    var endpoint = new IPEndPoint(IPAddress.Any, 0);
                    var bytes = discoverySocket.Receive(ref endpoint);
                    var message = Encoding.UTF8.GetString(bytes);
                    if (message == "DESKTOP_MOD_DISCOVER_V1")
                    {
                        var response = Encoding.UTF8.GetBytes($"DESKTOP_MOD_RECEIVER_V1|{Environment.MachineName}|Windows|5000");
                        discoverySocket.Send(response, response.Length, endpoint);
                    }
                    else if (message.StartsWith("DESKTOP_MOD_START_V1", StringComparison.Ordinal))
                    {
                        var phoneAddress = endpoint.Address.ToString();
                        var requestedTransport = message.Split('|').ElementAtOrDefault(1) == "USB" ? "USB" : "Wi-Fi";
                        PostToUi(() =>
                        {
                            if (isConnected) Disconnect();
                            ConnectToDevice(phoneAddress, requestedTransport);
                        });
                    }
                }
            }
            catch (SocketException) when (!discoveryRunning) { }
            catch (ObjectDisposedException) { }
            catch (Exception error)
            {
                if (discoveryRunning) PostToUi(() => telemetry.Text = $"Wireless discovery unavailable: {error.Message}");
            }
        }) { IsBackground = true, Name = "DesktopModDiscovery" };
        discoveryThread.Start();
    }

    private static void EnsureAdbForwarding()
    {
        var adb = FindAdb();
        if (adb is null)
            throw new IOException("ADB was not found. Install Android Platform Tools or add adb.exe to PATH.");

        RunAdb(adb, "start-server");
        var devices = RunAdb(adb, "devices");
        var connectedDevices = devices.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries)
            .Where(line => line.EndsWith("\tdevice", StringComparison.Ordinal))
            .ToArray();

        if (connectedDevices.Length == 0)
            throw new IOException("No authorized Android phone was found. Connect USB, enable USB debugging, and accept the phone's authorization prompt.");
        if (connectedDevices.Length > 1)
            throw new IOException("More than one Android device is connected. Disconnect the extra device and try again.");

        RunAdb(adb, "forward tcp:5000 tcp:5000");
    }

    private void TryStartAdbSession()
    {
        new Thread(() =>
        {
            try
            {
                EnsureAdbForwarding();
                PostToUi(() =>
                {
                    if (!isConnected && !connectionAttemptActive)
                    {
                        telemetry.Text = "Developer Mode detected • connecting over private ADB USB forwarding";
                        ConnectToDevice("127.0.0.1", "ADB USB");
                    }
                });
            }
            catch (Exception error)
            {
                PostToUi(() =>
                {
                    if (!isConnected)
                        telemetry.Text = $"ADB unavailable ({error.GetBaseException().Message}) • USB tethering and Wi-Fi remain available";
                });
            }
        }) { IsBackground = true, Name = "DesktopModAdbDetection" }.Start();
    }

    private static string? FindAdb()
    {
        var candidates = new List<string>();
        var sdkRoot = Environment.GetEnvironmentVariable("ANDROID_SDK_ROOT");
        var androidHome = Environment.GetEnvironmentVariable("ANDROID_HOME");
        if (!string.IsNullOrWhiteSpace(sdkRoot))
            candidates.Add(Path.Combine(sdkRoot, "platform-tools", "adb.exe"));
        if (!string.IsNullOrWhiteSpace(androidHome))
            candidates.Add(Path.Combine(androidHome, "platform-tools", "adb.exe"));
        candidates.Add(Path.Combine(AppContext.BaseDirectory, "platform-tools", "adb.exe"));
        candidates.Add(@"C:\GitLab\AndroidSdk\platform-tools\adb.exe");

        var localAdb = candidates.FirstOrDefault(File.Exists);
        if (localAdb is not null) return localAdb;

        try
        {
            var probe = new ProcessStartInfo("where.exe", "adb.exe")
            {
                UseShellExecute = false,
                RedirectStandardOutput = true,
                CreateNoWindow = true
            };
            using var process = Process.Start(probe);
            var path = process?.StandardOutput.ReadLine();
            process?.WaitForExit(3000);
            return !string.IsNullOrWhiteSpace(path) && File.Exists(path) ? path : null;
        }
        catch
        {
            return null;
        }
    }

    private static string RunAdb(string adb, string arguments)
    {
        var startInfo = new ProcessStartInfo(adb, arguments)
        {
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true
        };
        using var process = Process.Start(startInfo) ?? throw new IOException("ADB could not be started.");
        var output = process.StandardOutput.ReadToEnd();
        var error = process.StandardError.ReadToEnd();
        if (!process.WaitForExit(10000))
        {
            process.Kill(true);
            throw new IOException("ADB did not respond within 10 seconds.");
        }
        if (process.ExitCode != 0)
            throw new IOException(string.IsNullOrWhiteSpace(error) ? "ADB command failed." : error.Trim());
        return output;
    }

    private void Disconnect()
    {
        connectionAttemptActive = false;
        isConnected = false;
        sessionTimer.Stop();
        clipboardTimer.Stop();
        lock (sendLock)
        {
            var client = tcpClient;
            tcpClient = null;
            client?.Close();
        }
        state.Text = "● Waiting for phone";
        state.ForeColor = Color.FromArgb(148, 163, 184);
        connect.Text = "Connect phone";
        connect.BackColor = Color.FromArgb(37, 99, 235);
        connect.Enabled = true;
        telemetry.Text = "Waiting for Desktop Mod on USB tethering or Wi-Fi";
        dashboard.Visible = true;
        dashboard.SetState(DashboardState.Idle);

        viewport.Image = null;
        lock (frameLock)
        {
            latestFrame?.Dispose();
            latestFrame = null;
        }
        Interlocked.Exchange(ref renderPending, 0);
        h264Decoder?.Dispose();
        h264Decoder = null;
        viewport.Paint += PaintPlaceholder;
        viewport.Invalidate();
    }

    private void PostToUi(Action action)
    {
        if (IsDisposed || Disposing || !IsHandleCreated) return;
        try { BeginInvoke(action); }
        catch (InvalidOperationException) when (IsDisposed || Disposing || !IsHandleCreated) { }
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        discoveryRunning = false;
        discoverySocket?.Close();
        discoverySocket = null;
        Disconnect();
        fileTransfers.Dispose();
        base.OnFormClosed(e);
    }
}
