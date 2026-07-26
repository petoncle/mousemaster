using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace MouseMasterConfigurator
{
    internal static class AppTheme
    {
        public static readonly Color PageBackground = Color.FromArgb(246, 247, 249);
        public static readonly Color Surface = Color.White;
        public static readonly Color Sidebar = Color.FromArgb(249, 250, 251);
        public static readonly Color Text = Color.FromArgb(25, 29, 35);
        public static readonly Color MutedText = Color.FromArgb(102, 111, 124);
        public static readonly Color Border = Color.FromArgb(225, 229, 235);
        public static readonly Color Accent = Color.FromArgb(37, 99, 235);
        public static readonly Color AccentHover = Color.FromArgb(29, 78, 216);
        public static readonly Color AccentSoft = Color.FromArgb(235, 242, 255);
        public static readonly Color Success = Color.FromArgb(22, 130, 84);
        public static readonly Color SuccessSoft = Color.FromArgb(235, 248, 241);
        public static readonly Color Warning = Color.FromArgb(169, 92, 12);
        public static readonly Color WarningSoft = Color.FromArgb(255, 247, 229);
        public static readonly Color Danger = Color.FromArgb(190, 48, 48);
        public static readonly Color DangerSoft = Color.FromArgb(255, 239, 239);

        public static Font Font(float size, FontStyle style)
        {
            return new Font("Segoe UI", size, style, GraphicsUnit.Point);
        }
    }

    internal class RoundedButton : Button
    {
        private bool hovering;
        private bool pressed;

        public RoundedButton()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.ResizeRedraw |
                ControlStyles.UserPaint,
                true);
            FlatStyle = FlatStyle.Flat;
            FlatAppearance.BorderSize = 0;
            Cursor = Cursors.Hand;
            Radius = DpiHelper.S(6);
            FillColor = AppTheme.Surface;
            HoverColor = Color.FromArgb(244, 246, 249);
            PressedColor = Color.FromArgb(236, 239, 244);
            BorderColor = AppTheme.Border;
            TextColor = AppTheme.Text;
            BorderThickness = 1;
            Font = AppTheme.Font(9.5f, FontStyle.Regular);
            Height = DpiHelper.S(38);
        }

        public int Radius { get; set; }
        public int BorderThickness { get; set; }
        public Color FillColor { get; set; }
        public Color HoverColor { get; set; }
        public Color PressedColor { get; set; }
        public Color BorderColor { get; set; }
        public Color TextColor { get; set; }

        protected override void OnMouseEnter(EventArgs e)
        {
            hovering = true;
            Invalidate();
            base.OnMouseEnter(e);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            hovering = false;
            pressed = false;
            Invalidate();
            base.OnMouseLeave(e);
        }

        protected override void OnMouseDown(MouseEventArgs mevent)
        {
            pressed = true;
            Invalidate();
            base.OnMouseDown(mevent);
        }

        protected override void OnMouseUp(MouseEventArgs mevent)
        {
            pressed = false;
            Invalidate();
            base.OnMouseUp(mevent);
        }

        protected override void OnPaint(PaintEventArgs pevent)
        {
            pevent.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            pevent.Graphics.Clear(Parent == null ? BackColor : Parent.BackColor);
            Rectangle bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using (GraphicsPath path = RoundedRectangle(bounds, Radius))
            {
                Color fill = !Enabled
                    ? Color.FromArgb(241, 243, 246)
                    : pressed ? PressedColor : hovering ? HoverColor : FillColor;
                using (var brush = new SolidBrush(fill))
                    pevent.Graphics.FillPath(brush, path);
                if (BorderThickness > 0)
                {
                    using (var pen = new Pen(BorderColor, BorderThickness))
                        pevent.Graphics.DrawPath(pen, path);
                }
            }

            Color foreground = Enabled ? TextColor : Color.FromArgb(155, 161, 170);
            Rectangle textBounds = ClientRectangle;
            TextFormatFlags alignment = TextFormatFlags.HorizontalCenter;
            if (TextAlign == ContentAlignment.MiddleLeft ||
                TextAlign == ContentAlignment.TopLeft ||
                TextAlign == ContentAlignment.BottomLeft)
            {
                alignment = TextFormatFlags.Left;
                textBounds.X += DpiHelper.S(14);
                textBounds.Width -= DpiHelper.S(28);
            }
            else if (TextAlign == ContentAlignment.MiddleRight ||
                     TextAlign == ContentAlignment.TopRight ||
                     TextAlign == ContentAlignment.BottomRight)
            {
                alignment = TextFormatFlags.Right;
                textBounds.X += DpiHelper.S(14);
                textBounds.Width -= DpiHelper.S(28);
            }
            TextRenderer.DrawText(
                pevent.Graphics,
                Text,
                Font,
                textBounds,
                foreground,
                alignment |
                TextFormatFlags.VerticalCenter |
                TextFormatFlags.EndEllipsis |
                TextFormatFlags.NoPrefix);
        }

        internal static GraphicsPath RoundedRectangle(Rectangle bounds, int radius)
        {
            var path = new GraphicsPath();
            int diameter = Math.Max(1, radius * 2);
            var arc = new Rectangle(bounds.X, bounds.Y, diameter, diameter);
            path.AddArc(arc, 180, 90);
            arc.X = bounds.Right - diameter;
            path.AddArc(arc, 270, 90);
            arc.Y = bounds.Bottom - diameter;
            path.AddArc(arc, 0, 90);
            arc.X = bounds.Left;
            path.AddArc(arc, 90, 90);
            path.CloseFigure();
            return path;
        }

        /// <summary>
        /// Grows a button (never below its 96-DPI design width) so its text fits
        /// in the current language at the current DPI.
        /// </summary>
        internal static void FitWidthToText(RoundedButton button, int designMinimumWidth)
        {
            Size text = TextRenderer.MeasureText(
                button.Text,
                button.Font,
                new Size(int.MaxValue, int.MaxValue),
                TextFormatFlags.NoPadding | TextFormatFlags.SingleLine | TextFormatFlags.NoPrefix);
            button.Width = Math.Max(DpiHelper.S(designMinimumWidth), text.Width + DpiHelper.S(30));
        }
    }

    internal sealed class NavigationButton : RoundedButton
    {
        private bool selected;

        public NavigationButton()
        {
            Radius = DpiHelper.S(6);
            BorderThickness = 0;
            TextAlign = ContentAlignment.MiddleLeft;
            Font = AppTheme.Font(10f, FontStyle.Regular);
            Height = DpiHelper.S(44);
        }

        public bool Selected
        {
            get { return selected; }
            set
            {
                selected = value;
                FillColor = selected ? AppTheme.AccentSoft : Color.Transparent;
                HoverColor = selected ? AppTheme.AccentSoft : Color.FromArgb(242, 244, 247);
                PressedColor = AppTheme.AccentSoft;
                TextColor = selected ? AppTheme.Accent : AppTheme.Text;
                Font = AppTheme.Font(10f, selected ? FontStyle.Bold : FontStyle.Regular);
                Invalidate();
            }
        }

        protected override void OnPaint(PaintEventArgs pevent)
        {
            base.OnPaint(pevent);
            if (!selected)
                return;
            using (var brush = new SolidBrush(AppTheme.Accent))
                pevent.Graphics.FillRectangle(
                    brush, 0, DpiHelper.S(10), DpiHelper.S(3), Height - DpiHelper.S(20));
        }
    }

    internal sealed class ToggleSwitch : CheckBox
    {
        public ToggleSwitch()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.Opaque |
                ControlStyles.ResizeRedraw |
                ControlStyles.UserPaint,
                true);
            AutoSize = false;
            Size = DpiHelper.Sz(44, 24);
            BackColor = AppTheme.Surface;
            Cursor = Cursors.Hand;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.Clear(BackColor);
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle track = new Rectangle(0, DpiHelper.S(2), Width - 1, Height - DpiHelper.S(5));
            using (GraphicsPath trackPath = RoundedButton.RoundedRectangle(track, track.Height / 2))
            using (var trackBrush = new SolidBrush(Checked ? AppTheme.Accent : Color.FromArgb(196, 202, 211)))
                e.Graphics.FillPath(trackBrush, trackPath);

            int knobSize = Height - DpiHelper.S(8);
            int knobX = Checked ? Width - knobSize - DpiHelper.S(4) : DpiHelper.S(4);
            using (var knobBrush = new SolidBrush(Color.White))
                e.Graphics.FillEllipse(knobBrush, knobX, DpiHelper.S(4), knobSize, knobSize);
        }
    }

    internal sealed class BindingRow : UserControl
    {
        private readonly Label titleLabel;
        private readonly Label descriptionLabel;
        private readonly RoundedButton bindingButton;
        private readonly RoundedButton clearButton;
        private readonly ToolTip toolTip;
        private IList<KeyChord> bindings = new List<KeyChord>();

        public BindingRow(ActionDefinition definition)
        {
            Definition = definition;
            Height = DpiHelper.S(78);
            BackColor = AppTheme.Surface;
            Margin = new Padding(0);

            titleLabel = new Label
            {
                AutoSize = false,
                Font = AppTheme.Font(10.5f, FontStyle.Regular),
                ForeColor = AppTheme.Text,
                Text = definition.Title,
                Location = new Point(0, DpiHelper.S(16)),
                Height = DpiHelper.S(24)
            };
            descriptionLabel = new Label
            {
                AutoSize = false,
                Font = AppTheme.Font(8.7f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                Text = definition.Description,
                Location = new Point(0, DpiHelper.S(42)),
                Height = DpiHelper.S(20)
            };
            bindingButton = new RoundedButton
            {
                Width = DpiHelper.S(270),
                Height = DpiHelper.S(38),
                Radius = DpiHelper.S(6),
                FillColor = Color.White,
                HoverColor = Color.FromArgb(247, 249, 252),
                PressedColor = Color.FromArgb(239, 243, 249),
                BorderColor = AppTheme.Border,
                TextColor = AppTheme.Text,
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            bindingButton.Click += delegate
            {
                EventHandler handler = EditRequested;
                if (handler != null)
                    handler(this, EventArgs.Empty);
            };

            clearButton = new RoundedButton
            {
                Text = "×",
                Width = DpiHelper.S(30),
                Height = DpiHelper.S(30),
                Radius = DpiHelper.S(15),
                BorderThickness = 0,
                FillColor = Color.Transparent,
                HoverColor = AppTheme.DangerSoft,
                PressedColor = Color.FromArgb(250, 220, 220),
                TextColor = Color.FromArgb(124, 132, 143),
                Font = AppTheme.Font(13f, FontStyle.Regular),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Visible = definition.CanClear
            };
            clearButton.Click += delegate
            {
                EventHandler handler = ClearRequested;
                if (handler != null)
                    handler(this, EventArgs.Empty);
            };

            toolTip = new ToolTip
            {
                AutoPopDelay = 12000,
                InitialDelay = 400,
                ReshowDelay = 100
            };
            toolTip.SetToolTip(clearButton, L10n.T("binding.clearTip"));

            Controls.Add(titleLabel);
            Controls.Add(descriptionLabel);
            Controls.Add(bindingButton);
            Controls.Add(clearButton);
            Resize += delegate { LayoutChildren(); };
            LayoutChildren();
        }

        public ActionDefinition Definition { get; private set; }
        public event EventHandler EditRequested;
        public event EventHandler ClearRequested;

        public void SetBindings(IEnumerable<KeyChord> value)
        {
            bindings = value == null
                ? new List<KeyChord>()
                : value.Select(delegate(KeyChord chord) { return chord.Clone(); }).ToList();
            string full = bindings.Count == 0
                ? L10n.T("binding.none")
                : string.Join(" / ", bindings.Select(delegate(KeyChord chord) { return chord.DisplayText(); }).ToArray());
            string compact;
            if (bindings.Count == 0)
                compact = full;
            else if (bindings.Count <= 3 && full.Length <= 34)
                compact = full;
            else
                compact = string.Join(
                    " / ",
                    bindings.Take(3).Select(delegate(KeyChord chord) { return chord.DisplayText(); }).ToArray()) +
                    L10n.F("binding.more", bindings.Count);

            bindingButton.Text = compact;
            bindingButton.TextColor = bindings.Count == 0 ? AppTheme.MutedText : AppTheme.Text;
            bindingButton.BorderColor = bindings.Count == 0 ? Color.FromArgb(214, 219, 226) : AppTheme.Border;
            toolTip.SetToolTip(bindingButton, full);
            clearButton.Enabled = bindings.Count > 0;
            bindingButton.Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            using (var pen = new Pen(AppTheme.Border))
                e.Graphics.DrawLine(pen, 0, Height - 1, Width, Height - 1);
        }

        private void LayoutChildren()
        {
            int rightArea = DpiHelper.S(326);
            int labelWidth = Math.Max(DpiHelper.S(240), Width - rightArea - DpiHelper.S(16));
            titleLabel.Width = labelWidth;
            descriptionLabel.Width = labelWidth;
            bindingButton.Left = Math.Max(labelWidth + DpiHelper.S(16), Width - DpiHelper.S(310));
            bindingButton.Top = DpiHelper.S(19);
            clearButton.Left = Width - DpiHelper.S(30);
            clearButton.Top = DpiHelper.S(23);
        }
    }

    internal sealed class ToggleOptionRow : UserControl
    {
        private readonly Label titleLabel;
        private readonly Label descriptionLabel;
        private readonly ToggleSwitch toggle;

        public ToggleOptionRow(string title, string description)
        {
            Height = DpiHelper.S(78);
            BackColor = AppTheme.Surface;
            Margin = new Padding(0);
            titleLabel = new Label
            {
                Text = title,
                Font = AppTheme.Font(10.5f, FontStyle.Regular),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                Location = new Point(0, DpiHelper.S(16)),
                Height = DpiHelper.S(24)
            };
            descriptionLabel = new Label
            {
                Text = description,
                Font = AppTheme.Font(8.7f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = new Point(0, DpiHelper.S(42)),
                Height = DpiHelper.S(20)
            };
            toggle = new ToggleSwitch
            {
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Location = new Point(Width - DpiHelper.S(44), DpiHelper.S(27))
            };
            toggle.CheckedChanged += delegate
            {
                EventHandler handler = CheckedChanged;
                if (handler != null)
                    handler(this, EventArgs.Empty);
            };
            Controls.Add(titleLabel);
            Controls.Add(descriptionLabel);
            Controls.Add(toggle);
            Resize += delegate { LayoutChildren(); };
            LayoutChildren();
        }

        public bool Checked
        {
            get { return toggle.Checked; }
            set { toggle.Checked = value; }
        }

        public event EventHandler CheckedChanged;

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            using (var pen = new Pen(AppTheme.Border))
                e.Graphics.DrawLine(pen, 0, Height - 1, Width, Height - 1);
        }

        private void LayoutChildren()
        {
            int labelWidth = Math.Max(DpiHelper.S(240), Width - DpiHelper.S(80));
            titleLabel.Width = labelWidth;
            descriptionLabel.Width = labelWidth;
            toggle.Left = Width - toggle.Width;
        }
    }

    internal sealed class NumericOptionRow : UserControl
    {
        private readonly Label titleLabel;
        private readonly Label descriptionLabel;
        private readonly NumericUpDown valueInput;
        private readonly Label unitLabel;

        public NumericOptionRow(NumericSettingDefinition definition, int value)
        {
            Definition = definition;
            Height = DpiHelper.S(78);
            BackColor = AppTheme.Surface;
            Margin = new Padding(0);

            titleLabel = new Label
            {
                Text = definition.Title,
                Font = AppTheme.Font(10.5f, FontStyle.Regular),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                Location = new Point(0, DpiHelper.S(16)),
                Height = DpiHelper.S(24)
            };
            descriptionLabel = new Label
            {
                Text = definition.Description,
                Font = AppTheme.Font(8.7f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = new Point(0, DpiHelper.S(42)),
                Height = DpiHelper.S(20)
            };
            valueInput = new NumericUpDown
            {
                AutoSize = false,
                Font = AppTheme.Font(10f, FontStyle.Regular),
                ForeColor = AppTheme.Text,
                BackColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                TextAlign = HorizontalAlignment.Right,
                ThousandsSeparator = true,
                Minimum = definition.Minimum,
                Maximum = Math.Max(definition.Maximum, value),
                Increment = definition.Increment,
                Value = Math.Max(definition.Minimum, value),
                Size = DpiHelper.Sz(160, 32),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                AccessibleName = definition.Title
            };
            unitLabel = new Label
            {
                Text = definition.Unit,
                Font = AppTheme.Font(9f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Size = DpiHelper.Sz(58, 32),
                TextAlign = ContentAlignment.MiddleLeft,
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };

            valueInput.ValueChanged += delegate
            {
                EventHandler handler = ValueChanged;
                if (handler != null)
                    handler(this, EventArgs.Empty);
            };

            Controls.Add(titleLabel);
            Controls.Add(descriptionLabel);
            Controls.Add(valueInput);
            Controls.Add(unitLabel);
            Resize += delegate { LayoutChildren(); };
            LayoutChildren();
        }

        public NumericSettingDefinition Definition { get; private set; }

        public int Value
        {
            get { return decimal.ToInt32(valueInput.Value); }
        }

        public event EventHandler ValueChanged;

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            using (var pen = new Pen(AppTheme.Border))
                e.Graphics.DrawLine(pen, 0, Height - 1, Width, Height - 1);
        }

        private void LayoutChildren()
        {
            unitLabel.Left = Width - unitLabel.Width;
            unitLabel.Top = DpiHelper.S(23);
            valueInput.Left = unitLabel.Left - DpiHelper.S(8) - valueInput.Width;
            valueInput.Top = DpiHelper.S(23);
            int labelWidth = Math.Max(DpiHelper.S(240), valueInput.Left - DpiHelper.S(16));
            titleLabel.Width = labelWidth;
            descriptionLabel.Width = labelWidth;
        }
    }

    internal static class TextBoxCue
    {
        private const int EmSetCueBanner = 0x1501;

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr SendMessage(
            IntPtr windowHandle,
            int message,
            IntPtr wordParameter,
            string longParameter);

        public static void Set(TextBox textBox, string cue)
        {
            if (textBox.IsHandleCreated)
                SendMessage(textBox.Handle, EmSetCueBanner, (IntPtr)1, cue);
            else
                textBox.HandleCreated += delegate
                {
                    SendMessage(textBox.Handle, EmSetCueBanner, (IntPtr)1, cue);
                };
        }
    }
}
