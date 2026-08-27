using System.Drawing.Drawing2D;

namespace UniversalMobileDesktop.Receiver;

internal enum DashboardState { Idle, Searching, Connecting, Connected, Failed }
internal enum AccentIcon { App, Devices, Usb, Wifi, Monitor, Link, Info, Fullscreen, Phone, Shield }

internal sealed class DesktopModDashboard : UserControl
{
    private readonly GlassPanel hero = new() { Radius = 28, GlassOpacity = 150 };
    private readonly TableLayoutPanel cards = new() { BackColor = Color.Transparent };
    private readonly Panel actionBar = new() { Dock = DockStyle.Bottom, Height = 0, BackColor = Color.Transparent };
    private readonly RoundedButton start = new() { Text = "Start Desktop", Primary = true, Size = new Size(190, 48) };
    private readonly ConnectionCard usb;
    private readonly ConnectionCard wifi;
    public event EventHandler? ConnectRequested;
    public event EventHandler? StartDesktopRequested;

    public DesktopModDashboard()
    {
        DoubleBuffered = true; BackColor = Color.FromArgb(2, 7, 22); AutoScroll = true; Padding = new Padding(24);
        usb = new ConnectionCard("1. USB without Developer Mode", AccentIcon.Usb, Color.FromArgb(36, 145, 255),
            "Connect via USB data cable and enable USB tethering on your phone.",
            [(AccentIcon.Usb, "Connect a USB data cable and enable USB tethering on the phone"), (AccentIcon.Devices, "Open Desktop Mod on both devices"), (AccentIcon.Monitor, "Select this PC on the phone and tap Connect")]);
        wifi = new ConnectionCard("2. Wi-Fi", AccentIcon.Wifi, Color.FromArgb(105, 82, 245),
            "Join the same private network to connect wirelessly.",
            [(AccentIcon.Wifi, "Join the same private network"), (AccentIcon.Monitor, "Select this PC on the phone"), (AccentIcon.Link, "Tap Connect to start")]);
        usb.ActionRequested += (_, _) => ConnectRequested?.Invoke(this, EventArgs.Empty); wifi.ActionRequested += (_, _) => ConnectRequested?.Invoke(this, EventArgs.Empty);
        start.Click += (_, _) => StartDesktopRequested?.Invoke(this, EventArgs.Empty);

        var logo = new IconBadge { Icon = AccentIcon.Devices, Accent = Color.FromArgb(53, 138, 255), Size = new Size(82, 82), Anchor = AnchorStyles.None, Circular = true };
        var title = TextLabel("Connect Desktop Mode", 31, Color.White, FontStyle.Bold, ContentAlignment.MiddleCenter);
        var subtitle = TextLabel("Choose a connection method to use your phone as a separate desktop.", 12.5f, Color.FromArgb(186, 204, 235), FontStyle.Regular, ContentAlignment.MiddleCenter);
        title.Dock = subtitle.Dock = DockStyle.Fill;
        var heading = new TableLayoutPanel { Dock = DockStyle.Top, Height = 210, ColumnCount = 1, RowCount = 3, BackColor = Color.Transparent, Padding = new Padding(0, 8, 0, 0) };
        heading.RowStyles.Add(new RowStyle(SizeType.Absolute, 100)); heading.RowStyles.Add(new RowStyle(SizeType.Absolute, 62)); heading.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
        heading.Controls.Add(logo, 0, 0); heading.Controls.Add(title, 0, 1); heading.Controls.Add(subtitle, 0, 2);

        cards.Dock = DockStyle.Fill; cards.Padding = new Padding(0, 8, 0, 18);
        var banner = new GlassPanel { Dock = DockStyle.Bottom, Height = 58, Radius = 29, GlassOpacity = 116, Padding = new Padding(22, 8, 22, 8) };
        var bannerContent = new Panel { Dock = DockStyle.Fill, BackColor = Color.Transparent };
        var info = new IconBadge { Icon = AccentIcon.Info, Accent = Color.FromArgb(70, 145, 255), Circular = true, Size = new Size(30, 30), Location = new Point(8, 6) };
        var infoText = TextLabel("Your phone stays usable. This is a separate desktop, not screen mirroring.", 10.5f, Color.FromArgb(169, 202, 255), FontStyle.Regular, ContentAlignment.MiddleLeft);
        infoText.Dock = DockStyle.Fill; infoText.Padding = new Padding(44, 0, 0, 0); bannerContent.Controls.Add(infoText); bannerContent.Controls.Add(info); banner.Controls.Add(bannerContent);
        actionBar.Controls.Add(start); actionBar.Resize += (_, _) => start.Location = new Point((actionBar.Width - start.Width) / 2, 6);
        hero.Padding = new Padding(34, 16, 34, 26); hero.Controls.Add(cards); hero.Controls.Add(actionBar); hero.Controls.Add(banner); hero.Controls.Add(heading); Controls.Add(hero);
        Resize += (_, _) => ApplyResponsiveLayout(); SetState(DashboardState.Idle); ApplyResponsiveLayout();
    }

