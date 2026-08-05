using System;
using System.Collections.Generic;
using System.Linq;

namespace MouseMasterConfigurator
{
    internal sealed class KeyChord : IEquatable<KeyChord>
    {
        private static readonly string[] ModifierOrder =
        {
            "leftctrl", "rightctrl",
            "leftshift", "rightshift",
            "leftalt", "rightalt",
            "leftwin", "rightwin"
        };

        private readonly List<string> modifiers;

        public KeyChord(IEnumerable<string> modifiers, string key)
        {
            if (key == null)
                throw new ArgumentNullException("key");

            Key = NormalizeKey(key);
            this.modifiers = modifiers == null
                ? new List<string>()
                : modifiers.Select(NormalizeKey)
                           .Where(delegate(string item) { return item.Length > 0 && item != Key; })
                           .Distinct(StringComparer.OrdinalIgnoreCase)
                           .OrderBy(ModifierRank)
                           .ThenBy(delegate(string item) { return item; }, StringComparer.OrdinalIgnoreCase)
                           .ToList();
        }

        public string Key { get; private set; }

        public IList<string> Modifiers
        {
            get { return modifiers.AsReadOnly(); }
        }

        public bool HasModifiers
        {
            get { return modifiers.Count > 0; }
        }

        public string Serialize()
        {
            if (modifiers.Count == 0)
                return Key;
            return string.Join("+", modifiers.Concat(new[] { Key }).ToArray());
        }

        public string DisplayText()
        {
            IEnumerable<string> parts = modifiers.Concat(new[] { Key }).Select(KeyNames.FriendlyName);
            return string.Join(" + ", parts.ToArray());
        }

        public override string ToString()
        {
            return DisplayText();
        }

        public KeyChord Clone()
        {
            return new KeyChord(modifiers, Key);
        }

        public static KeyChord Parse(string serialized)
        {
            if (string.IsNullOrWhiteSpace(serialized))
                throw new ArgumentException("Shortcut cannot be blank.", "serialized");

            string[] parts = serialized.Split(new[] { '+' }, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length == 0)
                throw new ArgumentException("Shortcut cannot be blank.", "serialized");
            if (parts.Length == 1)
                return new KeyChord(new string[0], parts[0]);
            return new KeyChord(parts.Take(parts.Length - 1), parts[parts.Length - 1]);
        }

        public static List<KeyChord> ParseList(string serialized)
        {
            var result = new List<KeyChord>();
            if (string.IsNullOrWhiteSpace(serialized) || serialized.Trim() == "(none)")
                return result;

            foreach (string item in serialized.Split(new[] { '|' }, StringSplitOptions.RemoveEmptyEntries))
            {
                KeyChord chord = Parse(item.Trim());
                if (!result.Contains(chord))
                    result.Add(chord);
            }
            return result;
        }

        public static string SerializeList(IEnumerable<KeyChord> chords)
        {
            if (chords == null)
                return "(none)";
            string[] values = chords.Distinct().Select(delegate(KeyChord chord) { return chord.Serialize(); }).ToArray();
            return values.Length == 0 ? "(none)" : string.Join("|", values);
        }

        public static bool IsModifier(string key)
        {
            string normalized = NormalizeKey(key);
            return ModifierOrder.Contains(normalized, StringComparer.OrdinalIgnoreCase);
        }

        public static string NormalizeKey(string key)
        {
            return (key ?? string.Empty).Trim().ToLowerInvariant();
        }

        private static int ModifierRank(string key)
        {
            for (int index = 0; index < ModifierOrder.Length; index++)
            {
                if (string.Equals(ModifierOrder[index], key, StringComparison.OrdinalIgnoreCase))
                    return index;
            }
            return ModifierOrder.Length;
        }

        public bool Equals(KeyChord other)
        {
            if (ReferenceEquals(other, null))
                return false;
            return string.Equals(Key, other.Key, StringComparison.OrdinalIgnoreCase) &&
                   modifiers.SequenceEqual(other.modifiers, StringComparer.OrdinalIgnoreCase);
        }

        public override bool Equals(object obj)
        {
            return Equals(obj as KeyChord);
        }

        public override int GetHashCode()
        {
            unchecked
            {
                int hash = StringComparer.OrdinalIgnoreCase.GetHashCode(Key);
                foreach (string modifier in modifiers)
                    hash = (hash * 397) ^ StringComparer.OrdinalIgnoreCase.GetHashCode(modifier);
                return hash;
            }
        }
    }

