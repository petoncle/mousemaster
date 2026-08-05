using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

internal static class FocusModeIntegrationTest
{
    private const uint KeyUp = 0x0002;

    [DllImport("user32.dll")]
    private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr window);

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    [DllImport("kernel32.dll")]
    private static extern uint GetCurrentThreadId();

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool AttachThreadInput(
        uint threadIdAttach,
        uint threadIdAttachTo,
        bool attach);

    [DllImport("user32.dll")]
    private static extern IntPtr SetFocus(IntPtr window);

    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length != 2)
        {
            Console.Error.WriteLine(
                "Usage: FocusModeIntegrationTest.exe <mousemaster.exe> <mousemaster.properties>");
            return 2;
        }

        string mousemasterPath = Path.GetFullPath(args[0]);
        string configurationPath = Path.GetFullPath(args[1]);
        if (!File.Exists(mousemasterPath) || !File.Exists(configurationPath))
        {
            Console.Error.WriteLine("MouseMaster executable or configuration file is missing.");
            return 2;
        }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        var output = new StringBuilder();
        var errors = new StringBuilder();
        var loaded = new ManualResetEvent(false);
        Process mousemaster = null;
        ReceiverForm receiver = null;
        try
        {
            var startInfo = new ProcessStartInfo
            {
                FileName = mousemasterPath,
                WorkingDirectory = Path.GetDirectoryName(configurationPath),
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            mousemaster = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
            mousemaster.OutputDataReceived += delegate(object sender, DataReceivedEventArgs eventArgs)
            {
                if (eventArgs.Data == null)
                    return;
                lock (output)
                    output.AppendLine(eventArgs.Data);
                if (eventArgs.Data.Contains("Loaded configuration file"))
                    loaded.Set();
            };
            mousemaster.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs eventArgs)
            {
                if (eventArgs.Data == null)
                    return;
                lock (errors)
                    errors.AppendLine(eventArgs.Data);
            };

            if (!mousemaster.Start())
                throw new InvalidOperationException("MouseMaster did not start.");
            mousemaster.BeginOutputReadLine();
            mousemaster.BeginErrorReadLine();
            WaitForConfiguration(mousemaster, loaded, output);

            receiver = new ReceiverForm();
            receiver.Show();
            receiver.Editor.Focus();
            receiver.Activate();
            ForceForeground(receiver);
            if (GetForegroundWindow() != receiver.Handle)
                throw new InvalidOperationException(
                    "The input receiver could not become the foreground window. Receiver=" +
                    FormatWindow(receiver.Handle) + ", foreground=" +
                    FormatWindow(GetForegroundWindow()));

            SendChord(Keys.X);
            PumpMessages(250);
            Assert(receiver.Editor.Text == "x", "idle-mode input reaches the active application");

            receiver.Editor.Clear();
            receiver.ResetShortcutCount();
            SendChord(Keys.ControlKey, Keys.M);
            PumpMessages(500);
            SendChord(Keys.F2);
            PumpMessages(350);
            Assert(
                receiver.F2Count == 1,
                "focus mode preserves a configured pass-through combo");
            SendChord(Keys.B);
            PumpMessages(250);
            Assert(
                receiver.Editor.Text == "b",
                "the configured pass-through combo still performs its mode switch");

            receiver.Editor.Clear();
            receiver.ResetShortcutCount();
            SendChord(Keys.ControlKey, Keys.M);
            PumpMessages(500);
            SendChord(Keys.A);
            SendChord(Keys.C);
            SendChord(Keys.D1);
            SendChord(Keys.ControlKey, Keys.S);
            PumpMessages(400);
            Assert(receiver.Editor.Text.Length == 0, "focus mode eats ordinary typing");
            Assert(receiver.ControlSCount == 0, "focus mode eats application shortcuts");

            SendChord(Keys.Q);
            PumpMessages(300);
            SendChord(Keys.B);
            PumpMessages(250);
            Assert(receiver.Editor.Text == "b", "input resumes after leaving keyboard-mouse mode");

            Console.WriteLine(
                "PASS: focus mode preserved configured combos, swallowed unhandled input, and restored input after Q.");
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine("FAIL: " + exception);
            lock (output)
                Console.Error.WriteLine(output.ToString());
            lock (errors)
                Console.Error.WriteLine(errors.ToString());
            return 1;
        }
        finally
        {
            if (receiver != null)
            {
                receiver.Close();
                receiver.Dispose();
            }
            if (mousemaster != null)
            {
                try
                {
                    if (!mousemaster.HasExited)
                    {
                        mousemaster.Kill();
                        mousemaster.WaitForExit();
                    }
                }
                catch
                {
                }
                mousemaster.Dispose();
            }
            loaded.Dispose();
        }
    }

    private static void WaitForConfiguration(
        Process mousemaster,
        WaitHandle loaded,
        StringBuilder output)
    {
        DateTime deadline = DateTime.UtcNow.AddSeconds(12);
        while (!loaded.WaitOne(50))
        {
            Application.DoEvents();
            if (mousemaster.HasExited)
                throw new InvalidOperationException(
                    "MouseMaster exited before loading the configuration." + Environment.NewLine + output);
            if (DateTime.UtcNow >= deadline)
                throw new TimeoutException("MouseMaster did not report a loaded configuration.");
        }
    }

    private static void PumpMessages(int milliseconds)
    {
        DateTime deadline = DateTime.UtcNow.AddMilliseconds(milliseconds);
        while (DateTime.UtcNow < deadline)
        {
            Application.DoEvents();
            Thread.Sleep(10);
        }
    }

    private static void ForceForeground(ReceiverForm receiver)
    {
        DateTime deadline = DateTime.UtcNow.AddSeconds(2);
        do
        {
            IntPtr foreground = GetForegroundWindow();
            uint ignoredProcessId;
            uint foregroundThreadId = GetWindowThreadProcessId(
                foreground,
                out ignoredProcessId);
            uint currentThreadId = GetCurrentThreadId();
            bool attached = false;
            try
            {
                if (foregroundThreadId != 0 && foregroundThreadId != currentThreadId)
                    attached = AttachThreadInput(currentThreadId, foregroundThreadId, true);
                receiver.Activate();
                receiver.BringToFront();
                SetForegroundWindow(receiver.Handle);
                SetFocus(receiver.Editor.Handle);
            }
            finally
            {
                if (attached)
                    AttachThreadInput(currentThreadId, foregroundThreadId, false);
            }

            PumpMessages(50);
            if (GetForegroundWindow() == receiver.Handle)
                return;
        }
        while (DateTime.UtcNow < deadline);
    }

    private static void SendChord(params Keys[] keys)
    {
        foreach (Keys key in keys)
            keybd_event((byte)key, 0, 0, UIntPtr.Zero);
        for (int index = keys.Length - 1; index >= 0; index--)
            keybd_event((byte)keys[index], 0, KeyUp, UIntPtr.Zero);
    }

    private static void Assert(bool condition, string message)
    {
        if (!condition)
            throw new InvalidOperationException("Assertion failed: " + message);
        Console.WriteLine("OK: " + message);
    }

    private static string FormatWindow(IntPtr window)
    {
        uint processId;
        GetWindowThreadProcessId(window, out processId);
        string processName = "unknown";
        try
        {
            using (Process process = Process.GetProcessById((int)processId))
                processName = process.ProcessName;
        }
        catch
        {
        }
        return string.Format("0x{0:X} ({1}, pid {2})", window.ToInt64(), processName, processId);
    }

    private sealed class ReceiverForm : Form
    {
        public ReceiverForm()
        {
            Text = "MouseMaster focus-mode integration receiver";
            StartPosition = FormStartPosition.Manual;
            Location = new Point(-20000, -20000);
            ClientSize = new Size(320, 100);
            ShowInTaskbar = false;
            FormBorderStyle = FormBorderStyle.FixedToolWindow;
            TopMost = true;
            Editor = new TextBox
            {
                Dock = DockStyle.Fill,
                Multiline = false
            };
            Controls.Add(Editor);
        }

        public TextBox Editor { get; private set; }
        public int ControlSCount { get; private set; }
        public int F2Count { get; private set; }

        public void ResetShortcutCount()
        {
            ControlSCount = 0;
            F2Count = 0;
        }

        protected override bool ProcessCmdKey(ref Message message, Keys keyData)
        {
            if ((keyData & (Keys.KeyCode | Keys.Modifiers)) == (Keys.Control | Keys.S))
                ControlSCount++;
            if ((keyData & (Keys.KeyCode | Keys.Modifiers)) == Keys.F2)
                F2Count++;
            return base.ProcessCmdKey(ref message, keyData);
        }
    }
}