    public void SetState(DashboardState value, string? detail = null)
    {
        actionBar.Height = value == DashboardState.Connected ? 62 : 0;
        usb.SetStatus(value == DashboardState.Connected ? "USB connected" : "USB or Developer Mode", value == DashboardState.Connected ? Color.FromArgb(52, 211, 153) : Color.FromArgb(74, 153, 255));
        wifi.SetStatus(value == DashboardState.Searching ? "Searching…" : "Private network", Color.FromArgb(139, 120, 255));
    }

    private void ApplyResponsiveLayout()
    {
        var available = Math.Max(1, ClientSize.Width - Padding.Horizontal); var stacked = available < 820;
        hero.Width = Math.Min(stacked ? available : 945, available); hero.Height = stacked ? 930 : 665;
        hero.Left = Math.Max(Padding.Left, (ClientSize.Width - hero.Width) / 2); hero.Top = Math.Max(Padding.Top + 6, (ClientSize.Height - hero.Height) / 2 - 8);
        cards.SuspendLayout(); cards.Controls.Clear(); cards.ColumnStyles.Clear(); cards.RowStyles.Clear();
        if (stacked) { cards.ColumnCount = 1; cards.RowCount = 2; cards.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100)); cards.RowStyles.Add(new RowStyle(SizeType.Absolute, 315)); cards.RowStyles.Add(new RowStyle(SizeType.Absolute, 315)); cards.Controls.Add(usb, 0, 0); cards.Controls.Add(wifi, 0, 1); }
        else { cards.ColumnCount = 2; cards.RowCount = 1; cards.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50)); cards.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50)); cards.RowStyles.Add(new RowStyle(SizeType.Percent, 100)); cards.Controls.Add(usb, 0, 0); cards.Controls.Add(wifi, 1, 0); }
        cards.ResumeLayout(); Invalidate();
    }

    protected override void OnPaintBackground(PaintEventArgs e)
    {
        using var background = new LinearGradientBrush(ClientRectangle, Color.FromArgb(3, 13, 38), Color.FromArgb(1, 5, 19), 120f); e.Graphics.FillRectangle(background, ClientRectangle);
        PaintGlow(e.Graphics, new Point(Width / 3, 40), 620, Color.FromArgb(36, 40, 122, 235));
        PaintGlow(e.Graphics, new Point(Width * 3 / 4, 50), 560, Color.FromArgb(34, 109, 70, 220));
        var shadow = new Rectangle(hero.Left - 18, hero.Top + 12, hero.Width + 36, hero.Height + 38);
        using var shadowPath = Drawing.Round(shadow, 42); using var shadowBrush = new SolidBrush(Color.FromArgb(52, 0, 0, 0)); e.Graphics.FillPath(shadowBrush, shadowPath);
    }
    private static void PaintGlow(Graphics g, Point center, int diameter, Color color) { for (var i = 9; i >= 1; i--) { var size = diameter * i / 9; using var brush = new SolidBrush(Color.FromArgb(Math.Max(1, color.A / 14), color)); g.FillEllipse(brush, center.X - size / 2, center.Y - size / 2, size, size); } }
    private static Label TextLabel(string text, float size, Color color, FontStyle style, ContentAlignment alignment) => new() { Text = text, ForeColor = color, BackColor = Color.Transparent, Font = new Font("Segoe UI Variable Text", size, style), TextAlign = alignment, AutoEllipsis = true };
}