    internal static class KeyNames
    {
        private static readonly Dictionary<string, string> FriendlyNames =
            new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
            {
                { "leftctrl", "Left Ctrl" },
                { "rightctrl", "Right Ctrl" },
                { "leftshift", "Left Shift" },
                { "rightshift", "Right Shift" },
                { "leftalt", "Left Alt" },
                { "rightalt", "Right Alt" },
                { "leftwin", "Left Win" },
                { "rightwin", "Right Win" },
                { "capslock", "Caps Lock" },
                { "backspace", "Backspace" },
                { "enter", "Enter" },
                { "esc", "Esc" },
                { "space", "Space" },
                { "tab", "Tab" },
                { "delete", "Delete" },
                { "insert", "Insert" },
                { "home", "Home" },
                { "end", "End" },
                { "pageup", "Page Up" },
                { "pagedown", "Page Down" },
                { "uparrow", "Up Arrow" },
                { "downarrow", "Down Arrow" },
                { "leftarrow", "Left Arrow" },
                { "rightarrow", "Right Arrow" },
                { ",", "," },
                { ".", "." },
                { ";", ";" },
                { "'", "'" },
                { "/", "/" },
                { "minus", "-" },
                { "equals", "=" },
                { "openbracket", "[" },
                { "closebracket", "]" },
                { "backslash", "\\" },
                { "backtick", "`" }
            };

        public static string FriendlyName(string key)
        {
            string result;
            if (FriendlyNames.TryGetValue(KeyChord.NormalizeKey(key), out result))
                return result;
            if (key != null && key.Length == 1)
                return key.ToUpperInvariant();
            return string.IsNullOrEmpty(key) ? string.Empty : char.ToUpperInvariant(key[0]) + key.Substring(1);
        }
    }

    internal sealed class CategoryDefinition
    {
        public CategoryDefinition(string id)
        {
            Id = id;
        }

        public string Id { get; private set; }

        public string Title
        {
            get { return L10n.T("category." + Id + ".title"); }
        }

        public string Description
        {
            get { return L10n.T("category." + Id + ".description"); }
        }
    }

    internal sealed class ActionDefinition
    {
        public ActionDefinition(
            string id,
            string categoryId,
            string defaultBindings,
            string aliasName,
            bool allowModifiers,
            bool allowMultiple,
            bool canClear,
            params string[] conflictScopes)
        {
            Id = id;
            CategoryId = categoryId;
            DefaultBindings = defaultBindings;
            AliasName = aliasName;
            AllowModifiers = allowModifiers;
            AllowMultiple = allowMultiple;
            CanClear = canClear;
            ConflictScopes = conflictScopes ?? new string[0];
        }

        public string Id { get; private set; }
        public string CategoryId { get; private set; }
        public string DefaultBindings { get; private set; }
        public string AliasName { get; private set; }
        public bool AllowModifiers { get; private set; }
        public bool AllowMultiple { get; private set; }
        public bool CanClear { get; private set; }
        public string[] ConflictScopes { get; private set; }

        public string Title
        {
            get { return L10n.T("action." + Id + ".title"); }
        }

        public string Description
        {
            get { return L10n.T("action." + Id + ".description"); }
        }
    }

    internal sealed class NumericSettingDefinition
    {
        public NumericSettingDefinition(
            string id,
            string categoryId,
            string unit,
            int minimum,
            int maximum,
            int increment)
        {
            Id = id;
            CategoryId = categoryId;
            Unit = unit;
            Minimum = minimum;
            Maximum = maximum;
            Increment = increment;
        }

        public string Id { get; private set; }
        public string CategoryId { get; private set; }
        public string Unit { get; private set; }
        public int Minimum { get; private set; }
        public int Maximum { get; private set; }
        public int Increment { get; private set; }

        public string Title
        {
            get { return L10n.T("num." + Id + ".title"); }
        }

        public string Description
        {
            get { return L10n.T("num." + Id + ".description"); }
        }
    }

    internal sealed class ConfiguratorState
    {
        private readonly Dictionary<string, List<KeyChord>> bindings =
            new Dictionary<string, List<KeyChord>>(StringComparer.OrdinalIgnoreCase);

        public bool FocusModeEnabled { get; set; }
        public bool AltTabCenteringEnabled { get; set; }
        public int MouseMaxVelocity { get; set; }
        public int MouseAcceleration { get; set; }
        public int WheelMaxVelocity { get; set; }
        public int WheelAcceleration { get; set; }

        public IList<KeyChord> GetBindings(string actionId)
        {
            List<KeyChord> value;
            if (!bindings.TryGetValue(actionId, out value))
            {
                value = new List<KeyChord>();
                bindings[actionId] = value;
            }
            return value;
        }

