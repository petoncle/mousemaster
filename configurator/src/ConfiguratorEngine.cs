using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;

namespace MouseMasterConfigurator
{
    internal sealed class ConfiguratorEngine
    {
        private const string LegacyFocusKeyAliasProperty = "key-alias.mmcfgfocuskey";
        private const string FocusKeyAliasPrefix = "key-alias.mmcfgfocus";

        private static readonly string[] AltTabCenteringProperties =
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

        private static readonly string[] FocusModes =
        {
            "normal-mode",
            "edge-mode",
            "grid-mode",
            "window-mode",
            "hint1-mode",
            "hint2-mode",
            "screen-selection-mode",
            "ui-hint-mode"
        };

        private static readonly string[] FocusKeys =
        {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            ",", ".", ";", "'", "/", "-", "=", "[", "]", "\\", "`",
            "tab", "enter", "leftshift", "leftctrl", "leftalt",
            "rightshift", "rightctrl", "rightalt", "pause", "scrolllock",
            "capslock", "esc", "space", "pageup", "pagedown", "end", "home",
            "leftarrow", "uparrow", "rightarrow", "downarrow", "printscreen",
            "insert", "del", "break", "backspace", "leftwin", "rightwin", "menu",
            "numpad0", "numpad1", "numpad2", "numpad3", "numpad4",
            "numpad5", "numpad6", "numpad7", "numpad8", "numpad9",
            "numpadmultiply", "numpadadd", "numpadsubtract", "numpaddecimal",
            "numpaddivide", "numlock",
            "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10",
            "f11", "f12", "f13", "f14", "f15", "f16", "f17", "f18", "f19",
            "f20", "f21", "f22", "f23", "f24"
        };

        private static readonly HashSet<string> FocusKeySet =
            new HashSet<string>(FocusKeys, StringComparer.OrdinalIgnoreCase);

        /// <summary>
        /// Actions whose bindings never produce a combo in normal-mode
        /// (hint selection key lists and hint-mode internals). Their chords
        /// must not contribute modifier exclusions to normal-mode combos.
        /// </summary>
        private static readonly HashSet<string> NonNormalModeActions =
            new HashSet<string>(StringComparer.Ordinal)
            {
                "hint1keys",
                "hint2keys",
                "extendedhint1keys",
                "extendedhint2keys",
                "screenhintkeys",
                "hint2modifier",
                "cancel",
                "hintback"
            };

        public ConfiguratorState LoadState(string text)
        {
            ConfigDocument document = ConfigDocument.Parse(text);
            IDictionary<string, string> metadata = document.ReadMetadata();
            ConfiguratorState state = ConfiguratorState.CreateDefaults();
            ImportMotionSettings(document, metadata, state);

            if (metadata.Count > 0 && metadata.ContainsKey("mmcfg.version"))
            {
                string focus;
                if (metadata.TryGetValue("mmcfg.focus", out focus))
                    state.FocusModeEnabled = ParseBoolean(focus);
                string altTab;
                if (metadata.TryGetValue("mmcfg.alttab", out altTab))
                    state.AltTabCenteringEnabled = ParseBoolean(altTab);

                foreach (ActionDefinition action in BindingCatalog.Actions)
                {
                    string serialized;
                    if (metadata.TryGetValue("mmcfg.binding." + action.Id, out serialized))
                        state.SetBindings(action.Id, KeyChord.ParseList(serialized));
                }
                return state;
            }

            ImportAliases(document, state);
            state.FocusModeEnabled = document.HasActiveProperty("normal-mode.noop.mmcfg-focus");
            state.AltTabCenteringEnabled =
                document.HasActiveProperty("idle-mode.to.begin-alt-tab-mode");
            return state;
        }

        public string Apply(string sourceText, ConfiguratorState state)
        {
            Validate(state);

            ConfigDocument document = ConfigDocument.Parse(sourceText);
            document.RestoreManagedComments();
            document.RemoveMetadata();
            foreach (string mode in FocusModes)
            {
                document.RemoveProperty(mode + ".noop.mmcfg-focus");
                document.RemoveProperty(FocusAliasProperty(mode));
                document.RemoveProperty(FocusAliasProperty(mode) + ".us-qwerty");
            }
            document.RemoveProperty(LegacyFocusKeyAliasProperty);
            document.RemoveProperty(LegacyFocusKeyAliasProperty + ".us-qwerty");

            UpdateAliases(document, state);
            UpdateActivationAndExit(document, state);
            UpdateMouseButtons(document, state);
            UpdateWheel(document, state);
            UpdateMotionSettings(document, state);
            UpdateModes(document, state);
            UpdateAutomation(document, state);
            UpdateVirtualKeys(document);
            ApplyFeatureToggles(document, state);
            AppendMetadata(document, state);

            IList<string> duplicates = document.DuplicateActivePropertyKeys();
            if (duplicates.Count > 0)
                throw new InvalidOperationException(
                    "Configuration contains duplicate properties: " + string.Join(", ", duplicates.ToArray()));
            IList<string> invalid = document.InvalidActivePropertyLines();
            if (invalid.Count > 0)
                throw new InvalidOperationException(
                    "Configuration contains blank property values: " + string.Join(", ", invalid.ToArray()));
            return document.GetText();
        }