internal sealed class ConnectionCard : GlassPanel
{
    private readonly Label state;
    public event EventHandler? ActionRequested;
    public ConnectionCard(string heading, AccentIcon iconType, Color accent, string description, (AccentIcon icon, string text)[] steps)
    {
        Dock = DockStyle.Fill; Margin = new Padding(10); Padding = new Padding(24); Radius = 20; GlassOpacity = 115; Cursor = Cursors.Hand;
        var top = new Panel { Dock = DockStyle.Top, Height = 86, BackColor = Color.Transparent };
        var icon = new IconBadge { Icon = iconType, Accent = accent, Size = new Size(62, 62), Location = new Point(0, 2) };
        var title = new Label { Text = heading, Location = new Point(80, 2), Size = new Size(330, 30), Font = new Font("Segoe UI Variable Text", 14.5f, FontStyle.Bold), ForeColor = Color.White, BackColor = Color.Transparent, AutoEllipsis = true };
        var body = new Label { Text = description, Location = new Point(80, 35), Size = new Size(325, 47), Font = new Font("Segoe UI Variable Text", 10.5f), ForeColor = Color.FromArgb(195, 214, 244), BackColor = Color.Transparent };
        top.Controls.Add(icon); top.Controls.Add(title); top.Controls.Add(body);
        var divider = new Panel { Dock = DockStyle.Top, Height = 1, BackColor = Color.FromArgb(48, 150, 178, 235) };
        var stepList = new StepList { Dock = DockStyle.Fill, Accent = accent, Steps = steps, Padding = new Padding(0, 18, 0, 0) };
        state = new Label { Dock = DockStyle.Bottom, Height = 24, Font = new Font("Segoe UI Variable Text", 9, FontStyle.Bold), ForeColor = accent, BackColor = Color.Transparent };
        Controls.Add(stepList); Controls.Add(divider); Controls.Add(top); Controls.Add(state);
        MouseEnter += HoverOn; MouseLeave += HoverOff; Click += Forward;
        foreach (Control control in Controls) { control.Click += Forward; foreach (Control child in control.Controls) child.Click += Forward; }
    }
    public void SetStatus(string text, Color color) { state.Text = "●  " + text; state.ForeColor = color; }
    private void Forward(object? sender, EventArgs e) => ActionRequested?.Invoke(this, EventArgs.Empty);
    private void HoverOn(object? sender, EventArgs e) { Hovered = true; Invalidate(); }
    private void HoverOff(object? sender, EventArgs e) { Hovered = false; Invalidate(); }
}

internal sealed class StepList : Control
{
    public (AccentIcon icon, string text)[] Steps { get; set; } = [];
    public Color Accent { get; set; } = Color.RoyalBlue;
    public StepList() { SetStyle(ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true); BackColor = Color.Transparent; }
    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var y = Padding.Top;
        using var font = new Font("Segoe UI Variable Text", 10.5f); using var badgeFont = new Font("Segoe UI", 9, FontStyle.Bold); using var textBrush = new SolidBrush(Color.FromArgb(225, 234, 249));
        for (var i = 0; i < Steps.Length; i++, y += 53) { using var badge = new SolidBrush(Color.FromArgb(205, Accent)); e.Graphics.FillEllipse(badge, 0, y, 34, 34); TextRenderer.DrawText(e.Graphics, (i + 1).ToString(), badgeFont, new Rectangle(0, y, 34, 34), Color.White, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter); TextRenderer.DrawText(e.Graphics, Steps[i].text, font, new Rectangle(49, y - 2, Math.Max(1, Width - 49), 45), textBrush.Color, TextFormatFlags.WordBreak | TextFormatFlags.VerticalCenter); }
    }
}

