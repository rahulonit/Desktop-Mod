using System.Drawing;
using System.Diagnostics;
using System.Net.Sockets;
using UniversalMobileDesktop.Protocol;

namespace UniversalMobileDesktop.Receiver;

public sealed class ReceiverForm : Form
{
    private readonly Label state = new();
    private readonly Label telemetry = new();
    private readonly Button connect = new();
    private readonly PictureBox viewport = new();
    private readonly System.Windows.Forms.Timer sessionTimer = new() { Interval = 1000 };
    private int seconds;
    private bool isConnected;
    private volatile bool connectionAttemptActive;
    private TcpClient? tcpClient;
    private Thread? receiveThread;
    private int frameCount;

    public ReceiverForm()
    {
        Text = "Desktop Mod";
        MinimumSize = new Size(1024, 720);
        Size = new Size(1280, 800);
        BackColor = Color.FromArgb(15, 23, 42);
        ForeColor = Color.White;
        StartPosition = FormStartPosition.CenterScreen;

        // Header
        var header = new Panel { Dock = DockStyle.Top, Height = 72, BackColor = Color.FromArgb(17, 24, 39), Padding = new Padding(20, 12, 20, 12) };
        var title = new Label { Text = "◆  Desktop Mod", AutoSize = true, Font = new Font("Segoe UI", 17, FontStyle.Bold), Location = new Point(20, 18) };
        state.Text = "● Waiting for phone";
        state.AutoSize = true;
        state.ForeColor = Color.FromArgb(148, 163, 184);
        state.Location = new Point(340, 25);
        connect.Text = "Connect";
        connect.AutoSize = true;
        connect.FlatStyle = FlatStyle.Flat;
        connect.BackColor = Color.FromArgb(37, 99, 235);
        connect.ForeColor = Color.White;
        connect.FlatAppearance.BorderSize = 0;
        connect.Padding = new Padding(12, 4, 12, 4);
        connect.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        connect.Location = new Point(Width - 190, 18);
        connect.Click += (_, _) => ToggleSession();
        header.Resize += (_, _) => connect.Left = header.ClientSize.Width - connect.Width - 20;
        header.Controls.AddRange([title, state, connect]);

        // Viewport (PictureBox for rendering decoded frames)
        viewport.Dock = DockStyle.Fill;
        viewport.BackColor = Color.Black;
        viewport.SizeMode = PictureBoxSizeMode.Zoom;

        // Footer
        var footer = new Panel { Dock = DockStyle.Bottom, Height = 48, BackColor = Color.FromArgb(17, 24, 39), Padding = new Padding(18, 14, 18, 8) };
        telemetry.Text = "No active session • Run: adb forward tcp:5000 tcp:5000";
        telemetry.AutoSize = true;
        telemetry.ForeColor = Color.FromArgb(148, 163, 184);
        footer.Controls.Add(telemetry);

        Controls.Add(viewport);
        Controls.Add(footer);
        Controls.Add(header);

        sessionTimer.Tick += (_, _) =>
        {
            seconds++;
            telemetry.Text = $"Transport active • {frameCount} encoded frames received • {seconds}s • {(frameCount > 0 ? frameCount / seconds : 0)} FPS";
        };

        // Draw initial placeholder text
        viewport.Paint += PaintPlaceholder;
        Shown += (_, _) => ConnectToDevice();
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
            e.Graphics.DrawString("Connect your phone", titleFont, Brushes.White, new RectangleF(0, bounds.Height / 2f - 75, bounds.Width, 50), center);
            e.Graphics.DrawString(
                "Step 1: Connect phone via USB and enable USB debugging\n" +
                "Step 2: Run in terminal: adb forward tcp:5000 tcp:5000\n" +
                "Step 3: Launch the Desktop Mod app on your phone\n" +
                "Step 4: Click \"Connect\" above",
                bodyFont, Brushes.LightSlateGray, new RectangleF(60, bounds.Height / 2f - 10, bounds.Width - 120, 120), center);
        }
    }

    private void ToggleSession()
    {
        if (isConnected) {
            connectionAttemptActive = false;
            Disconnect();
        }
        else ConnectToDevice();
    }

    private void ConnectToDevice()
    {
        connectionAttemptActive = true;
        state.Text = "● Connecting...";
        state.ForeColor = Color.FromArgb(250, 204, 21);
        connect.Enabled = false;

        receiveThread = new Thread(() =>
        {
            try
            {
                EnsureAdbForwarding();

                // Establish the phone session before loading the comparatively heavy decoder runtime.
                tcpClient = new TcpClient();
                tcpClient.ConnectAsync("127.0.0.1", 5000).Wait(TimeSpan.FromSeconds(5));
                if (!tcpClient.Connected)
                    throw new IOException("The phone did not accept the desktop connection within 5 seconds.");
                isConnected = true;
                BeginInvoke(() =>
                {
                    state.Text = "● Phone connected • transport mode";
                    state.ForeColor = Color.FromArgb(74, 222, 128);
                    telemetry.Text = "USB transport connected";
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
                        "Check that the mobile app is open, the USB cable is connected, and ADB forwarding is active, then try again.",
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
            BeginInvoke(() =>
            {
                var previous = viewport.Image;
                viewport.Image = frame;
                previous?.Dispose();
            });
        }
        catch (Exception error)
        {
            BeginInvoke(() => telemetry.Text = $"Frame decode failed: {error.Message}");
        }
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
        connect.Text = "Connect";
        connect.BackColor = Color.FromArgb(37, 99, 235);
        connect.Enabled = true;
        telemetry.Text = "No active session • Run: adb forward tcp:5000 tcp:5000";

        viewport.Image = null;
        viewport.Paint += PaintPlaceholder;
        viewport.Invalidate();
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        Disconnect();
        base.OnFormClosed(e);
    }
}
