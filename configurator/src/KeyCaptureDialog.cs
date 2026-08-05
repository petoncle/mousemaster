using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace MouseMasterConfigurator
{
    internal sealed class KeyCaptureDialog : Form
    {
        private readonly ActionDefinition definition;
        private readonly List<KeyChord> pending;
        private ListBox shortcutList;
        private RoundedButton captureButton;
        private Label captureHint;
        private RoundedButton removeButton;
        private RoundedButton clearButton;
        private RoundedButton cancelButton;
        private RoundedButton saveButton;
        private bool capturing;
        private string pendingModifier;

        public KeyCaptureDialog(ActionDefinition definition, IEnumerable<KeyChord> current)
        {
            this.definition = definition;
            pending = current == null
                ? new List<KeyChord>()
                : current.Select(delegate(KeyChord chord) { return chord.Clone(); }).ToList();

            Text = L10n.T("dialog.title");
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ShowInTaskbar = false;
            KeyPreview = true;
            BackColor = AppTheme.PageBackground;
            ClientSize = DpiHelper.Sz(570, 500);
            AutoScaleMode = AutoScaleMode.None;
            Font = AppTheme.Font(9.5f, FontStyle.Regular);

            BuildContents();
            RefreshList();
        }

        public IList<KeyChord> Result
        {
            get { return pending.Select(delegate(KeyChord chord) { return chord.Clone(); }).ToList(); }
        }

        private void BuildContents()
        {
            var title = new Label
            {
                Text = definition.Title,
                Font = AppTheme.Font(16f, FontStyle.Bold),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                Location = DpiHelper.Pt(28, 24),
                Size = DpiHelper.Sz(514, 32)
            };
            var description = new Label
            {
                Text = definition.Description,
                Font = AppTheme.Font(9f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = DpiHelper.Pt(29, 59),
                Size = DpiHelper.Sz(512, 42)
            };

            captureButton = new RoundedButton
            {
                Text = L10n.T("dialog.capture"),
                Location = DpiHelper.Pt(28, 112),
                Size = DpiHelper.Sz(514, 56),
                Radius = DpiHelper.S(7),
                BorderColor = Color.FromArgb(195, 207, 228),
                BorderThickness = 1,
                FillColor = Color.White,
                HoverColor = AppTheme.AccentSoft,
                PressedColor = Color.FromArgb(222, 233, 253),
                TextColor = AppTheme.Accent,
                Font = AppTheme.Font(11f, FontStyle.Bold)
            };
            captureButton.Click += delegate { BeginCapture(); };

            captureHint = new Label
            {
                Text = definition.AllowModifiers
                    ? L10n.T("dialog.hintModifiers")
                    : L10n.T("dialog.hintSingle"),
                Font = AppTheme.Font(8.5f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = DpiHelper.Pt(29, 177),
                Size = DpiHelper.Sz(512, 22),
                TextAlign = ContentAlignment.MiddleLeft
            };

            shortcutList = new ListBox
            {
                Location = DpiHelper.Pt(28, 210),
                Size = DpiHelper.Sz(514, 166),
                BorderStyle = BorderStyle.FixedSingle,
                Font = AppTheme.Font(10f, FontStyle.Regular),
                IntegralHeight = false,
                DrawMode = DrawMode.OwnerDrawFixed,
                ItemHeight = DpiHelper.S(32),
                BackColor = Color.White,
                ForeColor = AppTheme.Text
            };
            shortcutList.DrawItem += DrawShortcutItem;

            removeButton = new RoundedButton
            {
                Text = L10n.T("dialog.remove"),
                Height = DpiHelper.S(38),
                FillColor = Color.White,
                HoverColor = AppTheme.DangerSoft,
                PressedColor = Color.FromArgb(250, 220, 220),
                BorderColor = AppTheme.Border,
                TextColor = AppTheme.Danger
            };
            removeButton.Click += delegate
            {
                if (shortcutList.SelectedIndex < 0)
                    return;
                pending.RemoveAt(shortcutList.SelectedIndex);
                RefreshList();
            };

            clearButton = new RoundedButton
            {
                Text = L10n.T("dialog.clear"),
                Height = DpiHelper.S(38),
                FillColor = Color.White,
                HoverColor = AppTheme.DangerSoft,
                PressedColor = Color.FromArgb(250, 220, 220),
                BorderColor = AppTheme.Border,
                TextColor = AppTheme.Danger,
                Enabled = definition.CanClear
            };
            clearButton.Click += delegate
            {
                pending.Clear();
                RefreshList();
            };

            cancelButton = new RoundedButton
            {
                Text = L10n.T("dialog.cancel"),
                DialogResult = DialogResult.Cancel,
                Height = DpiHelper.S(38),
                FillColor = Color.White,
                HoverColor = Color.FromArgb(245, 247, 249),
                PressedColor = Color.FromArgb(237, 240, 244),
                BorderColor = AppTheme.Border,
                TextColor = AppTheme.Text
            };
            saveButton = new RoundedButton
            {
                Text = L10n.T("dialog.save"),
                DialogResult = DialogResult.OK,
                Height = DpiHelper.S(38),
                FillColor = AppTheme.Accent,
                HoverColor = AppTheme.AccentHover,
                PressedColor = Color.FromArgb(30, 64, 175),
                BorderThickness = 0,
                TextColor = Color.White,
                Font = AppTheme.Font(9.5f, FontStyle.Bold)
            };

            RoundedButton.FitWidthToText(removeButton, 112);
            RoundedButton.FitWidthToText(clearButton, 96);
            RoundedButton.FitWidthToText(cancelButton, 92);
            RoundedButton.FitWidthToText(saveButton, 140);
            removeButton.Location = DpiHelper.Pt(28, 388);
            clearButton.Location = new Point(removeButton.Right + DpiHelper.S(10), DpiHelper.S(388));
            saveButton.Left = ClientSize.Width - DpiHelper.S(28) - saveButton.Width;
            saveButton.Top = DpiHelper.S(447);
            cancelButton.Left = saveButton.Left - DpiHelper.S(10) - cancelButton.Width;
            cancelButton.Top = DpiHelper.S(447);

            Controls.Add(title);
            Controls.Add(description);
            Controls.Add(captureButton);
            Controls.Add(captureHint);
            Controls.Add(shortcutList);
            Controls.Add(removeButton);
            Controls.Add(clearButton);
            Controls.Add(cancelButton);
            Controls.Add(saveButton);

            AcceptButton = saveButton;
            CancelButton = cancelButton;
        }

        protected override void WndProc(ref Message message)
        {
            if (message.Msg == DpiHelper.WmDpiChanged)
            {
                int newDpi = DpiHelper.DpiFromWParam(message.WParam);
                if (newDpi != (int)Math.Round(DpiHelper.Factor * 96))
                {
                    DpiHelper.UpdateForDpi(newDpi);
                    RebuildForDpi();
                }
            }
            base.WndProc(ref message);
        }

        private void RebuildForDpi()
        {
            capturing = false;
            pendingModifier = null;
            SuspendLayout();
            Controls.Clear();
            ClientSize = DpiHelper.Sz(570, 500);
            BuildContents();
            RefreshList();
            ResumeLayout(true);
        }

        protected override bool ProcessCmdKey(ref Message message, Keys keyData)
        {
            if (!capturing)
                return base.ProcessCmdKey(ref message, keyData);

            Keys keyCode = keyData & Keys.KeyCode;
            if (KeyCaptureMap.IsReservedPlaceholderKey(keyCode))
            {
                captureHint.Text = L10n.T("dialog.hintReserved");
                return true;
            }
            string keyName = KeyCaptureMap.ToMouseMasterKey(keyCode);
            if (string.IsNullOrEmpty(keyName))
                return true;

            if (KeyCaptureMap.IsModifierKeyCode(keyCode))
            {
                pendingModifier = KeyCaptureMap.SpecificModifier(keyCode);
                captureButton.Text = L10n.F(
                    "dialog.modifierPending",
                    KeyNames.FriendlyName(pendingModifier));
                return true;
            }

            List<string> modifiers = definition.AllowModifiers
                ? KeyCaptureMap.ResolveModifiers(
                    keyData,
                    KeyCaptureMap.PressedModifiers(),
                    pendingModifier)
                : new List<string>();
            AcceptChord(new KeyChord(modifiers, keyName));
            return true;
        }

        protected override void OnKeyUp(KeyEventArgs e)
        {
            if (capturing && pendingModifier != null && KeyCaptureMap.IsModifierKeyCode(e.KeyCode))
            {
                AcceptChord(new KeyChord(new string[0], pendingModifier));
                e.Handled = true;
                e.SuppressKeyPress = true;
            }
            base.OnKeyUp(e);
        }

        private void BeginCapture()
        {
            capturing = true;
            pendingModifier = null;
            captureButton.Text = L10n.T("dialog.capturing");
            captureButton.BorderColor = AppTheme.Accent;
            captureButton.FillColor = AppTheme.AccentSoft;
            captureHint.Text = L10n.T("dialog.hintListening");
            ActiveControl = captureButton;
        }

        private void AcceptChord(KeyChord chord)
        {
            if (!definition.AllowModifiers && chord.HasModifiers)
            {
                System.Media.SystemSounds.Exclamation.Play();
                captureHint.Text = L10n.T("dialog.hintRejectCombo");
                return;
            }

            if (!definition.AllowMultiple)
                pending.Clear();
            if (!pending.Contains(chord))
                pending.Add(chord);
            capturing = false;
            pendingModifier = null;
            captureButton.Text = L10n.T(definition.AllowMultiple ? "dialog.captureMore" : "dialog.captureRedo");
            captureButton.BorderColor = Color.FromArgb(195, 207, 228);
            captureButton.FillColor = Color.White;
            captureHint.Text = L10n.T(definition.AllowMultiple ? "dialog.hintMore" : "dialog.hintDone");
            RefreshList();
        }

        private void RefreshList()
        {
            int selection = shortcutList.SelectedIndex;
            shortcutList.Items.Clear();
            foreach (KeyChord chord in pending)
                shortcutList.Items.Add(chord);
            if (shortcutList.Items.Count > 0)
                shortcutList.SelectedIndex = Math.Min(Math.Max(selection, 0), shortcutList.Items.Count - 1);
            removeButton.Enabled = shortcutList.Items.Count > 0;
        }

        private static void DrawShortcutItem(object sender, DrawItemEventArgs e)
        {
            e.DrawBackground();
            if (e.Index < 0)
                return;
            var list = (ListBox)sender;
            var chord = (KeyChord)list.Items[e.Index];
            Color background = (e.State & DrawItemState.Selected) != 0
                ? AppTheme.AccentSoft
                : Color.White;
            using (var brush = new SolidBrush(background))
                e.Graphics.FillRectangle(brush, e.Bounds);
            TextRenderer.DrawText(
                e.Graphics,
                chord.DisplayText(),
                list.Font,
                new Rectangle(
                    e.Bounds.X + DpiHelper.S(12),
                    e.Bounds.Y,
                    e.Bounds.Width - DpiHelper.S(24),
                    e.Bounds.Height),
                AppTheme.Text,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPrefix);
        }
    }

    internal static class KeyCaptureMap
    {
        private const int VirtualLeftShift = 0xA0;
        private const int VirtualRightShift = 0xA1;
        private const int VirtualLeftControl = 0xA2;
        private const int VirtualRightControl = 0xA3;
        private const int VirtualLeftAlt = 0xA4;
        private const int VirtualRightAlt = 0xA5;
        private const int VirtualLeftWin = 0x5B;
        private const int VirtualRightWin = 0x5C;

        [DllImport("user32.dll")]
        private static extern short GetAsyncKeyState(int virtualKey);

        public static bool IsModifierKeyCode(Keys keyCode)
        {
            return keyCode == Keys.ShiftKey || keyCode == Keys.LShiftKey || keyCode == Keys.RShiftKey ||
                   keyCode == Keys.ControlKey || keyCode == Keys.LControlKey || keyCode == Keys.RControlKey ||
                   keyCode == Keys.Menu || keyCode == Keys.LMenu || keyCode == Keys.RMenu ||
                   keyCode == Keys.LWin || keyCode == Keys.RWin;
        }

        public static bool IsReservedPlaceholderKey(Keys keyCode)
        {
            return keyCode == Keys.F24;
        }

        public static string SpecificModifier(Keys keyCode)
        {
            if (keyCode == Keys.LShiftKey)
                return "leftshift";
            if (keyCode == Keys.RShiftKey)
                return "rightshift";
            if (keyCode == Keys.LControlKey)
                return "leftctrl";
            if (keyCode == Keys.RControlKey)
                return "rightctrl";
            if (keyCode == Keys.LMenu)
                return "leftalt";
            if (keyCode == Keys.RMenu)
                return "rightalt";
            if (keyCode == Keys.LWin)
                return "leftwin";
            if (keyCode == Keys.RWin)
                return "rightwin";

            if (keyCode == Keys.ControlKey)
            {
                if (IsDown(VirtualRightControl))
                    return "rightctrl";
                return "leftctrl";
            }
            if (keyCode == Keys.Menu)
            {
                if (IsDown(VirtualRightAlt))
                    return "rightalt";
                return "leftalt";
            }
            if (keyCode == Keys.ShiftKey)
            {
                if (IsDown(VirtualRightShift))
                    return "rightshift";
                return "leftshift";
            }
            return "leftshift";
        }

        public static List<string> PressedModifiers()
        {
            var result = new List<string>();
            AddIfDown(result, VirtualLeftControl, "leftctrl");
            AddIfDown(result, VirtualRightControl, "rightctrl");
            AddIfDown(result, VirtualLeftShift, "leftshift");
            AddIfDown(result, VirtualRightShift, "rightshift");
            AddIfDown(result, VirtualLeftAlt, "leftalt");
            AddIfDown(result, VirtualRightAlt, "rightalt");
            AddIfDown(result, VirtualLeftWin, "leftwin");
            AddIfDown(result, VirtualRightWin, "rightwin");
            return result;
        }

        public static List<string> ResolveModifiers(
            Keys keyData,
            IEnumerable<string> pressedModifiers,
            string pendingModifier)
        {
            var result = pressedModifiers == null
                ? new List<string>()
                : pressedModifiers
                    .Select(KeyChord.NormalizeKey)
                    .Where(KeyChord.IsModifier)
                    .Distinct(StringComparer.OrdinalIgnoreCase)
                    .ToList();

            AddModifier(result, pendingModifier);
            AddModifierFromKeyData(result, keyData, Keys.Control, "leftctrl", "rightctrl");
            AddModifierFromKeyData(result, keyData, Keys.Shift, "leftshift", "rightshift");
            AddModifierFromKeyData(result, keyData, Keys.Alt, "leftalt", "rightalt");
            return result;
        }

        public static string ToMouseMasterKey(Keys keyCode)
        {
            if (IsReservedPlaceholderKey(keyCode))
                return null;
            if (keyCode >= Keys.A && keyCode <= Keys.Z)
                return keyCode.ToString().ToLowerInvariant();
            if (keyCode >= Keys.D0 && keyCode <= Keys.D9)
                return ((int)(keyCode - Keys.D0)).ToString();
            if (keyCode >= Keys.F1 && keyCode <= Keys.F24)
                return keyCode.ToString().ToLowerInvariant();
            if (keyCode >= Keys.NumPad0 && keyCode <= Keys.NumPad9)
                return "numpad" + ((int)(keyCode - Keys.NumPad0)).ToString();

            switch (keyCode)
            {
                case Keys.ShiftKey:
                case Keys.LShiftKey:
                case Keys.RShiftKey:
                case Keys.ControlKey:
                case Keys.LControlKey:
                case Keys.RControlKey:
                case Keys.Menu:
                case Keys.LMenu:
                case Keys.RMenu:
                case Keys.LWin:
                case Keys.RWin:
                    return SpecificModifier(keyCode);
                case Keys.Capital:
                    return "capslock";
                case Keys.Escape:
                    return "esc";
                case Keys.Back:
                    return "backspace";
                case Keys.Return:
                    return "enter";
                case Keys.Space:
                    return "space";
                case Keys.Tab:
                    return "tab";
                case Keys.Delete:
                    return "del";
                case Keys.Insert:
                    return "insert";
                case Keys.Home:
                    return "home";
                case Keys.End:
                    return "end";
                case Keys.PageUp:
                    return "pageup";
                case Keys.PageDown:
                    return "pagedown";
                case Keys.Up:
                    return "uparrow";
                case Keys.Down:
                    return "downarrow";
                case Keys.Left:
                    return "leftarrow";
                case Keys.Right:
                    return "rightarrow";
                case Keys.Oemcomma:
                    return ",";
                case Keys.OemPeriod:
                    return ".";
                case Keys.OemSemicolon:
                    return ";";
                case Keys.OemQuotes:
                    return "'";
                case Keys.OemQuestion:
                    return "/";
                case Keys.OemMinus:
                    return "minus";
                case Keys.Oemplus:
                    return "=";
                case Keys.OemOpenBrackets:
                    return "[";
                case Keys.OemCloseBrackets:
                    return "]";
                case Keys.OemPipe:
                    return "backslash";
                case Keys.Oemtilde:
                    return "`";
                case Keys.Apps:
                    return "menu";
                case Keys.PrintScreen:
                    return "printscreen";
                case Keys.Scroll:
                    return "scrolllock";
                case Keys.Pause:
                    return "pause";
                case Keys.Add:
                    return "numpadadd";
                case Keys.Subtract:
                    return "numpadsubtract";
                case Keys.Multiply:
                    return "numpadmultiply";
                case Keys.Divide:
                    return "numpaddivide";
                case Keys.Decimal:
                    return "numpaddecimal";
                default:
                    return null;
            }
        }

        private static void AddIfDown(ICollection<string> target, int virtualKey, string name)
        {
            if (IsDown(virtualKey))
                target.Add(name);
        }

        private static void AddModifier(ICollection<string> target, string modifier)
        {
            string normalized = KeyChord.NormalizeKey(modifier);
            if (!KeyChord.IsModifier(normalized) ||
                target.Contains(normalized, StringComparer.OrdinalIgnoreCase))
                return;
            target.Add(normalized);
        }

        private static void AddModifierFromKeyData(
            ICollection<string> target,
            Keys keyData,
            Keys modifierFlag,
            string leftName,
            string rightName)
        {
            if ((keyData & modifierFlag) != modifierFlag)
                return;
            if (target.Contains(leftName, StringComparer.OrdinalIgnoreCase) ||
                target.Contains(rightName, StringComparer.OrdinalIgnoreCase))
                return;
            target.Add(leftName);
        }

        private static bool IsDown(int virtualKey)
        {
            return (GetAsyncKeyState(virtualKey) & 0x8000) != 0;
        }
    }
}