internal sealed class IconBadge : Control
{
    public AccentIcon Icon { get; set; }
    public Color Accent { get; set; } = Color.RoyalBlue;
    public bool Circular { get; set; }
    public IconBadge() { SetStyle(ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true); BackColor = Color.Transparent; }
    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var rect = new Rectangle(1, 1, Width - 3, Height - 3); using var path = Drawing.Round(rect, Circular ? rect.Width / 2 : 13);
        using var glow = new SolidBrush(Color.FromArgb(42, Accent)); e.Graphics.FillEllipse(glow, -6, -6, Width + 12, Height + 12);
        using var fill = new LinearGradientBrush(rect, Color.FromArgb(235, Accent), Color.FromArgb(160, 52, 57, 210), 45f); e.Graphics.FillPath(fill, path); using var border = new Pen(Color.FromArgb(120, 170, 215, 255)); e.Graphics.DrawPath(border, path);
        using var pen = new Pen(Color.White, Math.Max(2f, Width / 24f)) { StartCap = LineCap.Round, EndCap = LineCap.Round }; Drawing.DrawIcon(e.Graphics, pen, Icon, rect);
    }
}

internal class GlassPanel : Panel
{
    public int Radius { get; set; } = 18;
    public int GlassOpacity { get; set; } = 125;
    public bool Hovered { get; set; }
    public GlassPanel() { DoubleBuffered = true; BackColor = Color.Transparent; }
    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var rect = new Rectangle(0, 0, Width - 1, Height - 1); using var path = Drawing.Round(rect, Radius);
        using var baseFill = new SolidBrush(Color.FromArgb(GlassOpacity + (Hovered ? 18 : 0), 19, 42, 88)); e.Graphics.FillPath(baseFill, path);
        using var sheen = new LinearGradientBrush(rect, Color.FromArgb(Hovered ? 62 : 42, 86, 159, 255), Color.FromArgb(8, 95, 55, 190), 25f); e.Graphics.FillPath(sheen, path);
        using var tint = new SolidBrush(Color.FromArgb(72, 8, 18, 53)); e.Graphics.FillPath(tint, path);
        using var border = new Pen(Color.FromArgb(Hovered ? 145 : 85, 115, 166, 255), Hovered ? 1.5f : 1f); e.Graphics.DrawPath(border, path);
        using var topHighlight = new Pen(Color.FromArgb(50, 255, 255, 255)); e.Graphics.DrawLine(topHighlight, Radius, 1, Math.Max(Radius, Width - Radius), 1); base.OnPaint(e);
    }
}

