using System.Collections.Generic;
using System.Linq;

namespace MouseMasterConfigurator
{
    /// <summary>
    /// Declares the six categories, 37 actions and four numeric settings the GUI
    /// can edit. Display text is not stored here: titles and descriptions are
    /// resolved from L10n using "category.&lt;id&gt;", "action.&lt;id&gt;" and
    /// "num.&lt;id&gt;" keys so the UI language can switch at runtime.
    /// </summary>
    internal static class BindingCatalog
    {
        public static readonly IList<CategoryDefinition> Categories = new List<CategoryDefinition>
        {
            new CategoryDefinition("general"),
            new CategoryDefinition("movement"),
            new CategoryDefinition("mouse"),
            new CategoryDefinition("modes"),
            new CategoryDefinition("hints"),
            new CategoryDefinition("automation")
        };

        public static readonly IList<NumericSettingDefinition> NumericSettings =
            new List<NumericSettingDefinition>
            {
                new NumericSettingDefinition(
                    "mouse-max-velocity",
                    "movement",
                    "px/s",
                    1,
                    100000,
                    100),
                new NumericSettingDefinition(
                    "mouse-acceleration",
                    "movement",
                    "px/s²",
                    0,
                    100000,
                    100),
                new NumericSettingDefinition(
                    "wheel-max-velocity",
                    "mouse",
                    "px/s",
                    1,
                    100000,
                    100),
                new NumericSettingDefinition(
                    "wheel-acceleration",
                    "mouse",
                    "px/s²",
                    0,
                    100000,
                    100)
            };

        public static readonly IList<ActionDefinition> Actions = new List<ActionDefinition>
        {
            new ActionDefinition(
                "activate", "general",
                "leftalt+e|leftalt+capslock", null, true, true, false, "idle"),
            new ActionDefinition(
                "exit", "general",
                "q|p", "exit", true, true, true, "normal", "grid", "hint2", "screen"),
            new ActionDefinition(
                "clickdisable", "general",
                ".", "clickthendisable", false, false, true, "normal"),
            new ActionDefinition(
                "passthroughexit", "general",
                "leftctrl+f|leftctrl+l|leftctrl+e|f2|f3|/", null, true, true, true, "normal"),

            new ActionDefinition(
                "moveup", "movement",
                "i", "up", false, false, true, "normal", "edge", "grid", "window"),
            new ActionDefinition(
                "movedown", "movement",
                "k", "down", false, false, true, "normal", "edge", "grid", "window"),
            new ActionDefinition(
                "moveleft", "movement",
                "j", "left", false, false, true, "normal", "edge", "grid", "window"),
            new ActionDefinition(
                "moveright", "movement",
                "l", "right", false, false, true, "normal", "edge", "grid", "window"),
            new ActionDefinition(
                "fast", "movement",
                "v|b", "fast", false, true, true, "normal", "edge"),
            new ActionDefinition(
                "slow", "movement",
                "leftshift", "slow", false, true, true, "normal-speed"),
            new ActionDefinition(
                "superslow", "movement",
                "capslock", "superslow", false, true, true, "normal-speed"),
            new ActionDefinition(
                "edge", "movement",
                "rightalt", "edge", false, false, true, "normal"),

            new ActionDefinition(
                "leftbutton", "mouse",
                ";", "leftbutton", true, true, true, "normal", "edge"),
            new ActionDefinition(
                "middlebutton", "mouse",
                "rightshift", "middlebutton", true, true, true, "normal", "edge"),
            new ActionDefinition(
                "rightbutton", "mouse",
                "'", "rightbutton", true, true, true, "normal", "edge"),
            new ActionDefinition(
                "toggleleft", "mouse",
                "n", "toggleleft", true, true, true, "normal", "edge"),
            new ActionDefinition(
                "wheelup", "mouse",
                ",", "wheelup", true, true, true, "normal"),
            new ActionDefinition(
                "wheeldown", "mouse",
                "m", "wheeldown", true, true, true, "normal"),
            new ActionDefinition(
                "wheelleft", "mouse",
                "u", "wheelleft", true, true, true, "normal"),
            new ActionDefinition(
                "wheelright", "mouse",
                "o", "wheelright", true, true, true, "normal"),

            new ActionDefinition(
                "gridmode", "modes",
                "g", "grid", true, true, true, "normal", "grid"),
            new ActionDefinition(
                "windowmode", "modes",
                "leftshift+g", null, true, false, true, "normal", "window"),
            new ActionDefinition(
                "hintmode", "modes",
                "f", "hint", true, true, true, "normal"),
            new ActionDefinition(
                "uihintmode", "modes",
                "leftalt+f", null, true, false, true, "normal"),
            new ActionDefinition(
                "hint2modifier", "modes",
                "leftshift", "hint2mod", false, false, true, "hint1"),
            new ActionDefinition(
                "screenselection", "modes",
                "c", "screenselection", true, true, true, "normal", "screen"),
            new ActionDefinition(
                "cancel", "modes",
                "esc", null, false, true, true, "grid", "hint1", "hint2", "screen", "ui"),
            new ActionDefinition(
                "hintback", "modes",
                "backspace", null, false, true, true, "hint1", "hint2", "screen", "ui"),

            new ActionDefinition(
                "hint1keys", "hints",
                "q|w|e|r|t|a|s|d|f|g|z|x|c|v|b|y|u|i|o|p|h|j|k|l|;|n|m|,|.|/",
                "hint1key", false, true, true, "hint1-default", "ui"),
            new ActionDefinition(
                "hint2keys", "hints",
                "q|w|e|r|u|i|o|p|a|s|d|f|j|k|l|;|z|x|c|v|m|,|.|/",
                "hint2key", false, true, true, "hint2-default"),
            new ActionDefinition(
                "extendedhint1keys", "hints",
                "1|2|3|4|5|6|7|8|9|0|q|w|e|r|t|y|u|i|o|p|a|s|d|f|g|h|j|k|l|;|z|x|c|v|b|n|m|,|.|/",
                "extendedhint1key", false, true, true, "hint1-extended"),
            new ActionDefinition(
                "extendedhint2keys", "hints",
                "1|2|3|4|5|6|7|8|9|0|q|w|e|r|t|y|u|i|o|p|a|s|d|f|g|h|j|k|l|;|z|x|c|v|b|n|m|,|.|/",
                "extendedhint2key", false, true, true, "hint2-extended"),
            new ActionDefinition(
                "screenhintkeys", "hints",
                "j|k|l|;|a|s|d|f|g", "hintscreenselectionkey", false, true, true, "screen"),

            new ActionDefinition(
                "navigateback", "automation",
                "h", "navigateback", true, true, true, "normal"),
            new ActionDefinition(
                "navigateforward", "automation",
                "y", "navigateforward", true, true, true, "normal"),
            new ActionDefinition(
                "arrowmodifier", "automation",
                "leftalt", "arrowmod", false, false, true, "normal-arrow"),
            new ActionDefinition(
                "alttab", "automation",
                "leftalt+tab", null, true, false, true, "idle", "normal")
        };

        public static ActionDefinition FindAction(string id)
        {
            return Actions.First(delegate(ActionDefinition action) { return action.Id == id; });
        }

        public static CategoryDefinition FindCategory(string id)
        {
            return Categories.First(delegate(CategoryDefinition category) { return category.Id == id; });
        }
    }
}
