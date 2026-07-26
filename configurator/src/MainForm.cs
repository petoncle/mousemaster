using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Windows.Forms;

namespace MouseMasterConfigurator
{
    internal sealed class MainForm : Form
    {
        private readonly string configurationPath;
        private readonly ConfiguratorEngine engine;
        private readonly List<NavigationButton> navigationButtons =
            new List<NavigationButton>();

        private ConfiguratorState state;
        private string loadedSourceText;
        private bool dirty;
        private bool suppressToggleEvents;
        private bool suppressLanguageEvents;
        private string selectedCategoryId = "general";

        private TableLayoutPanel mainLayout;
        private FlowLayoutPanel rowsPanel;
        private Panel bottomBar;
        private Label subtitleLabel;
        private Label fileCaptionLabel;
        private ComboBox languageCombo;
        private Label pageTitle;
        private Label pageDescription;
        private TextBox searchBox;
        private Panel conflictPanel;
        private Label conflictLabel;
        private Label statusLabel;
        private RoundedButton restoreButton;
        private RoundedButton reloadButton;
        private RoundedButton saveButton;
        private ToggleSwitch focusToggle;
        private Label focusTitle;
        private Label focusDescription;

        private string statusMessage;
        private Color statusColor = Color.Empty;
        private ActionDefinition conflictCurrent;
        private IList<ConflictChange> conflictChanges;

        public MainForm(string configurationPath)
        {
            this.configurationPath = Path.GetFullPath(configurationPath);
            engine = new ConfiguratorEngine();

            Text = L10n.T("app.title");
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = AppTheme.PageBackground;
            ClientSize = DpiHelper.Sz(1180, 790);
            MinimumSize = DpiHelper.Sz(980, 650);
            // Layout metrics are scaled explicitly through DpiHelper; the built-in
            // auto-scaler would be a no-op here because the whole tree is created
            // at the current DPI.
            AutoScaleMode = AutoScaleMode.None;
            Font = AppTheme.Font(9.5f, FontStyle.Regular);

            InitializeLayout();
            LoadConfiguration(false);
            FormClosing += OnFormClosing;
        }