internal sealed class RoundedButton : Button
{
    public bool Primary { get; set; }
    private bool hover, pressed;
    public AccentIcon Icon { get; set; }
    public RoundedButton() { FlatStyle = FlatStyle.Flat; FlatAppearance.BorderSize = 0; ForeColor = Color.White; Font = new Font("Segoe UI Variable Text", 10.5f, FontStyle.Bold); Cursor = Cursors.Hand; TabStop = true; MinimumSize = new Size(44, 44); SetStyle(ControlStyles.UserPaint, true); }
    protected override void OnMouseEnter(EventArgs e) { hover = true; Invalidate(); base.OnMouseEnter(e); }
    protected override void OnMouseLeave(EventArgs e) { hover = false; pressed = false; Invalidate(); base.OnMouseLeave(e); }
    protected override void OnMouseDown(MouseEventArgs mevent) { pressed = true; Invalidate(); base.OnMouseDown(mevent); }
    protected override void OnMouseUp(MouseEventArgs mevent) { pressed = false; Invalidate(); base.OnMouseUp(mevent); }
    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var rect = new Rectangle(1, 1, Width - 3, Height - 3); if (pressed) rect.Inflate(-1, -1); using var path = Drawing.Round(rect, 13);
        var start = Primary ? Color.FromArgb(hover ? 76 : 57, 126, 255) : Color.FromArgb(hover ? 55 : 39, 52, 92); var end = Primary ? Color.FromArgb(35, 111, 237) : Color.FromArgb(31, 38, 74);
        using var fill = new LinearGradientBrush(rect, start, end, 30f); e.Graphics.FillPath(fill, path); using var border = new Pen(Color.FromArgb(Primary ? 135 : 75, 126, 170, 255)); e.Graphics.DrawPath(border, path);
        var textRect = rect; if (Icon != default) { using var pen = new Pen(Color.White, 2); var iconRect = new Rectangle(rect.Left + 18, rect.Top + 14, 18, 18); Drawing.DrawIcon(e.Graphics, pen, Icon, iconRect); textRect.X += 25; textRect.Width -= 25; }
        TextRenderer.DrawText(e.Graphics, Text, Font, textRect, Enabled ? ForeColor : Color.Gray, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter); if (Focused) { using var focus = new Pen(Color.FromArgb(96, 165, 250), 2); focus.DashStyle = DashStyle.Dot; var f = rect; f.Inflate(-3, -3); e.Graphics.DrawPath(focus, Drawing.Round(f, 10)); }
    }
}

internal sealed class StatusPill : Control
{
    public StatusPill() { SetStyle(ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true); BackColor = Color.Transparent; Font = new Font("Segoe UI Variable Text", 9.5f, FontStyle.Bold); ForeColor = Color.FromArgb(148, 163, 184); Size = new Size(180, 40); }
    protected override void OnPaint(PaintEventArgs e) { e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var rect = new Rectangle(0, 0, Width - 1, Height - 1); using var path = Drawing.Round(rect, Height / 2); using var fill = new SolidBrush(Color.FromArgb(125, 8, 19, 45)); e.Graphics.FillPath(fill, path); using var border = new Pen(Color.FromArgb(35, 130, 170, 230)); e.Graphics.DrawPath(border, path); using var dot = new SolidBrush(ForeColor); e.Graphics.FillEllipse(dot, 16, (Height - 10) / 2, 10, 10); TextRenderer.DrawText(e.Graphics, Text.Replace("●", "").Trim(), Font, new Rectangle(34, 0, Width - 40, Height), Color.White, TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis); }
}

internal sealed class AppLogoBadge : Control
{
    private readonly Image? logo;
    public AppLogoBadge() { SetStyle(ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true); BackColor = Color.Transparent; var path = Path.Combine(AppContext.BaseDirectory, "assets", "App_icon.png"); try { if (File.Exists(path)) logo = Image.FromFile(path); } catch { } }
    protected override void OnPaint(PaintEventArgs e) { e.Graphics.SmoothingMode = SmoothingMode.AntiAlias; var rect = new Rectangle(0, 0, Width - 1, Height - 1); using var clip = Drawing.Round(rect, Math.Max(9, Width / 4)); var previous = e.Graphics.Clip; e.Graphics.SetClip(clip); if (logo is not null) e.Graphics.DrawImage(logo, rect, new Rectangle(105, 105, Math.Max(1, logo.Width - 210), Math.Max(1, logo.Height - 210)), GraphicsUnit.Pixel); else { using var fill = new LinearGradientBrush(rect, Color.DeepSkyBlue, Color.BlueViolet, 45f); e.Graphics.FillPath(fill, clip); } e.Graphics.Clip = previous; using var border = new Pen(Color.FromArgb(115, 113, 177, 255)); e.Graphics.DrawPath(border, clip); }
}

