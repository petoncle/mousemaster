using System;
using System.Drawing;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace MouseMasterConfigurator
{
    /// <summary>
    /// Scales design-time pixel metrics (authored for 96 DPI) to the DPI of the
    /// monitor the window is on. Fonts are specified in points and therefore
    /// already scale with DPI; only pixel-based sizes, positions, paddings and
    /// margins go through this helper. Without it, fixed-size labels clip their
    /// (physically larger) text at 125% / 150% scaling.
    /// </summary>
    internal static class DpiHelper
    {
        public const int WmDpiChanged = 0x02E0;

        private const uint SwpNoZOrder = 0x0004;
        private const uint SwpNoActivate = 0x0010;

        private static float factor = ReadSystemDpiFactor();

        public static float Factor
        {
            get { return factor; }
        }

        /// <summary>Scales a design-time (96 DPI) pixel value.</summary>
        public static int S(int value)
        {
            return (int)Math.Round(value * factor, MidpointRounding.AwayFromZero);
        }

        public static Point Pt(int x, int y)
        {
            return new Point(S(x), S(y));
        }

        public static Size Sz(int width, int height)
        {
            return new Size(S(width), S(height));
        }

        public static Padding Pd(int left, int top, int right, int bottom)
        {
            return new Padding(S(left), S(top), S(right), S(bottom));
        }

        public static void UpdateForDpi(int dpi)
        {
            if (dpi < 48 || dpi > 960)
                return;
            factor = dpi / 96f;
        }

        /// <summary>Extracts the new DPI from a WM_DPICHANGED wParam.</summary>
        public static int DpiFromWParam(IntPtr wParam)
        {
            return (int)(wParam.ToInt64() & 0xFFFF);
        }

        /// <summary>
        /// Applies the window rectangle suggested by WM_DPICHANGED so the window
        /// keeps its relative size when moved between monitors.
        /// </summary>
        public static void ApplySuggestedWindowRect(Form form, IntPtr lParam)
        {
            if (form == null || lParam == IntPtr.Zero)
                return;
            try
            {
                NativeRect rect = (NativeRect)Marshal.PtrToStructure(lParam, typeof(NativeRect));
                SetWindowPos(
                    form.Handle,
                    IntPtr.Zero,
                    rect.Left,
                    rect.Top,
                    Math.Max(1, rect.Right - rect.Left),
                    Math.Max(1, rect.Bottom - rect.Top),
                    SwpNoZOrder | SwpNoActivate);
            }
            catch
            {
            }
        }

        private static float ReadSystemDpiFactor()
        {
            try
            {
                using (Graphics graphics = Graphics.FromHwnd(IntPtr.Zero))
                    return graphics.DpiX / 96f;
            }
            catch
            {
                return 1f;
            }
        }

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool SetWindowPos(
            IntPtr hWnd,
            IntPtr hWndInsertAfter,
            int x,
            int y,
            int cx,
            int cy,
            uint flags);

        [StructLayout(LayoutKind.Sequential)]
        private struct NativeRect
        {
            public int Left;
            public int Top;
            public int Right;
            public int Bottom;
        }
    }
}
