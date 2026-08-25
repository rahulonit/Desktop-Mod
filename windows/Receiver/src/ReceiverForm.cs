using System.Drawing;
using System.Diagnostics;
using System.Buffers.Binary;
using System.Net.Sockets;
using System.Net;
using System.Text;
using UniversalMobileDesktop.Protocol;

namespace UniversalMobileDesktop.Receiver;

public sealed class ReceiverForm : Form
{
    private readonly Label state = new();
    private readonly Label telemetry = new();
    private readonly Button connect = new();
    private readonly Button fullscreen = new();
    private readonly PictureBox viewport = new();
    private readonly Panel header = new();
    private readonly Panel footer = new();
    private readonly Label fullscreenHint = new();
    private readonly System.Windows.Forms.Timer hintTimer = new() { Interval = 3500 };
    private readonly System.Windows.Forms.Timer sessionTimer = new() { Interval = 1000 };
    private int seconds;
    private bool isConnected;
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

    public ReceiverForm()
    {
        Text = "Desktop Mod";
        MinimumSize = new Size(1024, 720);
        Size = new Size(1280, 800);
        BackColor = Color.FromArgb(15, 23, 42);
        ForeColor = Color.White;
        StartPosition = FormStartPosition.CenterScreen;
        WindowState = FormWindowState.Maximized;
        KeyPreview = true;

        // Header
        header.Dock = DockStyle.Top;
        header.Height = 72;
        header.BackColor = Color.FromArgb(17, 24, 39);
        header.Padding = new Padding(20, 12, 20, 12);
        var title = new Label { Text = "◆  Desktop Mod", AutoSize = true, Font = new Font("Segoe UI", 17, FontStyle.Bold), Location = new Point(20, 18) };
        state.Text = "● Waiting for phone";
        state.AutoSize = true;
        state.ForeColor = Color.FromArgb(148, 163, 184);
        state.Location = new Point(340, 25);
        connect.Text = "Connection help";
        connect.AutoSize = true;
        connect.FlatStyle = FlatStyle.Flat;
        connect.BackColor = Color.FromArgb(37, 99, 235);
        connect.ForeColor = Color.White;
        connect.FlatAppearance.BorderSize = 0;
        connect.Padding = new Padding(12, 4, 12, 4);
        connect.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        connect.Location = new Point(Width - 190, 18);
        connect.Click += (_, _) => ToggleSession();
        fullscreen.Text = "Full screen";
        fullscreen.AutoSize = true;
        fullscreen.FlatStyle = FlatStyle.Flat;
        fullscreen.BackColor = Color.FromArgb(51, 65, 85);
        fullscreen.ForeColor = Color.White;
        fullscreen.FlatAppearance.BorderSize = 0;
        fullscreen.Padding = new Padding(12, 4, 12, 4);
        fullscreen.Click += (_, _) => EnterFullscreen();
        header.Resize += (_, _) => PositionHeaderButtons();
        header.Controls.AddRange([title, state, fullscreen, connect]);

        // Viewport (PictureBox for rendering decoded frames)
        viewport.Dock = DockStyle.Fill;
        viewport.BackColor = Color.Black;
        viewport.SizeMode = PictureBoxSizeMode.Zoom;
        viewport.TabStop = true;
        viewport.MouseDown += (_, eventArgs) => SendPointer(eventArgs.Location, eventArgs.Button == MouseButtons.Right ? 5 : 1);
        viewport.MouseUp += (_, eventArgs) => SendPointer(eventArgs.Location, eventArgs.Button == MouseButtons.Right ? 6 : 2);
        viewport.MouseMove += (_, eventArgs) => SendPointer(eventArgs.Location, 0);
        viewport.MouseWheel += (_, eventArgs) => SendPointer(eventArgs.Location, eventArgs.Delta > 0 ? 3 : 4);
        viewport.MouseEnter += (_, _) => viewport.Focus();
        viewport.KeyDown += (_, eventArgs) => SendKey(eventArgs.KeyCode, true);
        viewport.KeyUp += (_, eventArgs) => SendKey(eventArgs.KeyCode, false);

        // Footer
        footer.Dock = DockStyle.Bottom;
        footer.Height = 48;
        footer.BackColor = Color.FromArgb(17, 24, 39);
        footer.Padding = new Padding(18, 14, 18, 8);
        telemetry.Text = "Waiting for Desktop Mod on USB tethering or Wi-Fi";
        telemetry.AutoSize = true;
        telemetry.ForeColor = Color.FromArgb(148, 163, 184);
        footer.Controls.Add(telemetry);

        Controls.Add(viewport);
        Controls.Add(footer);
        Controls.Add(header);

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

        // Draw initial placeholder text
        viewport.Paint += PaintPlaceholder;
        Shown += (_, _) =>
        {
            StartDiscoveryResponder();
        };
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
        header.Visible = false;
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
        header.Visible = true;
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
        if (!isConnected)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            using var titleFont = new Font("Segoe UI", 25, FontStyle.Bold);
            using var bodyFont = new Font("Segoe UI", 11);
            var center = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            var bounds = viewport.ClientRectangle;
            e.Graphics.DrawString("Connect Desktop Mod", titleFont, Brushes.White, new RectangleF(0, bounds.Height / 2f - 95, bounds.Width, 50), center);
            e.Graphics.DrawString(
                "USB without Developer Mode\n" +
                "1   Connect a USB data cable and enable USB tethering on the phone\n" +
                "2   Open Desktop Mod on both devices\n" +
                "3   Select this PC on the phone and tap Connect\n\n" +
                "Wi-Fi\n" +
                "Join the same private network, select this PC on the phone, and tap Connect.\n" +
                "Your phone stays usable. This is a separate desktop, not screen mirroring.",
                bodyFont, Brushes.LightSlateGray, new RectangleF(60, bounds.Height / 2f - 25, bounds.Width - 120, 170), center);
        }
    }

    private void ToggleSession()
    {
        if (isConnected) {
            connectionAttemptActive = false;
            Disconnect();
        }
        else MessageBox.Show(
            this,
            "Start the connection from the Desktop Mod app on your phone.\n\n" +
            "For USB: connect a data cable, enable USB tethering, then select this PC.\n" +
            "For Wi-Fi: connect both devices to the same private network, then select this PC.",
            "Connect Desktop Mod",
            MessageBoxButtons.OK,
            MessageBoxIcon.Information);
    }

    private void ConnectToDevice(string host, string transportName)
    {
        connectionAttemptActive = true;
        state.Text = "● Connecting...";
        state.ForeColor = Color.FromArgb(250, 204, 21);
        connect.Enabled = false;

        receiveThread = new Thread(() =>
        {
            try
            {
                // Establish the phone session before loading the comparatively heavy decoder runtime.
                tcpClient = new TcpClient();
                tcpClient.NoDelay = true;
                tcpClient.ReceiveBufferSize = transportName == "USB" ? 512 * 1024 : 192 * 1024;
                tcpClient.ConnectAsync(host, 5000).Wait(TimeSpan.FromSeconds(5));
                if (!tcpClient.Connected)
                    throw new IOException("The phone did not accept the desktop connection within 5 seconds.");
                isConnected = true;
                BeginInvoke(() =>
                {
                    state.Text = $"● Phone connected • {transportName}";
                    state.ForeColor = Color.FromArgb(74, 222, 128);
                    telemetry.Text = $"{transportName} transport connected • {host}";
                });

                BeginInvoke(() =>
                {
                    isConnected = true;
                    state.Text = "● Phone connected";
                    state.ForeColor = Color.FromArgb(74, 222, 128);
                    connect.Text = "Disconnect";
                    connect.BackColor = Color.FromArgb(220, 38, 38);
                    connect.Enabled = true;
                    seconds = 0;
                    frameCount = 0;
                    sessionTimer.Start();
                    viewport.Paint -= PaintPlaceholder;
                    viewport.Invalidate();
                });

                var stream = tcpClient.GetStream();
                using var reader = new BinaryReader(stream);

                while (isConnected)
                {
                    var packet = Packet.Deserialize(reader);
                    if (packet == null) throw new IOException("The phone closed the desktop session.");

                    if (packet.Type == PacketType.VideoFrame)
                    {
                        frameCount++;
                        RenderFrame(packet.Payload);
                    }
                }
            }
            catch (Exception ex)
            {
                if (!connectionAttemptActive) return;
                var reason = ex.GetBaseException().Message;
                BeginInvoke(() =>
                {
                    Disconnect();
                    state.Text = "● Connection failed";
                    state.ForeColor = Color.FromArgb(248, 113, 113);
                    telemetry.Text = reason;
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
            BeginInvoke(() => RenderLatestFrame());
        }
        catch (Exception error)
        {
            BeginInvoke(() => telemetry.Text = $"Frame decode failed: {error.Message}");
        }
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
                BeginInvoke(() => RenderLatestFrame());
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
            lock (sendLock) tcpClient?.GetStream().Write(bytes, 0, bytes.Length);
        }
        catch (Exception error)
        {
            BeginInvoke(() => telemetry.Text = $"Input send failed: {error.Message}");
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
                        BeginInvoke(() =>
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
                if (discoveryRunning) BeginInvoke(() => telemetry.Text = $"Wireless discovery unavailable: {error.Message}");
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
        tcpClient?.Close();
        tcpClient = null;
        state.Text = "● Waiting for phone";
        state.ForeColor = Color.FromArgb(148, 163, 184);
        connect.Text = "Connection help";
        connect.BackColor = Color.FromArgb(37, 99, 235);
        connect.Enabled = true;
        telemetry.Text = "Waiting for Desktop Mod on USB tethering or Wi-Fi";

        viewport.Image = null;
        lock (frameLock)
        {
            latestFrame?.Dispose();
            latestFrame = null;
        }
        Interlocked.Exchange(ref renderPending, 0);
        viewport.Paint += PaintPlaceholder;
        viewport.Invalidate();
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        discoveryRunning = false;
        discoverySocket?.Close();
        discoverySocket = null;
        Disconnect();
        base.OnFormClosed(e);
    }
}