        public void SetBindings(string actionId, IEnumerable<KeyChord> value)
        {
            bindings[actionId] = value == null
                ? new List<KeyChord>()
                : value.Select(delegate(KeyChord chord) { return chord.Clone(); }).Distinct().ToList();
        }

        public int GetNumericSetting(string settingId)
        {
            switch (settingId)
            {
                case "mouse-max-velocity":
                    return MouseMaxVelocity;
                case "mouse-acceleration":
                    return MouseAcceleration;
                case "wheel-max-velocity":
                    return WheelMaxVelocity;
                case "wheel-acceleration":
                    return WheelAcceleration;
                default:
                    throw new ArgumentException("Unknown numeric setting: " + settingId, "settingId");
            }
        }

        public void SetNumericSetting(string settingId, int value)
        {
            switch (settingId)
            {
                case "mouse-max-velocity":
                    MouseMaxVelocity = value;
                    break;
                case "mouse-acceleration":
                    MouseAcceleration = value;
                    break;
                case "wheel-max-velocity":
                    WheelMaxVelocity = value;
                    break;
                case "wheel-acceleration":
                    WheelAcceleration = value;
                    break;
                default:
                    throw new ArgumentException("Unknown numeric setting: " + settingId, "settingId");
            }
        }

        public ConfiguratorState Clone()
        {
            var clone = new ConfiguratorState
            {
                FocusModeEnabled = FocusModeEnabled,
                AltTabCenteringEnabled = AltTabCenteringEnabled,
                MouseMaxVelocity = MouseMaxVelocity,
                MouseAcceleration = MouseAcceleration,
                WheelMaxVelocity = WheelMaxVelocity,
                WheelAcceleration = WheelAcceleration
            };
            foreach (KeyValuePair<string, List<KeyChord>> pair in bindings)
                clone.SetBindings(pair.Key, pair.Value);
            return clone;
        }

        public static ConfiguratorState CreateDefaults()
        {
            var state = new ConfiguratorState
            {
                FocusModeEnabled = false,
                AltTabCenteringEnabled = true,
                MouseMaxVelocity = 2200,
                MouseAcceleration = 3000,
                WheelMaxVelocity = 2000,
                WheelAcceleration = 500
            };
            foreach (ActionDefinition definition in BindingCatalog.Actions)
                state.SetBindings(definition.Id, KeyChord.ParseList(definition.DefaultBindings));
            return state;
        }
    }

    internal sealed class ConflictChange
    {
        public ConflictChange(ActionDefinition action, IList<KeyChord> removed)
        {
            Action = action;
            Removed = removed;
        }

        public ActionDefinition Action { get; private set; }
        public IList<KeyChord> Removed { get; private set; }
    }

    internal static class ConflictResolver
    {
        public static IList<ConflictChange> ApplyEdit(
            ConfiguratorState state,
            ActionDefinition current,
            IEnumerable<KeyChord> requested)
        {
            var normalized = requested == null
                ? new List<KeyChord>()
                : requested.Select(delegate(KeyChord chord) { return chord.Clone(); }).Distinct().ToList();

            if (!current.AllowMultiple && normalized.Count > 1)
                normalized = normalized.Take(1).ToList();
            if (!current.AllowModifiers && normalized.Any(delegate(KeyChord chord) { return chord.HasModifiers; }))
                throw new InvalidOperationException(L10n.F("engine.singleOnly", current.Title));
            if (!current.CanClear && normalized.Count == 0)
                throw new InvalidOperationException(L10n.F("engine.required", current.Title));

            state.SetBindings(current.Id, normalized);
            var changes = new List<ConflictChange>();
            foreach (ActionDefinition other in BindingCatalog.Actions)
            {
                if (string.Equals(other.Id, current.Id, StringComparison.OrdinalIgnoreCase))
                    continue;
                if (!ScopesOverlap(current, other))
                    continue;

                List<KeyChord> existing = state.GetBindings(other.Id).Select(
                    delegate(KeyChord chord) { return chord.Clone(); }).ToList();
                List<KeyChord> removed = existing.Where(
                    delegate(KeyChord oldChord) { return normalized.Contains(oldChord); }).ToList();
                if (removed.Count == 0)
                    continue;

                existing.RemoveAll(delegate(KeyChord oldChord) { return normalized.Contains(oldChord); });
                state.SetBindings(other.Id, existing);
                changes.Add(new ConflictChange(other, removed));
            }
            return changes;
        }

        private static bool ScopesOverlap(ActionDefinition first, ActionDefinition second)
        {
            return first.ConflictScopes.Intersect(
                second.ConflictScopes, StringComparer.OrdinalIgnoreCase).Any();
        }
    }
}
