using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Windows.Forms;

namespace MouseMasterConfigurator
{
    internal static class SelfTests
    {
        private static readonly List<string> Messages = new List<string>();
        private static int assertionCount;

        public static int Run(out string report, out string acceptanceConfiguration)
        {
            Messages.Clear();
            assertionCount = 0;
            acceptanceConfiguration = null;
            // Never persist language choices made by the headless UI tests.
            L10n.PersistenceEnabled = false;
            try
            {
                acceptanceConfiguration = RunAll();
                Messages.Add("PASS: " + assertionCount + " assertions");
                report = string.Join(Environment.NewLine, Messages.ToArray()) + Environment.NewLine;
                return 0;
            }
            catch (Exception exception)
            {
                Messages.Add("FAIL: " + exception);
                report = string.Join(Environment.NewLine, Messages.ToArray()) + Environment.NewLine;
                return 1;
            }
        }

        private static string RunAll()
        {
            string defaultText = EmbeddedDefault.ReadText();
            Assert(defaultText.Contains("key-alias.up.us-qwerty=i"), "embedded default is the expected IJKL profile");
            Assert(BindingCatalog.Actions.Count >= 36, "catalog exposes all documented operation groups");
            Assert(BindingCatalog.NumericSettings.Count == 4, "catalog exposes four motion controls");
            Assert(
                KeyCaptureMap.ResolveModifiers(
                    Keys.Control | Keys.M,
                    new string[0],
                    null).SequenceEqual(new[] { "leftctrl" }, StringComparer.OrdinalIgnoreCase),
                "keyData preserves Ctrl when asynchronous modifier state is already clear");
            Assert(
                KeyCaptureMap.SpecificModifier(Keys.ControlKey) == "leftctrl" &&
                KeyCaptureMap.SpecificModifier(Keys.Menu) == "leftalt" &&
                KeyCaptureMap.SpecificModifier(Keys.ShiftKey) == "leftshift",
                "generic modifier fallbacks preserve the modifier family");
            Assert(
                KeyChord.Parse("leftctrl+m").ToString() == "Left Ctrl + M",
                "key chords expose readable text to accessibility clients");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.Delete) == "del",
                "Delete uses the MouseMaster v89 key name");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.Apps) == "menu",
                "the application menu key uses the MouseMaster v89 key name");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.Oemplus) == "=",
                "the unshifted equals key uses its layout character");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.OemOpenBrackets) == "[",
                "the open bracket key uses its layout character");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.OemCloseBrackets) == "]",
                "the close bracket key uses its layout character");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.Oemtilde) == "`",
                "the backtick key uses its layout character");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.OemMinus) == "minus" &&
                KeyCaptureMap.ToMouseMasterKey(Keys.OemPipe) == "backslash",
                "reserved combo characters keep their MouseMaster static names");
            Assert(
                KeyCaptureMap.ToMouseMasterKey(Keys.F24) == null,
                "F24 is reserved for cleared bindings");
            VerifyVisualLayout(defaultText);

            ConfiguratorState state = ConfiguratorState.CreateDefaults();
            var conflicts = new List<ConflictChange>();
            conflicts.AddRange(Edit(state, "activate", "leftctrl+m"));
            conflicts.AddRange(Edit(state, "moveup", "k"));
            conflicts.AddRange(Edit(state, "movedown", "j"));
            conflicts.AddRange(Edit(state, "moveleft", "h"));
            conflicts.AddRange(Edit(state, "moveright", "l"));
            conflicts.AddRange(Edit(state, "wheelup", "u"));
            conflicts.AddRange(Edit(state, "wheeldown", "o"));
            conflicts.AddRange(Edit(state, "wheelleft", "leftshift+u"));
            conflicts.AddRange(Edit(state, "wheelright", "leftshift+o"));
            state.FocusModeEnabled = true;
            state.AltTabCenteringEnabled = true;
            state.MouseMaxVelocity = 4321;
            state.MouseAcceleration = 5432;
            state.WheelMaxVelocity = 6543;
            state.WheelAcceleration = 765;

            Assert(state.GetBindings("navigateback").Count == 0, "Vim H clears the old Navigate Back binding");
            Assert(conflicts.Any(delegate(ConflictChange change) { return change.Action.Id == "navigateback"; }),
                "conflict report includes Navigate Back");
            Assert(conflicts.Any(delegate(ConflictChange change) { return change.Action.Id == "wheelleft"; }),
                "U vertical scrolling clears the former U horizontal binding");
            Assert(conflicts.Any(delegate(ConflictChange change) { return change.Action.Id == "wheelright"; }),
                "O vertical scrolling clears the former O horizontal binding");
            Assert(
                state.GetBindings("wheelleft").Single().Equals(KeyChord.Parse("leftshift+u")),
                "Shift+U remains after exact-chord conflict resolution");

            var engine = new ConfiguratorEngine();
            string motionSource = defaultText
                .Replace(
                    "normal-mode.mouse.max-velocity=2200 |",
                    "normal-mode.mouse.max-velocity=2345 |")
                .Replace(
                    "normal-mode.mouse.acceleration=3000 |",
                    "normal-mode.mouse.acceleration=3456 |")
                .Replace(
                    "normal-mode.wheel.max-velocity=2000 |",
                    "normal-mode.wheel.max-velocity=4567 |")
                .Replace(
                    "normal-mode.wheel.acceleration=500 |",
                    "normal-mode.wheel.acceleration=678 |");
            ConfiguratorState importedMotion = engine.LoadState(motionSource);
            Assert(
                importedMotion.MouseMaxVelocity == 2345 &&
                importedMotion.MouseAcceleration == 3456 &&
                importedMotion.WheelMaxVelocity == 4567 &&
                importedMotion.WheelAcceleration == 678,
                "motion settings import the default branch from existing properties");
            ConfiguratorState invalidMotion = ConfiguratorState.CreateDefaults();
            invalidMotion.MouseMaxVelocity = 0;
            bool invalidMotionRejected = false;
            try
            {
                engine.Apply(defaultText, invalidMotion);
            }
            catch (InvalidOperationException)
            {
                invalidMotionRejected = true;
            }
            Assert(invalidMotionRejected, "motion settings reject values outside the supported range");

            ConfiguratorState numpadState = ConfiguratorState.CreateDefaults();
            numpadState.SetBindings("navigateback", KeyChord.ParseList("numpad0"));
            string numpadOutput = engine.Apply(defaultText, numpadState);
            Assert(
                numpadOutput.Contains("key-alias.navigateback=numpad0") &&
                !numpadOutput.Contains("key-alias.navigateback.us-qwerty=numpad0"),
                "numpad bindings use a layout-independent alias");

            ConfiguratorState swappedHints = ConfiguratorState.CreateDefaults();
            swappedHints.FocusModeEnabled = true;
            Edit(swappedHints, "hintmode", "leftctrl+f");
            Edit(swappedHints, "uihintmode", "f");
            string swappedOutput = engine.Apply(defaultText, swappedHints);
            Assert(swappedOutput.Contains("normal-mode.to.hint1-mode=_{leftctrl} +f"),
                "Ctrl+F screen-hint entry keeps its pressed-modifier precondition");
            Assert(swappedOutput.Contains("normal-mode.to.ui-hint-mode=^{leftctrl} +f"),
                "plain F UI-hint entry excludes the screen-hint modifier");
            Assert(!swappedOutput.Contains("normal-mode.to.ui-hint-mode=+f"),
                "plain F UI-hint entry no longer fires for Ctrl+F");
            string swappedFocusKeys = ConfigDocument.Parse(swappedOutput)
                .GetPropertyValue("key-alias.mmcfgfocusnormalkey");
            Assert(
                !ContainsKey(swappedFocusKeys, "leftctrl") && ContainsKey(swappedFocusKeys, "f"),
                "focus fallback spares the hint modifier but still swallows plain F");

            ConfiguratorState altTabOffState = ConfiguratorState.CreateDefaults();
            altTabOffState.AltTabCenteringEnabled = false;
            string altTabOff = engine.Apply(defaultText, altTabOffState);
            string[] altTabModeProperties =
            {
                "idle-mode.to.begin-alt-tab-mode",
                "normal-mode.to.begin-alt-tab-mode",
                "begin-alt-tab-mode.to.end-alt-tab-mode",
                "end-alt-tab-mode.to.center-on-active-window-mode",
                "end-alt-tab-mode.to.previous-mode-from-history-stack",
                "center-on-active-window-mode.grid.area",
                "center-on-active-window-mode.grid.synchronization",
                "center-on-active-window-mode.to.previous-mode-from-history-stack"
            };
            Assert(
                altTabModeProperties.All(delegate(string property)
                {
                    return altTabOff.Contains(ConfigDocument.DisabledPrefix + property + "=");
                }),
                "disabling Alt-Tab centering disables the complete referenced mode chain");
            altTabOffState.AltTabCenteringEnabled = true;
            string altTabRestored = engine.Apply(altTabOff, altTabOffState);
            Assert(
                altTabModeProperties.All(delegate(string property)
                {
                    return ConfigDocument.Parse(altTabRestored).HasActiveProperty(property);
                }),
                "re-enabling Alt-Tab centering restores the complete mode chain");

            string output = engine.Apply(defaultText, state);
            Assert(output.Contains("key-alias.up.us-qwerty=k"), "Vim up mapping is written");
            Assert(output.Contains("key-alias.down.us-qwerty=j"), "Vim down mapping is written");
            Assert(output.Contains("key-alias.left.us-qwerty=h"), "Vim left mapping is written");
            Assert(output.Contains("key-alias.right.us-qwerty=l"), "Vim right mapping is written");
            Assert(output.Contains("key-alias.enablemod.us-qwerty=leftctrl"), "activation modifier is Ctrl only");
            Assert(output.Contains("key-alias.enablekey.us-qwerty=m"), "activation key is M only");
            Assert(output.Contains("idle-mode.to.normal-mode=+leftctrl +m | _{leftctrl} +m"),
                "activation combo has no redundant alternative");
            Assert(output.Contains("normal-mode.start-wheel.up=^{leftshift} +u"),
                "vertical wheel up yields to the Shift+U horizontal binding");
            Assert(output.Contains("normal-mode.start-wheel.down=^{leftshift} +o"),
                "vertical wheel down yields to the Shift+O horizontal binding");
            Assert(output.Contains("normal-mode.start-wheel.left=_{leftshift} +u"),
                "horizontal wheel left is Shift+U");
            Assert(output.Contains("normal-mode.stop-wheel.left=-u | _{u} -leftshift"),
                "horizontal wheel stops when either key is released");
            Assert(output.Contains("normal-mode.start-wheel.right=_{leftshift} +o"),
                "horizontal wheel right is Shift+O");
            Assert(output.Contains("normal-mode.to.hint1-mode=^{leftctrl leftalt} +f"),
                "plain F screen hints yield to Ctrl+F pass-through and Alt+F UI hints");
            Assert(output.Contains("normal-mode.to.ui-hint-mode=_{leftalt} +f"),
                "modified UI hint entry keeps its pressed-modifier precondition");
            Assert(
                output.Contains(
                    "normal-mode.mouse.max-velocity=4321 | _{slow} ^{superslow} -> 350 | _{fast} -> 4500 | _{superslow} -> 75"),
                "mouse maximum speed changes without replacing modifier branches");
            Assert(
                output.Contains("normal-mode.mouse.acceleration=5432 | _{fast} -> 5000"),
                "mouse acceleration changes without replacing the fast branch");
            Assert(
                output.Contains(
                    "normal-mode.wheel.max-velocity=6543 | _{slow} ^{superslow} -> 200 | _{fast} -> 10000 | _{superslow} -> 50"),
                "wheel maximum speed changes without replacing modifier branches");
            Assert(
                output.Contains("normal-mode.wheel.acceleration=765 | _{fast} -> 10000"),
                "wheel acceleration changes without replacing the fast branch");
            Assert(
                output.Contains("normal-mode.mouse.initial-velocity=1000") &&
                output.Contains("normal-mode.wheel.initial-velocity=1500"),
                "motion controls leave initial velocities unchanged");
            Assert(
                output.Contains(
                    "normal-mode.press.left=_{none | leftctrl} +; | _{none | leftctrl} +."),
                "Ctrl-click preconditions remain intact when mouse bindings are combined");
            Assert(CountOccurrences(output, ".noop.mmcfg-focus=+mmcfgfocus") == 8,
                "focus mode covers all eight keyboard-mouse modes");
            Assert(
                CountOccurrences(output, "key-alias.mmcfgfocus") == 8 &&
                !output.Contains("mmcfgfocusnormalkey.us-qwerty"),
                "focus mode defines layout-independent per-mode fallback aliases");
            ConfigDocument focusDocument = ConfigDocument.Parse(output);
            string normalFocusKeys =
                focusDocument.GetPropertyValue("key-alias.mmcfgfocusnormalkey");
            string edgeFocusKeys =
                focusDocument.GetPropertyValue("key-alias.mmcfgfocusedgekey");
            Assert(
                ContainsKey(normalFocusKeys, "s") &&
                !ContainsKey(normalFocusKeys, "f2") &&
                !ContainsKey(normalFocusKeys, "f3") &&
                !ContainsKey(normalFocusKeys, "/"),
                "normal focus fallback excludes configured non-eating exit keys");
            Assert(
                ContainsKey(edgeFocusKeys, "f2"),
                "pass-through exclusions are scoped to the mode that handles them");
            Assert(output.Contains("# mmcfg-disabled: normal-mode.macro=_arrowremapping-mode.macro"),
                "focus mode disables OS-bound arrow remapping in normal mode");
            Assert(output.Contains("# mmcfg-disabled: normal-mode.to.begin-alt-tab-mode="),
                "focus mode disables normal-mode Alt-Tab handling");
            Assert(output.Contains("# mmcfg.binding.activate=leftctrl+m"),
                "GUI state metadata is embedded in the properties file");
            Assert(
                output.Contains("# mmcfg.mouse.max-velocity=4321") &&
                output.Contains("# mmcfg.mouse.acceleration=5432") &&
                output.Contains("# mmcfg.wheel.max-velocity=6543") &&
                output.Contains("# mmcfg.wheel.acceleration=765"),
                "motion settings are embedded in GUI metadata");
            Assert(
                output.Contains("key-alias.navigateback.us-qwerty=f24"),
                "cleared aliases use the MouseMaster v89-compatible F24 placeholder");
            Assert(
                !output.Contains("mmcfgunbound") && !output.Contains("virtual-keys="),
                "generated configuration does not use unsupported virtual keys");

            ConfigDocument document = ConfigDocument.Parse(output);
            Assert(document.DuplicateActivePropertyKeys().Count == 0, "generated properties are unique");
            Assert(document.InvalidActivePropertyLines().Count == 0, "generated properties are non-empty");
            VerifyEveryCatalogActionAffectsConfiguration(defaultText);

            ConfiguratorState reloaded = engine.LoadState(output);
            Assert(reloaded.FocusModeEnabled, "focus setting reloads from metadata");
            Assert(reloaded.GetBindings("activate").Single().Equals(KeyChord.Parse("leftctrl+m")),
                "activation setting reloads from metadata");
            Assert(reloaded.GetBindings("navigateback").Count == 0,
                "cleared conflict reloads as unbound");
            Assert(
                reloaded.MouseMaxVelocity == 4321 &&
                reloaded.MouseAcceleration == 5432 &&
                reloaded.WheelMaxVelocity == 6543 &&
                reloaded.WheelAcceleration == 765,
                "motion settings reload from saved configuration");

            reloaded.FocusModeEnabled = false;
            string focusOff = engine.Apply(output, reloaded);
            Assert(!focusOff.Contains(".noop.mmcfg-focus=+mmcfgfocus"),
                "focus no-op properties are removed when disabled");
            Assert(!focusOff.Contains("key-alias.mmcfgfocus"),
                "focus key aliases are removed when focus mode is disabled");
            Assert(focusOff.Contains("normal-mode.macro=_arrowremapping-mode.macro"),
                "normal arrow mapping is restored when focus mode is disabled");
            Assert(focusOff.Contains("normal-mode.to.begin-alt-tab-mode="),
                "normal Alt-Tab listener is restored when focus mode is disabled");

            VerifyAtomicRestore(engine, defaultText);
            return output;
        }

        private static void VerifyEveryCatalogActionAffectsConfiguration(string defaultText)
        {
            var engine = new ConfiguratorEngine();
            string baseline = WithoutMetadata(
                engine.Apply(defaultText, ConfiguratorState.CreateDefaults()));

            foreach (ActionDefinition action in BindingCatalog.Actions)
            {
                ConfiguratorState changedState = ConfiguratorState.CreateDefaults();
                string replacement =
                    action.Id == "activate" ||
                    action.Id == "windowmode" ||
                    action.Id == "uihintmode" ||
                    action.Id == "alttab"
                        ? "leftctrl+f24"
                        : "f24";
                changedState.SetBindings(action.Id, KeyChord.ParseList(replacement));
                string changed = WithoutMetadata(engine.Apply(defaultText, changedState));
                Assert(
                    !string.Equals(changed, baseline, StringComparison.Ordinal),
                    "catalog action writes behavior: " + action.Id);
            }
        }

        private static string WithoutMetadata(string text)
        {
            ConfigDocument document = ConfigDocument.Parse(text);
            document.RemoveMetadata();
            return document.GetText();
        }

        private static IList<ConflictChange> Edit(
            ConfiguratorState state,
            string actionId,
            string serialized)
        {
            return ConflictResolver.ApplyEdit(
                state,
                BindingCatalog.FindAction(actionId),
                KeyChord.ParseList(serialized));
        }

        private static void VerifyAtomicRestore(ConfiguratorEngine engine, string defaultText)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "MouseMasterConfiguratorSelfTest-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(directory);
            string path = Path.Combine(directory, "mousemaster.properties");
            try
            {
                File.WriteAllText(path, "sentinel=value" + Environment.NewLine, new UTF8Encoding(false));
                engine.RestoreDefault(path);
                string restored = File.ReadAllText(path);
                Assert(restored == defaultText, "restore writes the embedded default byte-for-text");
                Assert(File.Exists(path + ".gui-backup"), "restore creates a rolling GUI backup");
                Assert(File.ReadAllText(path + ".gui-backup").Contains("sentinel=value"),
                    "GUI backup contains the pre-restore configuration");
            }
            finally
            {
                if (File.Exists(path))
                    File.Delete(path);
                if (File.Exists(path + ".gui-backup"))
                    File.Delete(path + ".gui-backup");
                if (Directory.Exists(directory))
                    Directory.Delete(directory);
            }
        }

        private static void VerifyVisualLayout(string defaultText)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "MouseMasterConfiguratorLayoutTest-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(directory);
            string path = Path.Combine(directory, "mousemaster.properties");
            UiLanguage originalLanguage = L10n.Language;
            try
            {
                File.WriteAllText(path, defaultText, new UTF8Encoding(false));
                L10n.Language = UiLanguage.Chinese;
                using (var form = new MainForm(path))
                {
                    form.CreateControl();
                    PerformLayoutRecursively(form);
                    VerifyHeaderLayout(form, "zh");
                    VerifyMovementPage(form, "zh");
                    VerifyMousePage(form, "zh");
                    AssertLabelsFit(form, "zh");

                    ComboBox languageCombo = FindDescendant<ComboBox>(
                        form,
                        delegate(ComboBox combo) { return true; });
                    Assert(
                        languageCombo != null && languageCombo.Items.Count == 2,
                        "sidebar offers a Chinese/English language switcher");
                    languageCombo.SelectedIndex = 1;
                    PerformLayoutRecursively(form);
                    Assert(
                        L10n.Language == UiLanguage.English,
                        "the language switcher activates English");
                    Assert(
                        form.Text == L10n.T("app.title"),
                        "language switch updates the window title");

                    NavigationButton generalButton = FindDescendant<NavigationButton>(
                        form,
                        delegate(NavigationButton button) { return (string)button.Tag == "general"; });
                    SelectCategory(form, generalButton);
                    PerformLayoutRecursively(form);
                    VerifyHeaderLayout(form, "en");
                    VerifyMovementPage(form, "en");
                    VerifyMousePage(form, "en");
                    AssertLabelsFit(form, "en");
                }
            }
            finally
            {
                L10n.Language = originalLanguage;
                if (File.Exists(path))
                    File.Delete(path);
                if (Directory.Exists(directory))
                    Directory.Delete(directory);
            }
        }

        private static void VerifyHeaderLayout(MainForm form, string languageTag)
        {
            Label pageTitle = FindDescendant<Label>(
                form,
                delegate(Label label) { return label.Name == "pageTitle"; });
            Label pageDescription = FindDescendant<Label>(
                form,
                delegate(Label label) { return label.Name == "pageDescription"; });
            Label focusDescription = FindDescendant<Label>(
                form,
                delegate(Label label) { return label.Name == "focusDescription"; });
            ToggleSwitch focusToggle = FindDescendant<ToggleSwitch>(
                form,
                delegate(ToggleSwitch toggle) { return true; });

            Assert(
                pageTitle != null && pageTitle.Text == L10n.T("category.general.title"),
                "page title shows the general category (" + languageTag + ")");
            Assert(
                pageDescription != null &&
                pageDescription.Text == L10n.T("category.general.description"),
                "page description shows the general category (" + languageTag + ")");
            Assert(
                pageTitle != null &&
                pageDescription != null &&
                pageDescription.Top - pageTitle.Bottom >= 5,
                "page title and description have a visible vertical gap (" + languageTag + ")");
            Assert(
                focusDescription != null &&
                focusToggle != null &&
                focusDescription.Text == L10n.T("focus.off"),
                "focus description shows the disabled state (" + languageTag + ")");
            Assert(
                focusDescription != null &&
                focusToggle != null &&
                focusDescription.Right + 10 <= focusToggle.Left,
                "focus description ends before the toggle switch (" + languageTag + ")");

            MethodInfo getStyle = typeof(Control).GetMethod(
                "GetStyle",
                BindingFlags.Instance | BindingFlags.NonPublic);
            bool paintsOpaque = getStyle != null && focusToggle != null &&
                (bool)getStyle.Invoke(focusToggle, new object[] { ControlStyles.Opaque });
            Assert(
                paintsOpaque && focusToggle.BackColor == AppTheme.Surface,
                "toggle switch paints an opaque surface background (" + languageTag + ")");
        }

        private static void VerifyMovementPage(MainForm form, string languageTag)
        {
            NavigationButton movementButton = FindDescendant<NavigationButton>(
                form,
                delegate(NavigationButton button) { return (string)button.Tag == "movement"; });
            Assert(
                movementButton != null && movementButton.Text == L10n.T("category.movement.title"),
                "movement navigation shows the localized title (" + languageTag + ")");
            SelectCategory(form, movementButton);
            PerformLayoutRecursively(form);
            IList<NumericUpDown> movementInputs = FindDescendants<NumericUpDown>(form);
            Assert(
                movementInputs.Count == 2 &&
                movementInputs.Any(delegate(NumericUpDown input) { return input.Value == 2200; }) &&
                movementInputs.Any(delegate(NumericUpDown input) { return input.Value == 3000; }),
                "movement page exposes mouse speed and acceleration inputs (found " +
                movementInputs.Count + ": " +
                string.Join(
                    ", ",
                    movementInputs.Select(
                        delegate(NumericUpDown input) { return input.Value.ToString(); }).ToArray()) +
                ")");
            AssertLabelsFit(form, languageTag);
        }

        private static void VerifyMousePage(MainForm form, string languageTag)
        {
            NavigationButton mouseButton = FindDescendant<NavigationButton>(
                form,
                delegate(NavigationButton button) { return (string)button.Tag == "mouse"; });
            Assert(
                mouseButton != null && mouseButton.Text == L10n.T("category.mouse.title"),
                "mouse navigation shows the localized title (" + languageTag + ")");
            SelectCategory(form, mouseButton);
            PerformLayoutRecursively(form);
            IList<NumericUpDown> wheelInputs = FindDescendants<NumericUpDown>(form);
            Assert(
                wheelInputs.Count == 2 &&
                wheelInputs.Any(delegate(NumericUpDown input) { return input.Value == 2000; }) &&
                wheelInputs.Any(delegate(NumericUpDown input) { return input.Value == 500; }),
                "mouse page exposes wheel speed and acceleration inputs");
            AssertLabelsFit(form, languageTag);
        }

        /// <summary>
        /// Guards the DPI-clipping regression: every fixed-size label without
        /// ellipsis must be large enough for its text, single-line or wrapped.
        /// </summary>
        private static void AssertLabelsFit(Control root, string languageTag)
        {
            foreach (Control control in AllDescendants(root))
            {
                Label label = control as Label;
                if (label == null || label.AutoSize || label.AutoEllipsis || label.Text.Length == 0)
                    continue;
                Size singleLine = TextRenderer.MeasureText(
                    label.Text,
                    label.Font,
                    new Size(int.MaxValue, int.MaxValue),
                    TextFormatFlags.NoPadding |
                    TextFormatFlags.SingleLine |
                    TextFormatFlags.NoPrefix);
                bool fits = singleLine.Width <= label.Width + 6 &&
                            singleLine.Height <= label.Height + 6;
                if (!fits)
                {
                    Size wrapped = TextRenderer.MeasureText(
                        label.Text,
                        label.Font,
                        new Size(Math.Max(1, label.Width), int.MaxValue),
                        TextFormatFlags.NoPadding |
                        TextFormatFlags.WordBreak |
                        TextFormatFlags.NoPrefix);
                    fits = wrapped.Height <= label.Height + 6;
                }
                string text = label.Text.Length <= 40 ? label.Text : label.Text.Substring(0, 40) + "…";
                Assert(
                    fits,
                    "label text fits its bounds (" + languageTag + "): \"" + text + "\" needs " +
                    singleLine.Width + "x" + singleLine.Height + " but has " +
                    label.Width + "x" + label.Height);
            }
        }

        private static IEnumerable<Control> AllDescendants(Control root)
        {
            var result = new List<Control>();
            var stack = new Stack<Control>();
            stack.Push(root);
            while (stack.Count > 0)
            {
                Control current = stack.Pop();
                foreach (Control child in current.Controls)
                {
                    result.Add(child);
                    stack.Push(child);
                }
            }
            return result;
        }

        private static T FindDescendant<T>(Control root, Predicate<T> predicate)
            where T : Control
        {
            foreach (Control child in root.Controls)
            {
                T match = child as T;
                if (match != null && predicate(match))
                    return match;
                match = FindDescendant(child, predicate);
                if (match != null)
                    return match;
            }
            return null;
        }

        private static IList<T> FindDescendants<T>(Control root)
            where T : Control
        {
            var result = new List<T>();
            foreach (Control child in root.Controls)
            {
                T match = child as T;
                if (match != null)
                    result.Add(match);
                result.AddRange(FindDescendants<T>(child));
            }
            return result;
        }

        private static void SelectCategory(MainForm form, NavigationButton button)
        {
            MethodInfo handler = typeof(MainForm).GetMethod(
                "OnCategoryClick",
                BindingFlags.Instance | BindingFlags.NonPublic);
            handler.Invoke(form, new object[] { button, EventArgs.Empty });
        }

        private static void PerformLayoutRecursively(Control root)
        {
            root.PerformLayout();
            foreach (Control child in root.Controls)
                PerformLayoutRecursively(child);
        }

        private static int CountOccurrences(string value, string needle)
        {
            int count = 0;
            int index = 0;
            while ((index = value.IndexOf(needle, index, StringComparison.Ordinal)) >= 0)
            {
                count++;
                index += needle.Length;
            }
            return count;
        }

        private static bool ContainsKey(string value, string key)
        {
            return (value ?? string.Empty).Split(
                new[] { ' ', '\t' },
                StringSplitOptions.RemoveEmptyEntries).Contains(
                    key,
                    StringComparer.OrdinalIgnoreCase);
        }

        private static void Assert(bool condition, string message)
        {
            assertionCount++;
            if (!condition)
                throw new InvalidOperationException("Assertion failed: " + message);
            Messages.Add("OK: " + message);
        }
    }
}
