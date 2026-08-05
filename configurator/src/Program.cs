using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("MouseMaster Configurator")]
[assembly: System.Reflection.AssemblyDescription("GUI configurator for MouseMaster")]
[assembly: System.Reflection.AssemblyCompany("Rezetyan")]
[assembly: System.Reflection.AssemblyProduct("MouseMaster Configurator")]
[assembly: System.Reflection.AssemblyVersion("2.1.0.0")]
[assembly: System.Reflection.AssemblyFileVersion("2.1.0.0")]

namespace MouseMasterConfigurator
{
    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            if (args.Length > 0 &&
                string.Equals(args[0], "--self-test", StringComparison.OrdinalIgnoreCase))
            {
                string reportPath = args.Length > 1
                    ? Path.GetFullPath(args[1])
                    : Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "MouseMasterConfigurator.self-test.log");
                string report;
                string acceptanceConfiguration;
                int exitCode = SelfTests.Run(out report, out acceptanceConfiguration);
                File.WriteAllText(reportPath, report, new UTF8Encoding(false));
                if (exitCode == 0 && args.Length > 2)
                {
                    string acceptancePath = Path.GetFullPath(args[2]);
                    string acceptanceDirectory = Path.GetDirectoryName(acceptancePath);
                    if (!string.IsNullOrEmpty(acceptanceDirectory))
                        Directory.CreateDirectory(acceptanceDirectory);
                    File.WriteAllText(
                        acceptancePath,
                        acceptanceConfiguration,
                        new UTF8Encoding(false));
                }
                Environment.ExitCode = exitCode;
                return;
            }

            // DPI awareness comes from app.manifest (PerMonitorV2); layout metrics
            // are scaled explicitly via DpiHelper.
            L10n.Language = L10n.LoadPreference();

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
            Application.ThreadException += delegate(object sender, ThreadExceptionEventArgs eventArgs)
            {
                ShowFatalError(eventArgs.Exception);
            };
            AppDomain.CurrentDomain.UnhandledException += delegate(object sender, UnhandledExceptionEventArgs eventArgs)
            {
                ShowFatalError(eventArgs.ExceptionObject as Exception);
            };

            string configurationPath = Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "mousemaster.properties");
            if (args.Length >= 2 &&
                string.Equals(args[0], "--config", StringComparison.OrdinalIgnoreCase))
                configurationPath = Path.GetFullPath(args[1]);

            using (var form = new MainForm(configurationPath))
            {
                form.Icon = AppIcon.Create();
                Application.Run(form);
            }
        }

        private static void ShowFatalError(Exception exception)
        {
            string message = exception == null ? L10n.T("error.unknown") : exception.ToString();
            try
            {
                string logPath = Path.Combine(
                    AppDomain.CurrentDomain.BaseDirectory,
                    "MouseMasterConfigurator.error.log");
                File.WriteAllText(logPath, message, new UTF8Encoding(false));
            }
            catch
            {
            }
            MessageBox.Show(
                message,
                L10n.T("error.fatal"),
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }
    }

    internal static class AppIcon
    {
        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        private static extern bool DestroyIcon(IntPtr handle);

        public static Icon Create()
        {
            using (var bitmap = new Bitmap(32, 32))
            using (Graphics graphics = Graphics.FromImage(bitmap))
            {
                graphics.SmoothingMode = SmoothingMode.AntiAlias;
                graphics.Clear(Color.Transparent);
                using (var background = new SolidBrush(AppTheme.Accent))
                    graphics.FillRoundedRectangle(background, new Rectangle(1, 1, 30, 30), 7);
                using (var pen = new Pen(Color.White, 2f))
                {
                    graphics.DrawEllipse(pen, 10, 10, 12, 12);
                    graphics.DrawLine(pen, 16, 5, 16, 10);
                    graphics.DrawLine(pen, 16, 22, 16, 27);
                    graphics.DrawLine(pen, 5, 16, 10, 16);
                    graphics.DrawLine(pen, 22, 16, 27, 16);
                }
                IntPtr handle = bitmap.GetHicon();
                try
                {
                    using (Icon temporary = Icon.FromHandle(handle))
                        return (Icon)temporary.Clone();
                }
                finally
                {
                    DestroyIcon(handle);
                }
            }
        }

        private static void FillRoundedRectangle(
            this Graphics graphics,
            Brush brush,
            Rectangle bounds,
            int radius)
        {
            using (GraphicsPath path = RoundedButton.RoundedRectangle(bounds, radius))
                graphics.FillPath(brush, path);
        }
    }
}