        public void RestoreDefault(string configurationPath)
        {
            AtomicFile.WriteAllText(configurationPath, EmbeddedDefault.ReadText(), true);
        }

        private static bool ParseBoolean(string value)
        {
            bool result;
            return bool.TryParse(value, out result) && result;
        }

        private static void Validate(ConfiguratorState state)
        {
            if (state.GetBindings("activate").Count == 0)
                throw new InvalidOperationException(L10n.T("engine.activateRequired"));

            IList<KeyChord> window = state.GetBindings("windowmode");
            if (window.Any(delegate(KeyChord chord) { return !chord.HasModifiers; }))
                throw new InvalidOperationException(L10n.T("engine.windowModifier"));

            if (state.AltTabCenteringEnabled)
            {
                IList<KeyChord> altTab = state.GetBindings("alttab");
                if (altTab.Count == 0 || altTab.Any(delegate(KeyChord chord) { return !chord.HasModifiers; }))
                    throw new InvalidOperationException(L10n.T("engine.altTabModifier"));
            }

            ValidateRange(state.MouseMaxVelocity, 1, 100000, L10n.T("num.mouse-max-velocity.title"));
            ValidateRange(state.MouseAcceleration, 0, 100000, L10n.T("num.mouse-acceleration.title"));
            ValidateRange(state.WheelMaxVelocity, 1, 100000, L10n.T("num.wheel-max-velocity.title"));
            ValidateRange(state.WheelAcceleration, 0, 100000, L10n.T("num.wheel-acceleration.title"));
        }

        private static void ValidateRange(int value, int minimum, int maximum, string title)
        {
            if (value < minimum || value > maximum)
            {
                throw new InvalidOperationException(
                    L10n.F("engine.range", title, minimum, maximum));
            }
        }

        private static void ImportMotionSettings(
            ConfigDocument document,
            IDictionary<string, string> metadata,
            ConfiguratorState state)
        {
            state.MouseMaxVelocity = ReadMotionValue(
                document,
                metadata,
                "normal-mode.mouse.max-velocity",
                "mmcfg.mouse.max-velocity",
                state.MouseMaxVelocity);
            state.MouseAcceleration = ReadMotionValue(
                document,
                metadata,
                "normal-mode.mouse.acceleration",
                "mmcfg.mouse.acceleration",
                state.MouseAcceleration);
            state.WheelMaxVelocity = ReadMotionValue(
                document,
                metadata,
                "normal-mode.wheel.max-velocity",
                "mmcfg.wheel.max-velocity",
                state.WheelMaxVelocity);
            state.WheelAcceleration = ReadMotionValue(
                document,
                metadata,
                "normal-mode.wheel.acceleration",
                "mmcfg.wheel.acceleration",
                state.WheelAcceleration);
        }

        private static int ReadMotionValue(
            ConfigDocument document,
            IDictionary<string, string> metadata,
            string propertyKey,
            string metadataKey,
            int fallback)
        {
            string propertyValue = document.GetPropertyValue(propertyKey);
            int separator = IndexOfTopLevel(propertyValue, '|');
            string defaultBranch = propertyValue == null
                ? null
                : (separator < 0 ? propertyValue : propertyValue.Substring(0, separator)).Trim();
            int parsed;
            if (int.TryParse(
                    defaultBranch,
                    NumberStyles.Integer,
                    CultureInfo.InvariantCulture,
                    out parsed))
                return parsed;

            string metadataValue;
            if (metadata.TryGetValue(metadataKey, out metadataValue) &&
                int.TryParse(
                    metadataValue,
                    NumberStyles.Integer,
                    CultureInfo.InvariantCulture,
                    out parsed))
                return parsed;
            return fallback;
        }

        private static void ImportAliases(ConfigDocument document, ConfiguratorState state)
        {
            foreach (ActionDefinition action in BindingCatalog.Actions)
            {
                if (string.IsNullOrEmpty(action.AliasName))
                    continue;
                string propertyKey = document.FindAliasPropertyKey(action.AliasName);
                if (propertyKey == null)
                    continue;
                string value = document.GetPropertyValue(propertyKey);
                if (string.IsNullOrWhiteSpace(value))
                    continue;
                var chords = value.Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries)
                    .Where(delegate(string key) { return !key.StartsWith("mmcfgunbound", StringComparison.OrdinalIgnoreCase); })
                    .Select(delegate(string key) { return new KeyChord(new string[0], key); })
                    .ToList();
                state.SetBindings(action.Id, chords);
            }

            IList<string> activationModifiers = ReadAliasKeys(document, "enablemod");
            IList<string> activationKeys = ReadAliasKeys(document, "enablekey");
            if (activationKeys.Count > 0)
            {
                var activation = new List<KeyChord>();
                if (activationModifiers.Count == 0)
                {
                    activation.AddRange(activationKeys.Select(
                        delegate(string key) { return new KeyChord(new string[0], key); }));
                }
                else
                {
                    foreach (string modifier in activationModifiers)
                    {
                        foreach (string key in activationKeys)
                            activation.Add(new KeyChord(new[] { modifier }, key));
                    }
                }
                state.SetBindings("activate", activation);
            }

