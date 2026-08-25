namespace UniversalMobileDesktop.Receiver;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) =>
        {
            File.WriteAllText(@"c:\Github\Desktop-Mod\windows\crash.log",
                $"[ThreadException] {DateTime.Now}\n{e.Exception}\n");
            MessageBox.Show(e.Exception.ToString(), "Desktop Mod - Error",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        };

        AppDomain.CurrentDomain.UnhandledException += (_, e) =>
        {
            File.WriteAllText(@"c:\Github\Desktop-Mod\windows\crash.log",
                $"[UnhandledException] {DateTime.Now}\n{e.ExceptionObject}\n");
        };

        try
        {
            ApplicationConfiguration.Initialize();
            Application.Run(new ReceiverForm());
        }
        catch (Exception ex)
        {
            File.WriteAllText(@"c:\Github\Desktop-Mod\windows\crash.log",
                $"[Main] {DateTime.Now}\n{ex}\n");
            MessageBox.Show(ex.ToString(), "Desktop Mod - Startup Error",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