internal static class Drawing
{
    public static GraphicsPath Round(Rectangle rect, int radius) { var path = new GraphicsPath(); var d = Math.Min(radius * 2, Math.Min(rect.Width, rect.Height)); path.AddArc(rect.Left, rect.Top, d, d, 180, 90); path.AddArc(rect.Right - d, rect.Top, d, d, 270, 90); path.AddArc(rect.Right - d, rect.Bottom - d, d, d, 0, 90); path.AddArc(rect.Left, rect.Bottom - d, d, d, 90, 90); path.CloseFigure(); return path; }
    public static void DrawIcon(Graphics g, Pen p, AccentIcon icon, Rectangle r)
    {
        var cx = r.Left + r.Width / 2; var cy = r.Top + r.Height / 2;
        switch (icon) {
            case AccentIcon.Usb: g.DrawLine(p, cx, r.Top + 3, cx, r.Bottom - 5); g.DrawLine(p, cx, cy - 1, r.Left + 4, cy - 6); g.DrawLine(p, cx, cy + 4, r.Right - 4, cy - 1); g.DrawEllipse(p, cx - 2, r.Top, 4, 4); g.DrawRectangle(p, r.Left + 2, cy - 8, 4, 4); g.DrawEllipse(p, r.Right - 6, cy - 3, 4, 4); break;
            case AccentIcon.Wifi: g.DrawArc(p, r.Left + 2, r.Top + 4, r.Width - 4, r.Height - 5, 210, 120); g.DrawArc(p, r.Left + 7, r.Top + 9, r.Width - 14, r.Height - 11, 210, 120); g.FillEllipse(Brushes.White, cx - 2, r.Bottom - 5, 4, 4); break;
            case AccentIcon.Monitor: g.DrawRectangle(p, r.Left + 2, r.Top + 3, r.Width - 4, r.Height - 8); g.DrawLine(p, cx, r.Bottom - 5, cx, r.Bottom - 1); g.DrawLine(p, cx - 5, r.Bottom - 1, cx + 5, r.Bottom - 1); break;
            case AccentIcon.Link: g.DrawArc(p, r.Left + 1, cy - 7, r.Width / 2 + 3, 12, 120, 220); g.DrawArc(p, cx - 3, cy - 5, r.Width / 2 + 2, 12, -60, 220); break;
            case AccentIcon.Devices: g.DrawRectangle(p, r.Left + 1, r.Top + 4, r.Width - 9, r.Height - 9); g.DrawLine(p, cx - 4, r.Bottom - 4, cx + 3, r.Bottom - 4); g.DrawRectangle(p, r.Right - 8, cy - 2, 7, r.Height / 2); break;
            case AccentIcon.Fullscreen: g.DrawLine(p, r.Left, r.Top + 6, r.Left, r.Top); g.DrawLine(p, r.Left, r.Top, r.Left + 6, r.Top); g.DrawLine(p, r.Right, r.Bottom - 6, r.Right, r.Bottom); g.DrawLine(p, r.Right, r.Bottom, r.Right - 6, r.Bottom); break;
            case AccentIcon.Phone: g.DrawRectangle(p, r.Left + 4, r.Top, r.Width - 8, r.Height); g.DrawLine(p, cx - 2, r.Bottom - 3, cx + 2, r.Bottom - 3); break;
            case AccentIcon.Info: g.DrawEllipse(p, r); g.DrawLine(p, cx, cy, cx, r.Bottom - 5); g.DrawEllipse(p, cx - 1, r.Top + 5, 2, 2); break;
            case AccentIcon.Shield: g.DrawPolygon(p, new Point[] { new(cx, r.Top), new(r.Right, r.Top + 4), new(r.Right - 2, r.Bottom - 5), new(cx, r.Bottom), new(r.Left + 2, r.Bottom - 5), new(r.Left, r.Top + 4) }); break;
        }
    }
}