            IList<string> windowModifiers = ReadAliasKeys(document, "windowmod");
            IList<string> gridKeys = ReadAliasKeys(document, "grid");
            if (windowModifiers.Count > 0 && gridKeys.Count > 0)
                state.SetBindings("windowmode", new[] { new KeyChord(windowModifiers, gridKeys[0]) });

            IList<string> uiModifiers = ReadAliasKeys(document, "uihintmod");
            IList<string> hintKeys = ReadAliasKeys(document, "hint");
            if (uiModifiers.Count > 0 && hintKeys.Count > 0)
                state.SetBindings("uihintmode", new[] { new KeyChord(uiModifiers, hintKeys[0]) });
        }

        private static IList<string> ReadAliasKeys(ConfigDocument document, string aliasName)
        {
            string key = document.FindAliasPropertyKey(aliasName);
            if (key == null)
                return new List<string>();
            string value = document.GetPropertyValue(key);
            if (string.IsNullOrWhiteSpace(value))
                return new List<string>();
            return value.Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries)
                .Where(delegate(string item)
                {
                    return !item.StartsWith("mmcfgunbound", StringComparison.OrdinalIgnoreCase);
                })
                .ToList();
        }

        private void UpdateAliases(ConfigDocument document, ConfiguratorState state)
        {
            foreach (ActionDefinition action in BindingCatalog.Actions)
            {
                if (string.IsNullOrEmpty(action.AliasName))
                    continue;
                SetAlias(document, action.AliasName, MainKeys(state, action.Id));
            }

            List<string> activationModifiers = state.GetBindings("activate")
                .SelectMany(delegate(KeyChord chord) { return chord.Modifiers; })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            SetAlias(document, "enablemod", activationModifiers);
            SetAlias(document, "enablekey", MainKeys(state, "activate"));

            List<string> windowModifiers = state.GetBindings("windowmode")
                .SelectMany(delegate(KeyChord chord) { return chord.Modifiers; })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            SetAlias(document, "windowmod", windowModifiers);

            List<string> uiHintModifiers = state.GetBindings("uihintmode")
                .SelectMany(delegate(KeyChord chord) { return chord.Modifiers; })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            SetAlias(document, "uihintmod", uiHintModifiers);
        }

        private void SetAlias(
            ConfigDocument document,
            string aliasName,
            IEnumerable<string> keys)
        {
            List<string> normalized = keys
                .Select(KeyChord.NormalizeKey)
                .Where(delegate(string key) { return key.Length > 0; })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            if (normalized.Count == 0)
                normalized.Add("f24");

            string basePropertyKey = "key-alias." + aliasName;
            string propertyKey = document.FindAliasPropertyKey(aliasName);
            if (normalized.Any(IsNumpadKey))
            {
                while (propertyKey != null)
                {
                    document.RemoveProperty(propertyKey);
                    propertyKey = document.FindAliasPropertyKey(aliasName);
                }
                propertyKey = basePropertyKey;
            }
            else if (propertyKey == null)
            {
                propertyKey = basePropertyKey;
            }
            document.SetProperty(propertyKey, string.Join(" ", normalized.ToArray()));
        }

        private static bool IsNumpadKey(string key)
        {
            return key.StartsWith("numpad", StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(key, "numlock", StringComparison.OrdinalIgnoreCase);
        }

        private static IEnumerable<string> MainKeys(ConfiguratorState state, string actionId)
        {
            return state.GetBindings(actionId)
                .Select(delegate(KeyChord chord) { return chord.Key; })
                .Distinct(StringComparer.OrdinalIgnoreCase);
        }

        private static void UpdateActivationAndExit(ConfigDocument document, ConfiguratorState state)
        {
            List<KeyChord> pool = NormalModeChordPool(state);
            string activation = BuildActivation(state.GetBindings("activate"), pool);
            SetOrDisable(document, "idle-mode.to.normal-mode", activation);

            var exitBranches = new List<string>();
            AddBranches(exitBranches, BuildPressWithPoolExclusions(state.GetBindings("exit"), pool, true));
            AddBranches(exitBranches, BuildRelease(state.GetBindings("clickdisable")));
            AddBranches(exitBranches, activation);
            AddBranches(exitBranches, BuildPressWithPoolExclusions(state.GetBindings("passthroughexit"), pool, false));
            SetOrDisable(document, "normal-mode.to.idle-mode", JoinBranches(exitBranches));

            string exit = BuildPressWithPoolExclusions(state.GetBindings("exit"), pool, true);
            SetOrDisable(document, "grid-mode.to.idle-mode", exit);
            SetOrDisable(document, "hint2-mode.to.idle-mode", exit);
            SetOrDisable(document, "screen-selection-mode.to.idle-mode", exit);
        }

        private static void UpdateMouseButtons(ConfigDocument document, ConfiguratorState state)
        {
            var leftPress = new List<string>();
            AddBranches(leftPress, BuildMousePress(state.GetBindings("leftbutton"), true));
            AddBranches(leftPress, BuildMousePress(state.GetBindings("clickdisable"), true));
            SetOrDisable(document, "normal-mode.press.left", JoinBranches(leftPress));

            var leftRelease = new List<string>();
            AddBranches(leftRelease, BuildRelease(state.GetBindings("leftbutton")));
            AddBranches(leftRelease, BuildRelease(state.GetBindings("clickdisable")));
            SetOrDisable(document, "normal-mode.release.left", JoinBranches(leftRelease));

            SetOrDisable(
                document,
                "normal-mode.press.middle",
                BuildMousePress(state.GetBindings("middlebutton"), false));
            SetOrDisable(
                document,
                "normal-mode.release.middle",
                BuildRelease(state.GetBindings("middlebutton")));
            SetOrDisable(
                document,
                "normal-mode.press.right",
                BuildMousePress(state.GetBindings("rightbutton"), true));
            SetOrDisable(
                document,
                "normal-mode.release.right",
                BuildRelease(state.GetBindings("rightbutton")));
            SetOrDisable(
                document,
                "normal-mode.toggle.left",
                BuildMousePress(state.GetBindings("toggleleft"), true));
        }

        private static void UpdateWheel(ConfigDocument document, ConfiguratorState state)
        {
            List<KeyChord> pool = NormalModeChordPool(state);
            UpdateWheelDirection(document, state, pool, "up", "wheelup");
            UpdateWheelDirection(document, state, pool, "down", "wheeldown");
            UpdateWheelDirection(document, state, pool, "left", "wheelleft");
            UpdateWheelDirection(document, state, pool, "right", "wheelright");
        }

        private static void UpdateWheelDirection(
            ConfigDocument document,
            ConfiguratorState state,
            List<KeyChord> pool,
            string direction,
            string actionId)
        {
            SetOrDisable(
                document,
                "normal-mode.start-wheel." + direction,
                BuildPressWithPoolExclusions(state.GetBindings(actionId), pool, true));
            SetOrDisable(
                document,
                "normal-mode.stop-wheel." + direction,
                BuildRelease(state.GetBindings(actionId)));
        }

        private static void UpdateMotionSettings(
            ConfigDocument document,
            ConfiguratorState state)
        {
            ReplaceDefaultPropertyBranch(
                document,
                "normal-mode.mouse.max-velocity",
                state.MouseMaxVelocity);
            ReplaceDefaultPropertyBranch(
                document,
                "normal-mode.mouse.acceleration",
                state.MouseAcceleration);
            ReplaceDefaultPropertyBranch(
                document,
                "normal-mode.wheel.max-velocity",
                state.WheelMaxVelocity);
            ReplaceDefaultPropertyBranch(
                document,
                "normal-mode.wheel.acceleration",
                state.WheelAcceleration);
        }

        private static void ReplaceDefaultPropertyBranch(
            ConfigDocument document,
            string propertyKey,
            int value)
        {
            string existing = document.GetPropertyValue(propertyKey);
            int separator = IndexOfTopLevel(existing, '|');
            string suffix = separator < 0
                ? string.Empty
                : " " + existing.Substring(separator).TrimStart();
            document.SetProperty(
                propertyKey,
                value.ToString(CultureInfo.InvariantCulture) + suffix);
        }

        private static void UpdateModes(ConfigDocument document, ConfiguratorState state)
        {
            List<KeyChord> pool = NormalModeChordPool(state);
            string gridEntry = BuildPressWithPoolExclusions(
                state.GetBindings("gridmode"),
                pool,
                true);
            SetOrDisable(document, "normal-mode.to.grid-mode", gridEntry);

            var gridExit = new List<string>();
            AddBranches(gridExit, BuildPress(state.GetBindings("gridmode"), true));
            AddBranches(gridExit, BuildPress(state.GetBindings("cancel"), true));
            SetOrDisable(document, "grid-mode.to.normal-mode", JoinBranches(gridExit));

            string windowEntry = BuildPressWithPoolExclusions(
                state.GetBindings("windowmode"),
                pool,
                true);
            SetOrDisable(document, "normal-mode.to.window-mode", windowEntry);
            List<string> windowModifiers = state.GetBindings("windowmode")
                .SelectMany(delegate(KeyChord chord) { return chord.Modifiers; })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            SetOrDisable(
                document,
                "normal-mode.noop.eatwindowmod",
                windowModifiers.Count == 0 ? null : "+windowmod");
            SetOrDisable(
                document,
                "window-mode.to.normal-mode",
                JoinBranches(windowModifiers.Select(delegate(string key) { return "-" + key; })));
            var windowCenters = state.GetBindings("windowmode")
                .Select(delegate(KeyChord chord) { return new KeyChord(new string[0], chord.Key); })
                .ToList();
            SetOrDisable(
                document,
                "window-mode.move-to-grid-center",
                BuildPress(windowCenters, true));

            string hintEntry = BuildPressWithPoolExclusions(
                state.GetBindings("hintmode"),
                pool,
                true);
            SetOrDisable(document, "normal-mode.to.hint1-mode", hintEntry);
            SetOrDisable(
                document,
                "normal-mode.to.ui-hint-mode",
                BuildPressWithPoolExclusions(state.GetBindings("uihintmode"), pool, true));
            SetOrDisable(
                document,
                "normal-mode.to.screen-selection-mode",
                BuildPressWithPoolExclusions(state.GetBindings("screenselection"), pool, true));

            var hint1Exit = new List<string>();
            AddBranches(hint1Exit, BuildPress(state.GetBindings("cancel"), true));
            AddBranches(hint1Exit, BuildPress(state.GetBindings("hintback"), true));
            hint1Exit.Add("^{hint2mod} +extendedhint1key");
            SetOrDisable(document, "hint1-mode.to.normal-mode", JoinBranches(hint1Exit));
            SetOrDisable(
                document,
                "hint1-mode.hint.undo",
                BuildPress(state.GetBindings("hintback"), true));

            bool hasHint2Modifier = state.GetBindings("hint2modifier").Count > 0;
            SetOrDisable(
                document,
                "hint1-mode.noop.eathintmod",
                hasHint2Modifier ? "+hint2mod" : null);
            SetOrDisable(
                document,
                "hint1-mode.to.hint2-mode",
                hasHint2Modifier ? "_{hint2mod} +extendedhint1key" : null);

            string hintBackWithPrecondition = BuildPressWithRawPrecondition(
                state.GetBindings("hintback"), "_{none | hint2mod}");
            SetOrDisable(document, "hint2-mode.to.hint1-mode", hintBackWithPrecondition);

            var hint2Exit = new List<string>();
            AddBranches(hint2Exit, BuildPress(state.GetBindings("cancel"), true));
            hint2Exit.Add("_{none | hint2mod} +extendedhint1key");
            SetOrDisable(document, "hint2-mode.to.normal-mode", JoinBranches(hint2Exit));

            var screenExit = new List<string>();
            AddBranches(screenExit, BuildPress(state.GetBindings("screenselection"), true));
            AddBranches(screenExit, BuildPress(state.GetBindings("cancel"), true));
            AddBranches(screenExit, BuildPress(state.GetBindings("hintback"), true));
            screenExit.Add("+hintscreenselectionkey");
            SetOrDisable(document, "screen-selection-mode.to.normal-mode", JoinBranches(screenExit));

            var uiExit = new List<string>();
            AddBranches(uiExit, BuildPress(state.GetBindings("cancel"), true));
            AddBranches(uiExit, BuildPress(state.GetBindings("hintback"), true));
            uiExit.Add("+hint1key");
            SetOrDisable(document, "ui-hint-mode.to.normal-mode", JoinBranches(uiExit));
            SetOrDisable(
                document,
                "ui-hint-mode.hint.undo",
                BuildPress(state.GetBindings("hintback"), true));
        }

        private static void UpdateAutomation(ConfigDocument document, ConfiguratorState state)
        {
            List<KeyChord> pool = NormalModeChordPool(state);
            string navigateBack = BuildPressWithPoolExclusions(state.GetBindings("navigateback"), pool, true);
            SetOrDisable(
                document,
                "normal-mode.macro.navigateback",
                string.IsNullOrWhiteSpace(navigateBack)
                    ? null
                    : navigateBack + " -> +leftalt +leftarrow -leftarrow -leftalt");

            string navigateForward = BuildPressWithPoolExclusions(state.GetBindings("navigateforward"), pool, true);
            SetOrDisable(
                document,
                "normal-mode.macro.navigateforward",
                string.IsNullOrWhiteSpace(navigateForward)
                    ? null
                    : navigateForward + " -> +leftalt +rightarrow -rightarrow -leftalt");

            string altTab = BuildPressWithPoolExclusions(state.GetBindings("alttab"), pool, false);
            SetOrDisable(document, "idle-mode.to.begin-alt-tab-mode", altTab);
            SetOrDisable(document, "normal-mode.to.begin-alt-tab-mode", altTab);

            List<string> altTabModifiers = state.GetBindings("alttab")
                .SelectMany(delegate(KeyChord chord) { return chord.Modifiers; })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
            SetOrDisable(
                document,
                "begin-alt-tab-mode.to.end-alt-tab-mode",
                JoinBranches(altTabModifiers.Select(delegate(string key) { return "-" + key; })));
        }

        private void UpdateVirtualKeys(ConfigDocument document)
        {
            var preserved = new List<string>();
            string existing = document.GetPropertyValue("virtual-keys");
            if (!string.IsNullOrWhiteSpace(existing))
            {
                preserved.AddRange(existing.Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries)
                    .Where(delegate(string key)
                    {
                        return !key.StartsWith("mmcfgunbound", StringComparison.OrdinalIgnoreCase);
                    }));
            }
            preserved = preserved.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            if (preserved.Count == 0)
                document.RemoveProperty("virtual-keys");
            else
                document.SetProperty("virtual-keys", string.Join(" ", preserved.ToArray()));
        }

        private static void ApplyFeatureToggles(ConfigDocument document, ConfiguratorState state)
        {
            if (!state.AltTabCenteringEnabled)
            {
                foreach (string property in AltTabCenteringProperties)
                    document.CommentProperty(property);
            }

            if (!state.FocusModeEnabled)
                return;

            document.CommentProperty("normal-mode.macro.navigateback");
            document.CommentProperty("normal-mode.macro.navigateforward");
            document.CommentProperty("normal-mode.macro");
            document.CommentProperty("normal-mode.to.begin-alt-tab-mode");

            foreach (string mode in FocusModes)
            {
                ISet<string> passThroughKeys = NonEatingHandledKeys(document, mode);
                string[] fallbackKeys = FocusKeys.Where(
                    delegate(string key) { return !passThroughKeys.Contains(key); }).ToArray();
                if (fallbackKeys.Length == 0)
                    continue;

                string aliasName = FocusAliasName(mode);
                document.SetProperty(
                    FocusAliasProperty(mode),
                    string.Join(" ", fallbackKeys));
                document.SetProperty(mode + ".noop.mmcfg-focus", "+" + aliasName);
            }
        }

        private static string FocusAliasName(string mode)
        {
            return "mmcfgfocus" +
                mode.Replace("-mode", string.Empty).Replace("-", string.Empty) +
                "key";
        }

        private static string FocusAliasProperty(string mode)
        {
            return "key-alias." + FocusAliasName(mode);
        }

        private static ISet<string> NonEatingHandledKeys(
            ConfigDocument document,
            string mode)
        {
            var result = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (KeyValuePair<string, string> property in
                     document.ActivePropertiesStartingWith(mode + "."))
            {
                string comboText = BeforeTopLevelArrow(property.Value);
                foreach (string branch in SplitTopLevel(comboText, '|'))
                {
                    if (branch.IndexOf('#') < 0)
                        continue;
                    CollectNonEatingMoves(document, branch, result);
                    CollectPressedPreconditions(document, branch, result);
                }
            }
            return result;
        }

        private static void CollectNonEatingMoves(
            ConfigDocument document,
            string combo,
            ISet<string> result)
        {
            for (int index = 0; index < combo.Length; index++)
            {
                if (combo[index] != '#')
                    continue;

                index++;
                bool negated = index < combo.Length && combo[index] == '!';
                if (negated)
                    index++;
                if (index >= combo.Length)
                    break;

                if (combo[index] == '{')
                {
                    int close = combo.IndexOf('}', index + 1);
                    if (close < 0)
                        break;
                    string content = combo.Substring(index + 1, close - index - 1).Trim();
                    var resolved = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                    foreach (string token in SplitKeyTokens(content))
                        AddResolvedFocusKeys(document, token, resolved, new HashSet<string>(
                            StringComparer.OrdinalIgnoreCase));

                    if (negated)
                    {
                        foreach (string key in FocusKeys)
                        {
                            if (!resolved.Contains(key))
                                result.Add(key);
                        }
                    }
                    else if (content == "*" || content == "+")
                    {
                        result.UnionWith(FocusKeys);
                    }
                    else if (content != "-")
                    {
                        result.UnionWith(resolved);
                    }
                    index = close;
                    continue;
                }

                int start = index;
                while (index < combo.Length &&
                       !char.IsWhiteSpace(combo[index]) &&
                       combo[index] != '|')
                {
                    index++;
                }
                string bareToken = StripMoveDuration(combo.Substring(start, index - start));
                var bareResolved = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                AddResolvedFocusKeys(
                    document,
                    bareToken,
                    bareResolved,
                    new HashSet<string>(StringComparer.OrdinalIgnoreCase));
                if (negated)
                {
                    foreach (string key in FocusKeys)
                    {
                        if (!bareResolved.Contains(key))
                            result.Add(key);
                    }
                }
                else
                {
                    result.UnionWith(bareResolved);
                }
                index--;
            }
        }

        private static void CollectPressedPreconditions(
            ConfigDocument document,
            string combo,
            ISet<string> result)
        {
            int index = 0;
            while ((index = combo.IndexOf("_{", index, StringComparison.Ordinal)) >= 0)
            {
                int close = combo.IndexOf('}', index + 2);
                if (close < 0)
                    return;
                string content = combo.Substring(index + 2, close - index - 2);
                foreach (string token in SplitKeyTokens(content))
                {
                    AddResolvedFocusKeys(
                        document,
                        token,
                        result,
                        new HashSet<string>(StringComparer.OrdinalIgnoreCase));
                }
                index = close + 1;
            }
        }

        private static void AddResolvedFocusKeys(
            ConfigDocument document,
            string token,
            ISet<string> result,
            ISet<string> visitedAliases)
        {
            token = token.Trim().TrimEnd('?');
            while (token.StartsWith("*", StringComparison.Ordinal) ||
                   token.StartsWith("!", StringComparison.Ordinal))
            {
                token = token.Substring(1);
            }
            if (token.Length == 0 ||
                string.Equals(token, "none", StringComparison.OrdinalIgnoreCase))
                return;
            if (FocusKeySet.Contains(token))
            {
                result.Add(token);
                return;
            }
            if (!visitedAliases.Add(token))
                return;

            string aliasProperty = document.FindAliasPropertyKey(token);
            if (aliasProperty == null)
                return;
            string aliasValue = document.GetPropertyValue(aliasProperty);
            foreach (string aliasToken in SplitKeyTokens(aliasValue))
                AddResolvedFocusKeys(document, aliasToken, result, visitedAliases);
        }

        private static IEnumerable<string> SplitKeyTokens(string value)
        {
            return (value ?? string.Empty).Split(
                new[] { ' ', '\t', '|' },
                StringSplitOptions.RemoveEmptyEntries);
        }

        private static string StripMoveDuration(string token)
        {
            for (int index = 1; index < token.Length; index++)
            {
                if (token[index] == '-' &&
                    index + 1 < token.Length &&
                    char.IsDigit(token[index + 1]))
                    return token.Substring(0, index);
            }
            return token;
        }

        private static string BeforeTopLevelArrow(string value)
        {
            int depth = 0;
            for (int index = 0; index < value.Length - 1; index++)
            {
                if (value[index] == '{')
                    depth++;
                else if (value[index] == '}')
                    depth = Math.Max(0, depth - 1);
                else if (depth == 0 && value[index] == '-' && value[index + 1] == '>')
                    return value.Substring(0, index);
            }
            return value;
        }

        private static IEnumerable<string> SplitTopLevel(string value, char separator)
        {
            var result = new List<string>();
            int depth = 0;
            int start = 0;
            for (int index = 0; index < value.Length; index++)
            {
                if (value[index] == '{')
                    depth++;
                else if (value[index] == '}')
                    depth = Math.Max(0, depth - 1);
                else if (depth == 0 && value[index] == separator)
                {
                    result.Add(value.Substring(start, index - start));
                    start = index + 1;
                }
            }
            result.Add(value.Substring(start));
            return result;
        }

        private static int IndexOfTopLevel(string value, char separator)
        {
            if (string.IsNullOrEmpty(value))
                return -1;
            int depth = 0;
            for (int index = 0; index < value.Length; index++)
            {
                if (value[index] == '{')
                    depth++;
                else if (value[index] == '}')
                    depth = Math.Max(0, depth - 1);
                else if (depth == 0 && value[index] == separator)
                    return index;
            }
            return -1;
        }

        private static void AppendMetadata(ConfigDocument document, ConfiguratorState state)
        {
            var metadata = new List<string>
            {
                "mmcfg.version=1",
                "mmcfg.focus=" + state.FocusModeEnabled.ToString().ToLowerInvariant(),
                "mmcfg.alttab=" + state.AltTabCenteringEnabled.ToString().ToLowerInvariant(),
                "mmcfg.mouse.max-velocity=" +
                    state.MouseMaxVelocity.ToString(CultureInfo.InvariantCulture),
                "mmcfg.mouse.acceleration=" +
                    state.MouseAcceleration.ToString(CultureInfo.InvariantCulture),
                "mmcfg.wheel.max-velocity=" +
                    state.WheelMaxVelocity.ToString(CultureInfo.InvariantCulture),
                "mmcfg.wheel.acceleration=" +
                    state.WheelAcceleration.ToString(CultureInfo.InvariantCulture)
            };
            foreach (ActionDefinition action in BindingCatalog.Actions)
            {
                metadata.Add(
                    "mmcfg.binding." + action.Id + "=" +
                    KeyChord.SerializeList(state.GetBindings(action.Id)));
            }
            document.AppendMetadata(metadata);
        }

        private static string BuildActivation(IEnumerable<KeyChord> chords, List<KeyChord> pool)
        {
            var branches = new List<string>();
            foreach (KeyChord chord in chords.Distinct())
            {
                if (!chord.HasModifiers)
                {
                    branches.Add(BuildPressChord(chord, true, PoolExcludedModifiers(chord, pool)));
                    continue;
                }

                string modifierPress;
                if (chord.Modifiers.Count == 1)
                    modifierPress = "+" + chord.Modifiers[0];
                else
                    modifierPress = "{+" + string.Join(" +", chord.Modifiers.ToArray()) + "}";
                branches.Add(modifierPress + " +" + chord.Key);
                branches.Add("_{" + string.Join(" ", chord.Modifiers.ToArray()) + "} +" + chord.Key);
            }
            return JoinBranches(branches);
        }

        private static string BuildPress(IEnumerable<KeyChord> chords, bool eat)
        {
            return JoinBranches(chords.Distinct().Select(
                delegate(KeyChord chord) { return BuildPressChord(chord, eat, new string[0]); }));
        }

        /// <summary>
        /// Chords of every action that produces a combo in normal-mode. A
        /// modifier-free chord (plain +key) matches even while modifiers are
        /// held, so it must explicitly exclude the modifiers of any sibling
        /// chord bound to the same main key (^{leftctrl} +f); otherwise both
        /// combos fire and the one later in the file wins.
        /// </summary>
        private static List<KeyChord> NormalModeChordPool(ConfiguratorState state)
        {
            var pool = new List<KeyChord>();
            foreach (ActionDefinition action in BindingCatalog.Actions)
            {
                if (NonNormalModeActions.Contains(action.Id))
                    continue;
                pool.AddRange(state.GetBindings(action.Id));
            }
            return pool;
        }

        private static List<string> PoolExcludedModifiers(
            KeyChord chord,
            List<KeyChord> pool)
        {
            return pool
                .Where(delegate(KeyChord other)
                {
                    return string.Equals(other.Key, chord.Key, StringComparison.OrdinalIgnoreCase);
                })
                .SelectMany(delegate(KeyChord other) { return other.Modifiers; })
                .Where(delegate(string modifier)
                {
                    return !chord.Modifiers.Contains(modifier, StringComparer.OrdinalIgnoreCase);
                })
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();
        }

        /// <summary>
        /// Builds press branches for chords that share normal-mode with other
        /// bindings. Modifier-free chords gain ^{...} exclusions for the
        /// modifiers of same-key siblings. Chords with modifiers are already
        /// protected by the strict _{...} semantics (an extra held key breaks
        /// the pressed precondition), so they are emitted unchanged.
        /// </summary>
        private static string BuildPressWithPoolExclusions(
            IEnumerable<KeyChord> chords,
            List<KeyChord> pool,
            bool eat)
        {
            return JoinBranches(chords.Distinct().Select(
                delegate(KeyChord chord)
                {
                    return BuildPressChord(
                        chord,
                        eat,
                        chord.HasModifiers
                            ? (IEnumerable<string>) new string[0]
                            : PoolExcludedModifiers(chord, pool));
                }));
        }

        private static string BuildPressChord(
            KeyChord chord,
            bool eat,
            IEnumerable<string> excludedModifiers)
        {
            var builder = new StringBuilder();
            string[] excluded = excludedModifiers.Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
            if (excluded.Length > 0)
                builder.Append("^{").Append(string.Join(" ", excluded)).Append("} ");
            if (chord.Modifiers.Count > 0)
                builder.Append("_{").Append(string.Join(" ", chord.Modifiers.ToArray())).Append("} ");
            builder.Append(eat ? "+" : "#").Append(chord.Key);
            return builder.ToString();
        }

        private static string BuildPressWithRawPrecondition(
            IEnumerable<KeyChord> chords,
            string rawPrecondition)
        {
            return JoinBranches(chords.Distinct().Select(
                delegate(KeyChord chord)
                {
                    string chordPrecondition = chord.Modifiers.Count == 0
                        ? string.Empty
                        : " _{" + string.Join(" ", chord.Modifiers.ToArray()) + "}";
                    return rawPrecondition + chordPrecondition + " +" + chord.Key;
                }));
        }

        private static string BuildMousePress(IEnumerable<KeyChord> chords, bool allowCtrlClick)
        {
            return JoinBranches(chords.Distinct().Select(
                delegate(KeyChord chord)
                {
                    if (allowCtrlClick && chord.Modifiers.Count == 0)
                        return "_{none | leftctrl} +" + chord.Key;
                    return BuildPressChord(chord, true, new string[0]);
                }));
        }

        private static string BuildRelease(IEnumerable<KeyChord> chords)
        {
            var branches = new List<string>();
            foreach (KeyChord chord in chords.Distinct())
            {
                branches.Add("-" + chord.Key);
                foreach (string modifier in chord.Modifiers)
                    branches.Add("_{" + chord.Key + "} -" + modifier);
            }
            return JoinBranches(branches);
        }

        private static void SetOrDisable(
            ConfigDocument document,
            string propertyKey,
            string propertyValue)
        {
            if (string.IsNullOrWhiteSpace(propertyValue))
                document.CommentProperty(propertyKey);
            else
                document.SetProperty(propertyKey, propertyValue);
        }

        private static void AddBranches(ICollection<string> target, string branches)
        {
            if (string.IsNullOrWhiteSpace(branches))
                return;

            var current = new StringBuilder();
            int braceDepth = 0;
            foreach (char character in branches)
            {
                if (character == '{')
                    braceDepth++;
                else if (character == '}' && braceDepth > 0)
                    braceDepth--;

                if (character == '|' && braceDepth == 0)
                {
                    string branch = current.ToString().Trim();
                    if (branch.Length > 0)
                        target.Add(branch);
                    current.Clear();
                    continue;
                }
                current.Append(character);
            }

            string finalBranch = current.ToString().Trim();
            if (finalBranch.Length > 0)
                target.Add(finalBranch);
        }

        private static string JoinBranches(IEnumerable<string> branches)
        {
            return string.Join(
                " | ",
                branches.Where(delegate(string branch) { return !string.IsNullOrWhiteSpace(branch); })
                        .Select(delegate(string branch) { return branch.Trim(); })
                        .Distinct(StringComparer.Ordinal)
                        .ToArray());
        }
    }
}