        private void InitializeLayout()
        {
            SuspendLayout();
            var sidebar = BuildSidebar();
            Controls.Add(sidebar);

            mainLayout = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                BackColor = AppTheme.Surface,
                ColumnCount = 1,
                RowCount = 4,
                Margin = new Padding(0),
                Padding = new Padding(0)
            };
            mainLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100f));
            mainLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, DpiHelper.S(136)));
            mainLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 0f));
            mainLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
            mainLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, DpiHelper.S(72)));

            Panel header = BuildHeader();
            conflictPanel = BuildConflictPanel();
            rowsPanel = BuildRowsPanel();
            bottomBar = BuildBottomBar();

            mainLayout.Controls.Add(header, 0, 0);
            mainLayout.Controls.Add(conflictPanel, 0, 1);
            mainLayout.Controls.Add(rowsPanel, 0, 2);
            mainLayout.Controls.Add(bottomBar, 0, 3);
            Controls.Add(mainLayout);
            mainLayout.BringToFront();

            ResumeLayout(true);
        }

        private Panel BuildSidebar()
        {
            var sidebar = new Panel
            {
                Dock = DockStyle.Left,
                Width = DpiHelper.S(240),
                BackColor = AppTheme.Sidebar,
                Padding = DpiHelper.Pd(18, 20, 18, 16)
            };
            sidebar.Paint += delegate(object sender, PaintEventArgs e)
            {
                using (var pen = new Pen(AppTheme.Border))
                    e.Graphics.DrawLine(pen, sidebar.Width - 1, 0, sidebar.Width - 1, sidebar.Height);
            };

            var logo = new LogoControl
            {
                Location = DpiHelper.Pt(18, 20),
                Size = DpiHelper.Sz(38, 38)
            };
            var brand = new Label
            {
                Text = "MouseMaster",
                Font = AppTheme.Font(12.5f, FontStyle.Bold),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                Location = DpiHelper.Pt(66, 20),
                Size = DpiHelper.Sz(158, 26)
            };
            subtitleLabel = new Label
            {
                Text = L10n.T("sidebar.subtitle"),
                Font = AppTheme.Font(8.8f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = DpiHelper.Pt(66, 47),
                Size = DpiHelper.Sz(158, 20)
            };
            sidebar.Controls.Add(logo);
            sidebar.Controls.Add(brand);
            sidebar.Controls.Add(subtitleLabel);

            int top = DpiHelper.S(96);
            foreach (CategoryDefinition category in BindingCatalog.Categories)
            {
                var button = new NavigationButton
                {
                    Text = category.Title,
                    Tag = category.Id,
                    Location = new Point(DpiHelper.S(18), top),
                    Size = DpiHelper.Sz(204, 44),
                    Selected = category.Id == selectedCategoryId
                };
                button.Click += OnCategoryClick;
                navigationButtons.Add(button);
                sidebar.Controls.Add(button);
                top += DpiHelper.S(50);
            }

            languageCombo = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                Font = AppTheme.Font(9.5f, FontStyle.Regular),
                ForeColor = AppTheme.Text,
                BackColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Anchor = AnchorStyles.Left | AnchorStyles.Bottom,
                Location = new Point(DpiHelper.S(18), sidebar.Height - DpiHelper.S(126)),
                Width = DpiHelper.S(204)
            };
            languageCombo.Items.Add("简体中文");
            languageCombo.Items.Add("English");
            suppressLanguageEvents = true;
            languageCombo.SelectedIndex = L10n.Language == UiLanguage.English ? 1 : 0;
            suppressLanguageEvents = false;
            languageCombo.SelectedIndexChanged += OnLanguageChanged;

            fileCaptionLabel = new Label
            {
                Text = L10n.T("sidebar.file"),
                Font = AppTheme.Font(8.5f, FontStyle.Bold),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Size = DpiHelper.Sz(204, 20),
                Anchor = AnchorStyles.Left | AnchorStyles.Bottom,
                Location = new Point(DpiHelper.S(18), sidebar.Height - DpiHelper.S(92))
            };
            var filePath = new Label
            {
                Text = configurationPath,
                Font = AppTheme.Font(8.2f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoEllipsis = true,
                AutoSize = false,
                Size = DpiHelper.Sz(204, 40),
                Anchor = AnchorStyles.Left | AnchorStyles.Bottom,
                Location = new Point(DpiHelper.S(18), sidebar.Height - DpiHelper.S(68))
            };
            sidebar.Controls.Add(languageCombo);
            sidebar.Controls.Add(fileCaptionLabel);
            sidebar.Controls.Add(filePath);
            sidebar.Resize += delegate
            {
                languageCombo.Top = sidebar.Height - DpiHelper.S(126);
                fileCaptionLabel.Top = sidebar.Height - DpiHelper.S(92);
                filePath.Top = sidebar.Height - DpiHelper.S(68);
            };
            var toolTip = new ToolTip();
            toolTip.SetToolTip(filePath, configurationPath);
            return sidebar;
        }

        private Panel BuildHeader()
        {
            var header = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = AppTheme.Surface,
                Padding = DpiHelper.Pd(30, 20, 30, 16)
            };
            header.Paint += delegate(object sender, PaintEventArgs e)
            {
                using (var pen = new Pen(AppTheme.Border))
                    e.Graphics.DrawLine(pen, 0, header.Height - 1, header.Width, header.Height - 1);
            };

            pageTitle = new Label
            {
                Name = "pageTitle",
                Text = L10n.T("category.general.title"),
                Font = AppTheme.Font(18f, FontStyle.Bold),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                Location = DpiHelper.Pt(30, 17),
                Size = DpiHelper.Sz(350, 34)
            };
            pageDescription = new Label
            {
                Name = "pageDescription",
                Text = L10n.T("category.general.description"),
                Font = AppTheme.Font(9f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                AutoEllipsis = true,
                Location = DpiHelper.Pt(31, 56),
                Size = DpiHelper.Sz(440, 22)
            };
            searchBox = new TextBox
            {
                BorderStyle = BorderStyle.FixedSingle,
                Font = AppTheme.Font(10f, FontStyle.Regular),
                ForeColor = AppTheme.Text,
                BackColor = Color.White,
                Location = DpiHelper.Pt(30, 91),
                Size = DpiHelper.Sz(360, 30)
            };
            TextBoxCue.Set(searchBox, L10n.T("search.cue"));
            searchBox.TextChanged += delegate { RenderRows(); };

            focusTitle = new Label
            {
                Name = "focusTitle",
                Text = L10n.T("focus.title"),
                Font = AppTheme.Font(10.5f, FontStyle.Bold),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                Size = DpiHelper.Sz(210, 24),
                TextAlign = ContentAlignment.MiddleRight,
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            focusDescription = new Label
            {
                Name = "focusDescription",
                Text = L10n.T("focus.off"),
                Font = AppTheme.Font(8.3f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Size = DpiHelper.Sz(700, 22),
                TextAlign = ContentAlignment.MiddleRight,
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            focusToggle = new ToggleSwitch
            {
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            focusToggle.CheckedChanged += delegate
            {
                if (suppressToggleEvents || state == null)
                    return;
                state.FocusModeEnabled = focusToggle.Checked;
                UpdateFocusAppearance();
                SetDirty(true);
            };

            header.Controls.Add(pageTitle);
            header.Controls.Add(pageDescription);
            header.Controls.Add(searchBox);
            header.Controls.Add(focusTitle);
            header.Controls.Add(focusDescription);
            header.Controls.Add(focusToggle);
            header.Resize += delegate
            {
                focusToggle.Left = header.Width - DpiHelper.S(30) - focusToggle.Width;
                focusToggle.Top = DpiHelper.S(23);
                focusTitle.Left = focusToggle.Left - focusTitle.Width - DpiHelper.S(12);
                focusTitle.Top = DpiHelper.S(16);
                int descriptionWidth = Math.Min(
                    DpiHelper.S(700),
                    focusToggle.Left - DpiHelper.S(12) - DpiHelper.S(20));
                focusDescription.Width = Math.Max(DpiHelper.S(200), descriptionWidth);
                focusDescription.Left = focusToggle.Left - DpiHelper.S(12) - focusDescription.Width;
                focusDescription.Top = DpiHelper.S(51);
                searchBox.Width = Math.Min(
                    DpiHelper.S(420),
                    Math.Max(DpiHelper.S(280), header.Width - DpiHelper.S(600)));
            };
            return header;
        }

        private Panel BuildConflictPanel()
        {
            var panel = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = AppTheme.WarningSoft,
                Padding = DpiHelper.Pd(30, 0, 30, 0),
                Visible = false
            };
            conflictLabel = new Label
            {
                Dock = DockStyle.Fill,
                Font = AppTheme.Font(9f, FontStyle.Regular),
                ForeColor = AppTheme.Warning,
                TextAlign = ContentAlignment.MiddleLeft,
                AutoEllipsis = true
            };
            var close = new RoundedButton
            {
                Text = "×",
                Dock = DockStyle.Right,
                Width = DpiHelper.S(32),
                Radius = DpiHelper.S(16),
                BorderThickness = 0,
                FillColor = Color.Transparent,
                HoverColor = Color.FromArgb(250, 232, 194),
                PressedColor = Color.FromArgb(244, 220, 172),
                TextColor = AppTheme.Warning,
                Font = AppTheme.Font(13f, FontStyle.Regular)
            };
            close.Click += delegate { HideConflictBanner(); };
            panel.Controls.Add(conflictLabel);
            panel.Controls.Add(close);
            return panel;
        }

        private FlowLayoutPanel BuildRowsPanel()
        {
            var panel = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                BackColor = AppTheme.Surface,
                AutoScroll = true,
                FlowDirection = FlowDirection.TopDown,
                WrapContents = false,
                Padding = DpiHelper.Pd(30, 14, 30, 22),
                Margin = new Padding(0)
            };
            panel.SizeChanged += delegate { ResizeRows(); };
            return panel;
        }

        private Panel BuildBottomBar()
        {
            var bottom = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = AppTheme.Surface,
                Padding = DpiHelper.Pd(30, 16, 30, 16)
            };
            bottom.Paint += delegate(object sender, PaintEventArgs e)
            {
                using (var pen = new Pen(AppTheme.Border))
                    e.Graphics.DrawLine(pen, 0, 0, bottom.Width, 0);
            };

            statusLabel = new Label
            {
                Text = L10n.T("status.loading"),
                Font = AppTheme.Font(8.8f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = DpiHelper.Pt(30, 24),
                Height = DpiHelper.S(24),
                TextAlign = ContentAlignment.MiddleLeft,
                AutoEllipsis = true,
                Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top
            };
            restoreButton = new RoundedButton
            {
                Text = L10n.T("button.restore"),
                Size = DpiHelper.Sz(104, 40),
                FillColor = Color.White,
                HoverColor = AppTheme.DangerSoft,
                PressedColor = Color.FromArgb(250, 220, 220),
                BorderColor = AppTheme.Border,
                TextColor = AppTheme.Danger,
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            restoreButton.Click += delegate { RestoreDefault(); };
            reloadButton = new RoundedButton
            {
                Text = L10n.T("button.reload"),
                Size = DpiHelper.Sz(104, 40),
                FillColor = Color.White,
                HoverColor = Color.FromArgb(245, 247, 249),
                PressedColor = Color.FromArgb(237, 240, 244),
                BorderColor = AppTheme.Border,
                TextColor = AppTheme.Text,
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            reloadButton.Click += delegate { ReloadRequested(); };
            saveButton = new RoundedButton
            {
                Text = L10n.T("button.save"),
                Size = DpiHelper.Sz(124, 40),
                FillColor = AppTheme.Accent,
                HoverColor = AppTheme.AccentHover,
                PressedColor = Color.FromArgb(30, 64, 175),
                BorderThickness = 0,
                TextColor = Color.White,
                Font = AppTheme.Font(9.5f, FontStyle.Bold),
                Anchor = AnchorStyles.Top | AnchorStyles.Right
            };
            saveButton.Click += delegate { SaveConfiguration(); };

            bottom.Controls.Add(statusLabel);
            bottom.Controls.Add(restoreButton);
            bottom.Controls.Add(reloadButton);
            bottom.Controls.Add(saveButton);
            bottom.Resize += delegate { LayoutBottomBar(); };
            FitBottomButtons();
            return bottom;
        }

        private void FitBottomButtons()
        {
            RoundedButton.FitWidthToText(restoreButton, 104);
            RoundedButton.FitWidthToText(reloadButton, 104);
            RoundedButton.FitWidthToText(saveButton, 124);
            LayoutBottomBar();
        }

        private void LayoutBottomBar()
        {
            if (bottomBar == null || saveButton == null)
                return;
            saveButton.Left = bottomBar.Width - DpiHelper.S(30) - saveButton.Width;
            saveButton.Top = DpiHelper.S(16);
            reloadButton.Left = saveButton.Left - DpiHelper.S(10) - reloadButton.Width;
            reloadButton.Top = DpiHelper.S(16);
            restoreButton.Left = reloadButton.Left - DpiHelper.S(10) - restoreButton.Width;
            restoreButton.Top = DpiHelper.S(16);
            statusLabel.Width = Math.Max(DpiHelper.S(200), restoreButton.Left - DpiHelper.S(48));
        }

        private void OnLanguageChanged(object sender, EventArgs e)
        {
            if (suppressLanguageEvents || languageCombo == null)
                return;
            UiLanguage selected = languageCombo.SelectedIndex == 1
                ? UiLanguage.English
                : UiLanguage.Chinese;
            if (selected == L10n.Language)
                return;
            L10n.Language = selected;
            L10n.SavePreference();
            ApplyLanguage();
        }

        /// <summary>Re-applies every user-visible string after a language switch.</summary>
        private void ApplyLanguage()
        {
            Text = L10n.T("app.title");
            if (subtitleLabel != null)
                subtitleLabel.Text = L10n.T("sidebar.subtitle");
            if (fileCaptionLabel != null)
                fileCaptionLabel.Text = L10n.T("sidebar.file");
            foreach (NavigationButton nav in navigationButtons)
                nav.Text = BindingCatalog.FindCategory((string)nav.Tag).Title;
            if (searchBox != null)
                TextBoxCue.Set(searchBox, L10n.T("search.cue"));
            if (focusTitle != null)
                focusTitle.Text = L10n.T("focus.title");
            UpdateFocusAppearance();
            restoreButton.Text = L10n.T("button.restore");
            reloadButton.Text = L10n.T("button.reload");
            saveButton.Text = L10n.T("button.save");
            FitBottomButtons();
            if (state != null)
            {
                ShowStatus(
                    dirty ? L10n.T("status.dirty") : L10n.T("status.ready"),
                    dirty ? AppTheme.Warning : AppTheme.Success);
            }
            if (conflictCurrent != null && conflictPanel.Visible)
                conflictLabel.Text = BuildConflictText(conflictCurrent, conflictChanges);
            RenderRows();
        }

        protected override void WndProc(ref Message message)
        {
            if (message.Msg == DpiHelper.WmDpiChanged)
            {
                int newDpi = DpiHelper.DpiFromWParam(message.WParam);
                if (newDpi != (int)Math.Round(DpiHelper.Factor * 96))
                {
                    DpiHelper.UpdateForDpi(newDpi);
                    DpiHelper.ApplySuggestedWindowRect(this, message.LParam);
                    RebuildForDpi();
                }
            }
            base.WndProc(ref message);
        }

        /// <summary>
        /// Recreates the whole control tree at the current DPI factor. The old
        /// .NET 4.0-targeted WinForms runtime does not rescale PerMonitorV2
        /// windows on its own, so moving to another monitor triggers a rebuild.
        /// </summary>
        private void RebuildForDpi()
        {
            SuspendLayout();
            Controls.Clear();
            navigationButtons.Clear();
            MinimumSize = DpiHelper.Sz(980, 650);
            InitializeLayout();
            if (state != null)
            {
                suppressToggleEvents = true;
                focusToggle.Checked = state.FocusModeEnabled;
                suppressToggleEvents = false;
            }
            UpdateFocusAppearance();
            ApplyLanguage();
            if (statusMessage != null)
            {
                ShowStatus(
                    statusMessage,
                    statusColor == Color.Empty ? AppTheme.MutedText : statusColor);
            }
            if (conflictCurrent != null)
            {
                conflictLabel.Text = BuildConflictText(conflictCurrent, conflictChanges);
                conflictPanel.Visible = true;
                mainLayout.RowStyles[1].Height = DpiHelper.S(44);
            }
            saveButton.Enabled = state != null;
            ResumeLayout(true);
        }

        private void OnCategoryClick(object sender, EventArgs e)
        {
            var button = sender as NavigationButton;
            if (button == null)
                return;
            selectedCategoryId = (string)button.Tag;
            searchBox.Text = string.Empty;
            foreach (NavigationButton nav in navigationButtons)
                nav.Selected = ReferenceEquals(nav, button);
            RenderRows();
        }

        private void RenderRows()
        {
            if (rowsPanel == null || state == null)
                return;

            rowsPanel.SuspendLayout();
            rowsPanel.Controls.Clear();
            string query = searchBox == null ? string.Empty : searchBox.Text.Trim();
            IEnumerable<ActionDefinition> actions;
            IEnumerable<NumericSettingDefinition> numericSettings;
            string sectionTitle;
            string sectionDescription;

            if (query.Length > 0)
            {
                actions = BindingCatalog.Actions.Where(
                    delegate(ActionDefinition action)
                    {
                        return action.Title.IndexOf(query, StringComparison.CurrentCultureIgnoreCase) >= 0 ||
                               action.Description.IndexOf(query, StringComparison.CurrentCultureIgnoreCase) >= 0;
                    });
                numericSettings = BindingCatalog.NumericSettings.Where(
                    delegate(NumericSettingDefinition setting)
                    {
                        return setting.Title.IndexOf(query, StringComparison.CurrentCultureIgnoreCase) >= 0 ||
                               setting.Description.IndexOf(query, StringComparison.CurrentCultureIgnoreCase) >= 0;
                    });
                sectionTitle = L10n.T("search.title");
                sectionDescription = L10n.F("search.description", query);
                pageTitle.Text = sectionTitle;
                pageDescription.Text = sectionDescription;
            }
            else
            {
                CategoryDefinition category = BindingCatalog.FindCategory(selectedCategoryId);
                actions = BindingCatalog.Actions.Where(
                    delegate(ActionDefinition action) { return action.CategoryId == selectedCategoryId; });
                numericSettings = BindingCatalog.NumericSettings.Where(
                    delegate(NumericSettingDefinition setting)
                    {
                        return setting.CategoryId == selectedCategoryId;
                    });
                sectionTitle = category.Title;
                sectionDescription = category.Description;
                pageTitle.Text = sectionTitle;
                pageDescription.Text = sectionDescription;
            }

            var section = new Panel
            {
                Height = DpiHelper.S(52),
                BackColor = AppTheme.Surface,
                Margin = new Padding(0)
            };
            var sectionLabel = new Label
            {
                Text = sectionTitle,
                Font = AppTheme.Font(11.5f, FontStyle.Bold),
                ForeColor = AppTheme.Text,
                AutoSize = false,
                AutoEllipsis = true,
                Location = new Point(0, DpiHelper.S(8)),
                Height = DpiHelper.S(25)
            };
            int itemCount = actions.Count() + numericSettings.Count();
            var countLabel = new Label
            {
                Text = L10n.F(itemCount == 1 ? "section.count.one" : "section.count.many", itemCount),
                Font = AppTheme.Font(8.5f, FontStyle.Regular),
                ForeColor = AppTheme.MutedText,
                AutoSize = false,
                Location = new Point(0, DpiHelper.S(32)),
                Height = DpiHelper.S(18)
            };
            section.Controls.Add(sectionLabel);
            section.Controls.Add(countLabel);
            rowsPanel.Controls.Add(section);

            bool showAltTabToggle = query.Length == 0 && selectedCategoryId == "automation";
            if (showAltTabToggle)
            {
                var altTabToggle = new ToggleOptionRow(
                    L10n.T("alttab.title"),
                    L10n.T("alttab.description"))
                {
                    Checked = state.AltTabCenteringEnabled
                };
                altTabToggle.CheckedChanged += delegate
                {
                    if (suppressToggleEvents)
                        return;
                    state.AltTabCenteringEnabled = altTabToggle.Checked;
                    SetDirty(true);
                };
                rowsPanel.Controls.Add(altTabToggle);
            }

            foreach (NumericSettingDefinition setting in numericSettings)
            {
                NumericSettingDefinition currentSetting = setting;
                var row = new NumericOptionRow(
                    currentSetting,
                    state.GetNumericSetting(currentSetting.Id));
                row.ValueChanged += delegate
                {
                    state.SetNumericSetting(currentSetting.Id, row.Value);
                    SetDirty(true);
                    ShowStatus(
                        L10n.F("status.updated", currentSetting.Title),
                        AppTheme.Warning);
                };
                rowsPanel.Controls.Add(row);
            }

            foreach (ActionDefinition action in actions)
            {
                var row = new BindingRow(action);
                row.SetBindings(state.GetBindings(action.Id));
                row.EditRequested += OnEditBinding;
                row.ClearRequested += OnClearBinding;
                rowsPanel.Controls.Add(row);
            }

            if (!actions.Any() && !numericSettings.Any())
            {
                var empty = new Label
                {
                    Text = L10n.T("row.empty"),
                    Font = AppTheme.Font(10f, FontStyle.Regular),
                    ForeColor = AppTheme.MutedText,
                    TextAlign = ContentAlignment.MiddleCenter,
                    Height = DpiHelper.S(120),
                    Margin = new Padding(0)
                };
                rowsPanel.Controls.Add(empty);
            }

            ResizeRows();
            rowsPanel.ResumeLayout(true);
        }

        private void ResizeRows()
        {
            if (rowsPanel == null)
                return;
            int width = Math.Max(
                DpiHelper.S(520),
                rowsPanel.ClientSize.Width - rowsPanel.Padding.Horizontal - DpiHelper.S(20));
            foreach (Control control in rowsPanel.Controls)
                control.Width = width;
        }

        private void OnEditBinding(object sender, EventArgs e)
        {
            var row = sender as BindingRow;
            if (row == null)
                return;
            using (var dialog = new KeyCaptureDialog(row.Definition, state.GetBindings(row.Definition.Id)))
            {
                if (dialog.ShowDialog(this) != DialogResult.OK)
                    return;
                ApplyBindingEdit(row.Definition, dialog.Result);
            }
        }

        private void OnClearBinding(object sender, EventArgs e)
        {
            var row = sender as BindingRow;
            if (row == null)
                return;
            ApplyBindingEdit(row.Definition, new KeyChord[0]);
        }

        private void ApplyBindingEdit(ActionDefinition definition, IEnumerable<KeyChord> requested)
        {
            try
            {
                IList<ConflictChange> conflicts = ConflictResolver.ApplyEdit(state, definition, requested);
                SetDirty(true);
                RenderRows();
                if (conflicts.Count > 0)
                    ShowConflicts(definition, conflicts);
                else
                    ShowStatus(L10n.F("status.updated", definition.Title), AppTheme.Warning);
            }
            catch (Exception exception)
            {
                MessageBox.Show(
                    this,
                    exception.Message,
                    L10n.T("msg.noSet"),
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning);
            }
        }

        private void ShowConflicts(ActionDefinition current, IList<ConflictChange> changes)
        {
            conflictCurrent = current;
            conflictChanges = changes;
            string compact = BuildConflictText(current, changes);
            conflictLabel.Text = compact;
            conflictPanel.Visible = true;
            mainLayout.RowStyles[1].Height = DpiHelper.S(44);
            System.Media.SystemSounds.Exclamation.Play();
            MessageBox.Show(
                this,
                compact,
                L10n.T("msg.conflict"),
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            ShowStatus(L10n.T("status.conflict"), AppTheme.Warning);
        }

        private static string BuildConflictText(
            ActionDefinition current,
            IEnumerable<ConflictChange> changes)
        {
            var details = new List<string>();
            foreach (ConflictChange change in changes)
            {
                string removed = string.Join(
                    L10n.T("conflict.joinChords"),
                    change.Removed.Select(delegate(KeyChord chord) { return chord.DisplayText(); }).ToArray());
                details.Add(L10n.F("conflict.detail", change.Action.Title, removed));
            }
            return L10n.F(
                "conflict.message",
                current.Title,
                string.Join(L10n.T("conflict.joinDetails"), details.ToArray()));
        }

        private void HideConflictBanner()
        {
            conflictPanel.Visible = false;
            conflictCurrent = null;
            conflictChanges = null;
            mainLayout.RowStyles[1].Height = 0f;
        }

        private void LoadConfiguration(bool showSuccess)
        {
            try
            {
                if (!File.Exists(configurationPath))
                    throw new FileNotFoundException(L10n.T("error.noConfig"), configurationPath);
                loadedSourceText = File.ReadAllText(configurationPath);
                state = engine.LoadState(loadedSourceText);
                dirty = false;
                suppressToggleEvents = true;
                focusToggle.Checked = state.FocusModeEnabled;
                suppressToggleEvents = false;
                UpdateFocusAppearance();
                saveButton.Enabled = true;
                RenderRows();
                HideConflictBanner();
                ShowStatus(
                    showSuccess ? L10n.T("status.reloaded") : L10n.T("status.ready"),
                    AppTheme.Success);
            }
            catch (Exception exception)
            {
                saveButton.Enabled = false;
                ShowStatus(L10n.F("status.loadFail", exception.Message), AppTheme.Danger);
                MessageBox.Show(
                    this,
                    exception.Message,
                    L10n.T("msg.readFail"),
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
        }

        private void SaveConfiguration()
        {
            if (state == null)
                return;
            try
            {
                string latest = File.ReadAllText(configurationPath);
                if (!string.Equals(latest, loadedSourceText, StringComparison.Ordinal))
                {
                    DialogResult result = MessageBox.Show(
                        this,
                        L10n.T("msg.external.body"),
                        L10n.T("msg.external"),
                        MessageBoxButtons.YesNo,
                        MessageBoxIcon.Question);
                    if (result != DialogResult.Yes)
                        return;
                }

                string output = engine.Apply(latest, state);
                AtomicFile.WriteAllText(configurationPath, output, true);
                loadedSourceText = output;
                state = engine.LoadState(output);
                dirty = false;
                suppressToggleEvents = true;
                focusToggle.Checked = state.FocusModeEnabled;
                suppressToggleEvents = false;
                UpdateFocusAppearance();
                RenderRows();
                HideConflictBanner();
                ShowStatus(L10n.T("status.saved"), AppTheme.Success);
            }
            catch (Exception exception)
            {
                ShowStatus(L10n.F("status.saveFail", exception.Message), AppTheme.Danger);
                MessageBox.Show(
                    this,
                    exception.Message,
                    L10n.T("msg.saveFail"),
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
        }

        private void ReloadRequested()
        {
            if (dirty)
            {
                DialogResult result = MessageBox.Show(
                    this,
                    L10n.T("msg.discard.body"),
                    L10n.T("msg.discard"),
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Question);
                if (result != DialogResult.Yes)
                    return;
            }
            LoadConfiguration(true);
        }

        private void RestoreDefault()
        {
            DialogResult result = MessageBox.Show(
                this,
                L10n.T("msg.restore.body"),
                L10n.T("msg.restore"),
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Warning);
            if (result != DialogResult.Yes)
                return;

            try
            {
                engine.RestoreDefault(configurationPath);
                LoadConfiguration(false);
                ShowStatus(L10n.T("status.restored"), AppTheme.Success);
            }
            catch (Exception exception)
            {
                ShowStatus(L10n.F("status.restoreFail", exception.Message), AppTheme.Danger);
                MessageBox.Show(
                    this,
                    exception.Message,
                    L10n.T("msg.restoreFail"),
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
        }

        private void SetDirty(bool value)
        {
            dirty = value;
            if (value)
                ShowStatus(L10n.T("status.dirty"), AppTheme.Warning);
        }

        private void ShowStatus(string message, Color color)
        {
            statusMessage = message;
            statusColor = color;
            if (statusLabel == null)
                return;
            statusLabel.Text = message;
            statusLabel.ForeColor = color;
        }

        private void UpdateFocusAppearance()
        {
            bool enabled = state != null && state.FocusModeEnabled;
            focusTitle.ForeColor = enabled ? AppTheme.Success : AppTheme.Text;
            focusDescription.ForeColor = enabled ? AppTheme.Success : AppTheme.MutedText;
            focusDescription.Text = L10n.T(enabled ? "focus.on" : "focus.off");
        }

        private void OnFormClosing(object sender, FormClosingEventArgs e)
        {
            if (!dirty)
                return;
            DialogResult result = MessageBox.Show(
                this,
                L10n.T("msg.unsaved.body"),
                L10n.T("msg.unsaved"),
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question);
            if (result != DialogResult.Yes)
                e.Cancel = true;
        }
    }

    internal sealed class LogoControl : Control
    {
        public LogoControl()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.ResizeRedraw |
                ControlStyles.UserPaint,
                true);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            Rectangle bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using (System.Drawing.Drawing2D.GraphicsPath path =
                RoundedButton.RoundedRectangle(bounds, DpiHelper.S(7)))
            using (var background = new SolidBrush(AppTheme.Accent))
                e.Graphics.FillPath(background, path);

            using (var pen = new Pen(Color.White, 1.8f * DpiHelper.Factor))
            {
                int centerX = Width / 2;
                int centerY = Height / 2;
                e.Graphics.DrawEllipse(
                    pen,
                    centerX - DpiHelper.S(6),
                    centerY - DpiHelper.S(6),
                    DpiHelper.S(12),
                    DpiHelper.S(12));
                e.Graphics.DrawLine(pen, centerX, DpiHelper.S(7), centerX, DpiHelper.S(13));
                e.Graphics.DrawLine(pen, centerX, Height - DpiHelper.S(7), centerX, Height - DpiHelper.S(13));
                e.Graphics.DrawLine(pen, DpiHelper.S(7), centerY, DpiHelper.S(13), centerY);
                e.Graphics.DrawLine(pen, Width - DpiHelper.S(7), centerY, Width - DpiHelper.S(13), centerY);
            }
        }
    }
}
