using System;
using System.Collections.Generic;
using System.Globalization;
using Microsoft.Win32;

namespace MouseMasterConfigurator
{
    internal enum UiLanguage
    {
        Chinese,
        English
    }

    /// <summary>
    /// Provides every user-visible string in Chinese and English. The active
    /// language defaults to the OS UI culture and can be overridden by the user;
    /// the override is persisted under HKCU\Software\MouseMasterConfigurator.
    /// Catalog definitions (categories, actions, numeric settings) resolve their
    /// Title/Description through this class at access time, so switching the
    /// language re-renders the whole UI without a restart.
    /// </summary>
    internal static class L10n
    {
        private const string RegistryKeyPath = @"Software\MouseMasterConfigurator";
        private const string RegistryValueName = "Language";

        private static UiLanguage language = DetectDefaultLanguage();

        public static UiLanguage Language
        {
            get { return language; }
            set { language = value; }
        }

        /// <summary>
        /// Self-tests disable persistence so clicking the language switcher from a
        /// headless test never touches the user's registry.
        /// </summary>
        public static bool PersistenceEnabled { get; set; }

        static L10n()
        {
            PersistenceEnabled = true;
        }

        public static string T(string key)
        {
            string value;
            Dictionary<string, string> primary = language == UiLanguage.English ? English : Chinese;
            Dictionary<string, string> fallback = language == UiLanguage.English ? Chinese : English;
            if (primary.TryGetValue(key, out value))
                return value;
            if (fallback.TryGetValue(key, out value))
                return value;
            return key;
        }

        public static string F(string key, object arg0)
        {
            return string.Format(CultureInfo.InvariantCulture, T(key), arg0);
        }

        public static string F(string key, object arg0, object arg1)
        {
            return string.Format(CultureInfo.InvariantCulture, T(key), arg0, arg1);
        }

        public static string F(string key, params object[] args)
        {
            return string.Format(CultureInfo.InvariantCulture, T(key), args);
        }

        /// <summary>Reads the persisted language; falls back to the OS UI culture.</summary>
        public static UiLanguage LoadPreference()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RegistryKeyPath, false))
                {
                    if (key != null)
                    {
                        string value = key.GetValue(RegistryValueName) as string;
                        if (string.Equals(value, "en-US", StringComparison.OrdinalIgnoreCase))
                            return UiLanguage.English;
                        if (string.Equals(value, "zh-CN", StringComparison.OrdinalIgnoreCase))
                            return UiLanguage.Chinese;
                    }
                }
            }
            catch
            {
            }
            return DetectDefaultLanguage();
        }

        public static void SavePreference()
        {
            if (!PersistenceEnabled)
                return;
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RegistryKeyPath))
                {
                    if (key != null)
                    {
                        key.SetValue(
                            RegistryValueName,
                            language == UiLanguage.English ? "en-US" : "zh-CN");
                    }
                }
            }
            catch
            {
            }
        }

        private static UiLanguage DetectDefaultLanguage()
        {
            string name = CultureInfo.CurrentUICulture.Name;
            return name.StartsWith("zh", StringComparison.OrdinalIgnoreCase)
                ? UiLanguage.Chinese
                : UiLanguage.English;
        }

        private static readonly Dictionary<string, string> Chinese =
            new Dictionary<string, string>(StringComparer.Ordinal)
            {
                { "app.title", "MouseMaster 个性化配置" },
                { "sidebar.subtitle", "个性化配置" },
                { "sidebar.file", "当前配置文件" },
                { "search.cue", "搜索操作或说明" },

                { "focus.title", "专注模式" },
                { "focus.on", "已启用：普通及子模式会吸收全部键盘事件" },
                { "focus.off", "激活后吞掉全部键盘事件，并停用对外键盘宏" },

                { "category.general.title", "基础" },
                { "category.general.description", "激活、退出与全局行为" },
                { "category.movement.title", "移动" },
                { "category.movement.description", "方向、速度与边缘定位" },
                { "category.mouse.title", "鼠标与滚轮" },
                { "category.mouse.description", "点击、拖拽和四向滚动" },
                { "category.modes.title", "定位模式" },
                { "category.modes.description", "网格、窗口、Hint 与多屏操作" },
                { "category.hints.title", "Hint 键盘" },
                { "category.hints.description", "标签选择键与撤销键" },
                { "category.automation.title", "重映射" },
                { "category.automation.description", "前进后退、方向键和 Alt-Tab" },

                { "num.mouse-max-velocity.title", "鼠标最高速度" },
                { "num.mouse-max-velocity.description", "调整普通档连续移动的速度上限；慢速、快速和超慢速分支保持不变。" },
                { "num.mouse-acceleration.title", "鼠标加速度" },
                { "num.mouse-acceleration.description", "调整普通档达到最高速度的快慢；设为 0 可关闭加速。" },
                { "num.wheel-max-velocity.title", "滚轮最高速度" },
                { "num.wheel-max-velocity.description", "调整普通档连续滚动的速度上限；速度修饰键分支保持不变。" },
                { "num.wheel-acceleration.title", "滚轮加速度" },
                { "num.wheel-acceleration.description", "调整普通档达到滚轮最高速度的快慢；设为 0 可关闭加速。" },

                { "action.activate.title", "激活 / 切换普通模式" },
                { "action.activate.description", "从空闲模式进入键盘鼠标模式；在普通模式中再次按下则退出。" },
                { "action.exit.title", "退出到空闲模式" },
                { "action.exit.description", "在普通、网格、二级 Hint 和屏幕选择模式中立即退出。" },
                { "action.clickdisable.title", "左键单击后退出" },
                { "action.clickdisable.description", "按下时按住左键，松开时完成单击并返回空闲模式。" },
                { "action.passthroughexit.title", "退出并保留原快捷键" },
                { "action.passthroughexit.description", "退出普通模式，同时允许当前应用继续收到这些快捷键。" },
                { "action.moveup.title", "向上移动" },
                { "action.moveup.description", "普通模式连续移动；也用于边缘、网格和窗口定位。" },
                { "action.movedown.title", "向下移动" },
                { "action.movedown.description", "普通模式连续移动；也用于边缘、网格和窗口定位。" },
                { "action.moveleft.title", "向左移动" },
                { "action.moveleft.description", "普通模式连续移动；也用于边缘、网格和窗口定位。" },
                { "action.moveright.title", "向右移动" },
                { "action.moveright.description", "普通模式连续移动；也用于边缘、网格和窗口定位。" },
                { "action.fast.title", "快速移动" },
                { "action.fast.description", "按住后提高鼠标和滚轮的最高速度与加速度。" },
                { "action.slow.title", "慢速移动" },
                { "action.slow.description", "按住后降低鼠标和滚轮最高速度。" },
                { "action.superslow.title", "超慢速移动" },
                { "action.superslow.description", "按住后进入精细定位速度。" },
                { "action.edge.title", "屏幕边缘模式" },
                { "action.edge.description", "按住并配合方向键，跳到当前屏幕有效区域边缘。" },
                { "action.leftbutton.title", "鼠标左键" },
                { "action.leftbutton.description", "支持按住和拖拽；不带修饰键时仍兼容 Ctrl+单击。" },
                { "action.middlebutton.title", "鼠标中键" },
                { "action.middlebutton.description", "按下和释放对应鼠标中键。" },
                { "action.rightbutton.title", "鼠标右键" },
                { "action.rightbutton.description", "支持按住和拖拽；不带修饰键时仍兼容 Ctrl+单击。" },
                { "action.toggleleft.title", "锁定 / 释放鼠标左键" },
                { "action.toggleleft.description", "适合不持续按键的长距离拖拽。" },
                { "action.wheelup.title", "向上滚动" },
                { "action.wheelup.description", "按住开始滚动，松开停止；支持组合键和备用键。" },
                { "action.wheeldown.title", "向下滚动" },
                { "action.wheeldown.description", "按住开始滚动，松开停止；支持组合键和备用键。" },
                { "action.wheelleft.title", "向左滚动" },
                { "action.wheelleft.description", "可以设置为 Shift + U 等组合键。" },
                { "action.wheelright.title", "向右滚动" },
                { "action.wheelright.description", "可以设置为 Shift + O 等组合键。" },
                { "action.gridmode.title", "屏幕网格模式" },
                { "action.gridmode.description", "进入网格；在网格中再次使用该键可返回普通模式。" },
                { "action.windowmode.title", "活动窗口定位模式" },
                { "action.windowmode.description", "按住组合中的修饰键进入；主键在窗口模式中用于跳到中心。" },
                { "action.hintmode.title", "屏幕 Hint" },
                { "action.hintmode.description", "在活动屏幕显示位置标签。" },
                { "action.uihintmode.title", "UI Hint" },
                { "action.uihintmode.description", "标记活动窗口中由 Windows UI Automation 暴露的控件。" },
                { "action.hint2modifier.title", "二级 Hint 修饰键" },
                { "action.hint2modifier.description", "一级 Hint 选择完成时按住它，进入局部放大的二级 Hint。" },
                { "action.screenselection.title", "屏幕选择" },
                { "action.screenselection.description", "为每块显示器显示一个标签并移动鼠标。" },
                { "action.cancel.title", "取消 / 返回" },
                { "action.cancel.description", "用于退出网格、Hint、屏幕选择和 UI Hint。" },
                { "action.hintback.title", "Hint 撤销 / 返回" },
                { "action.hintback.description", "撤销标签输入、返回上一级 Hint，或退出屏幕选择。" },
                { "action.hint1keys.title", "一级 / UI Hint 选择键" },
                { "action.hint1keys.description", "常规分辨率下生成一级屏幕 Hint，并用于 UI Hint。" },
                { "action.hint2keys.title", "二级 Hint 选择键" },
                { "action.hint2keys.description", "常规分辨率下生成二级精确定位标签。" },
                { "action.extendedhint1keys.title", "一级 Hint 扩展选择键" },
                { "action.extendedhint1keys.description", "4K、QHD 和超宽屏覆盖配置使用的 40 键集合。" },
                { "action.extendedhint2keys.title", "二级 Hint 扩展选择键" },
                { "action.extendedhint2keys.description", "高分辨率二级 Hint 使用的 40 键集合。" },
                { "action.screenhintkeys.title", "显示器标签选择键" },
                { "action.screenhintkeys.description", "按显示器顺序分配标签；最多提供九个快捷键。" },
                { "action.navigateback.title", "应用后退" },
                { "action.navigateback.description", "普通模式中向当前应用发送 Alt + Left Arrow；专注模式会停用输出。" },
                { "action.navigateforward.title", "应用前进" },
                { "action.navigateforward.description", "普通模式中向当前应用发送 Alt + Right Arrow；专注模式会停用输出。" },
                { "action.arrowmodifier.title", "方向键映射修饰键" },
                { "action.arrowmodifier.description", "按住后，当前移动方向键会输出系统方向键；专注模式中停用。" },
                { "action.alttab.title", "Alt-Tab 后自动居中" },
                { "action.alttab.description", "窗口切换结束后将鼠标移动到新活动窗口中心。" },

                { "search.title", "搜索结果" },
                { "search.description", "匹配“{0}”的操作" },
                { "section.count.one", "{0} 项设置" },
                { "section.count.many", "{0} 项设置" },
                { "alttab.title", "启用 Alt-Tab 自动居中" },
                { "alttab.description", "关闭后保留其他重映射，但不再监听窗口切换。" },
                { "row.empty", "没有找到匹配的操作。" },

                { "binding.none", "点击设置快捷键" },
                { "binding.more", "  ·  共 {0} 项" },
                { "binding.clearTip", "清除该操作的全部快捷键" },

                { "button.restore", "恢复默认" },
                { "button.reload", "重新读取" },
                { "button.save", "保存并应用" },

                { "status.loading", "正在读取配置…" },
                { "status.ready", "已连接配置文件，所有修改会在保存后热加载。" },
                { "status.reloaded", "已重新读取配置文件。" },
                { "status.loadFail", "无法读取配置文件：{0}" },
                { "status.dirty", "有尚未保存的修改。" },
                { "status.updated", "已更新“{0}”，尚未保存。" },
                { "status.saved", "已保存并应用；MouseMaster 会自动重新加载配置。" },
                { "status.saveFail", "保存失败：{0}" },
                { "status.restored", "已恢复默认设置；原配置保存在 .gui-backup 文件中。" },
                { "status.restoreFail", "恢复失败：{0}" },
                { "status.conflict", "存在冲突，旧操作中的冲突按键已清除。" },

                { "conflict.message", "已保留“{0}”的新设置，并清除冲突：{1}" },
                { "conflict.detail", "“{0}”中的 {1}" },
                { "conflict.joinChords", "、" },
                { "conflict.joinDetails", "；" },

                { "msg.noSet", "无法设置快捷键" },
                { "msg.conflict", "按键冲突已自动处理" },
                { "msg.readFail", "读取配置失败" },
                { "msg.external", "检测到外部修改" },
                { "msg.external.body", "配置文件在本程序之外发生了变化。是否把当前 GUI 设置合并到最新文件？" },
                { "msg.saveFail", "保存配置失败" },
                { "msg.discard", "放弃未保存修改" },
                { "msg.discard.body", "重新读取会丢弃尚未保存的 GUI 修改。是否继续？" },
                { "msg.restore", "恢复默认设置" },
                { "msg.restore.body", "将立即把 mousemaster.properties 恢复为配置器内置的原始默认版本。\n\n当前文件会先保存为 mousemaster.properties.gui-backup。" },
                { "msg.restoreFail", "恢复默认失败" },
                { "msg.unsaved", "未保存修改" },
                { "msg.unsaved.body", "还有尚未保存的修改。确定关闭吗？" },

                { "error.fatal", "MouseMaster 配置器错误" },
                { "error.unknown", "发生未知错误。" },
                { "error.noConfig", "找不到 mousemaster.properties。" },

                { "dialog.title", "设置快捷键" },
                { "dialog.capture", "点击后按下快捷键" },
                { "dialog.capturing", "请按下快捷键…" },
                { "dialog.hintModifiers", "支持 Ctrl、Shift、Alt、Win 组合；单独松开修饰键可绑定修饰键本身。" },
                { "dialog.hintSingle", "此操作仅支持单键。" },
                { "dialog.hintListening", "正在监听键盘。Esc、Tab、Enter 也可以直接作为快捷键。" },
                { "dialog.hintReserved", "F24 由配置器保留用于表示已清空的绑定，请选择其他按键。" },
                { "dialog.hintRejectCombo", "此操作仅支持单键，请松开修饰键后重试。" },
                { "dialog.captureMore", "继续添加快捷键" },
                { "dialog.captureRedo", "重新设置快捷键" },
                { "dialog.hintMore", "可以继续添加备用键；保存后会自动处理冲突。" },
                { "dialog.hintDone", "保存后会自动检查并处理冲突。" },
                { "dialog.remove", "删除选中项" },
                { "dialog.clear", "全部清除" },
                { "dialog.cancel", "取消" },
                { "dialog.save", "使用这些快捷键" },
                { "dialog.modifierPending", "{0}（松开以使用）" },

                { "engine.activateRequired", "至少需要保留一个激活快捷键。" },
                { "engine.windowModifier", "活动窗口定位模式必须包含至少一个修饰键。" },
                { "engine.altTabModifier", "Alt-Tab 自动居中需要一个包含修饰键的快捷键。" },
                { "engine.range", "{0}必须在 {1} 到 {2} 之间。" },
                { "engine.singleOnly", "{0} 仅支持单键，不支持组合修饰键。" },
                { "engine.required", "{0} 是必需操作，不能清空。" }
            };

        private static readonly Dictionary<string, string> English =
            new Dictionary<string, string>(StringComparer.Ordinal)
            {
                { "app.title", "MouseMaster Configurator" },
                { "sidebar.subtitle", "Configurator" },
                { "sidebar.file", "Current configuration file" },
                { "search.cue", "Search actions or descriptions" },

                { "focus.title", "Focus Mode" },
                { "focus.on", "Enabled: normal and sub-modes absorb all keyboard events" },
                { "focus.off", "While active, swallows all keyboard events and disables outbound macros" },

                { "category.general.title", "General" },
                { "category.general.description", "Activation, exit and global behavior" },
                { "category.movement.title", "Movement" },
                { "category.movement.description", "Direction, speed and edge positioning" },
                { "category.mouse.title", "Mouse & Wheel" },
                { "category.mouse.description", "Clicks, drags and four-way scrolling" },
                { "category.modes.title", "Position Modes" },
                { "category.modes.description", "Grid, window, hint and multi-screen modes" },
                { "category.hints.title", "Hint Keys" },
                { "category.hints.description", "Label selection and undo keys" },
                { "category.automation.title", "Remapping" },
                { "category.automation.description", "Back/forward, arrow keys and Alt-Tab" },

                { "num.mouse-max-velocity.title", "Mouse Max Speed" },
                { "num.mouse-max-velocity.description", "Normal-tier continuous-move speed limit; slow, fast and super-slow branches are unchanged." },
                { "num.mouse-acceleration.title", "Mouse Acceleration" },
                { "num.mouse-acceleration.description", "How fast the normal tier reaches max speed; set to 0 to disable acceleration." },
                { "num.wheel-max-velocity.title", "Wheel Max Speed" },
                { "num.wheel-max-velocity.description", "Normal-tier continuous-scroll speed limit; speed-modifier branches are unchanged." },
                { "num.wheel-acceleration.title", "Wheel Acceleration" },
                { "num.wheel-acceleration.description", "How fast the wheel reaches max speed; set to 0 to disable acceleration." },

                { "action.activate.title", "Activate / Toggle Normal Mode" },
                { "action.activate.description", "Enter keyboard-mouse mode from idle; press again in normal mode to exit." },
                { "action.exit.title", "Exit to Idle Mode" },
                { "action.exit.description", "Exit immediately from normal, grid, level-2 hint and screen-selection modes." },
                { "action.clickdisable.title", "Exit After Left Click" },
                { "action.clickdisable.description", "Holds the left button while pressed; clicks and returns to idle on release." },
                { "action.passthroughexit.title", "Exit & Keep Original Shortcut" },
                { "action.passthroughexit.description", "Exit normal mode while letting the current app receive these shortcuts." },
                { "action.moveup.title", "Move Up" },
                { "action.moveup.description", "Continuous movement in normal mode; also used for edge, grid and window positioning." },
                { "action.movedown.title", "Move Down" },
                { "action.movedown.description", "Continuous movement in normal mode; also used for edge, grid and window positioning." },
                { "action.moveleft.title", "Move Left" },
                { "action.moveleft.description", "Continuous movement in normal mode; also used for edge, grid and window positioning." },
                { "action.moveright.title", "Move Right" },
                { "action.moveright.description", "Continuous movement in normal mode; also used for edge, grid and window positioning." },
                { "action.fast.title", "Fast Move" },
                { "action.fast.description", "Hold to raise mouse and wheel maximum speed and acceleration." },
                { "action.slow.title", "Slow Move" },
                { "action.slow.description", "Hold to lower mouse and wheel maximum speed." },
                { "action.superslow.title", "Super-Slow Move" },
                { "action.superslow.description", "Hold for fine positioning speed." },
                { "action.edge.title", "Screen Edge Mode" },
                { "action.edge.description", "Hold with arrow keys to jump to an edge of the current screen's usable area." },
                { "action.leftbutton.title", "Left Mouse Button" },
                { "action.leftbutton.description", "Supports hold and drag; Ctrl+click stays compatible without modifiers." },
                { "action.middlebutton.title", "Middle Mouse Button" },
                { "action.middlebutton.description", "Press and release map to the middle mouse button." },
                { "action.rightbutton.title", "Right Mouse Button" },
                { "action.rightbutton.description", "Supports hold and drag; Ctrl+click stays compatible without modifiers." },
                { "action.toggleleft.title", "Lock / Release Left Button" },
                { "action.toggleleft.description", "For long drags without holding a key down." },
                { "action.wheelup.title", "Scroll Up" },
                { "action.wheelup.description", "Hold to scroll, release to stop; supports combos and alternate keys." },
                { "action.wheeldown.title", "Scroll Down" },
                { "action.wheeldown.description", "Hold to scroll, release to stop; supports combos and alternate keys." },
                { "action.wheelleft.title", "Scroll Left" },
                { "action.wheelleft.description", "Can be set to a combo such as Shift + U." },
                { "action.wheelright.title", "Scroll Right" },
                { "action.wheelright.description", "Can be set to a combo such as Shift + O." },
                { "action.gridmode.title", "Screen Grid Mode" },
                { "action.gridmode.description", "Enter the grid; using the key again in the grid returns to normal mode." },
                { "action.windowmode.title", "Window Position Mode" },
                { "action.windowmode.description", "Hold the combo's modifier to enter; the main key jumps to the window center." },
                { "action.hintmode.title", "Screen Hint" },
                { "action.hintmode.description", "Show position labels on the active screen." },
                { "action.uihintmode.title", "UI Hint" },
                { "action.uihintmode.description", "Label controls exposed by Windows UI Automation in the active window." },
                { "action.hint2modifier.title", "Level-2 Hint Modifier" },
                { "action.hint2modifier.description", "Hold while a level-1 hint completes to enter a zoomed level-2 hint." },
                { "action.screenselection.title", "Screen Selection" },
                { "action.screenselection.description", "Show a label per monitor and move the mouse to it." },
                { "action.cancel.title", "Cancel / Back" },
                { "action.cancel.description", "Exit grid, hint, screen selection and UI hint." },
                { "action.hintback.title", "Hint Undo / Back" },
                { "action.hintback.description", "Undo label input, go back one hint level, or exit screen selection." },
                { "action.hint1keys.title", "Level-1 / UI Hint Keys" },
                { "action.hint1keys.description", "Generate level-1 screen hints at regular resolutions; also used for UI hints." },
                { "action.hint2keys.title", "Level-2 Hint Keys" },
                { "action.hint2keys.description", "Generate level-2 precision labels at regular resolutions." },
                { "action.extendedhint1keys.title", "Extended Level-1 Hint Keys" },
                { "action.extendedhint1keys.description", "40-key set used by 4K, QHD and ultrawide overlay configurations." },
                { "action.extendedhint2keys.title", "Extended Level-2 Hint Keys" },
                { "action.extendedhint2keys.description", "40-key set used for high-resolution level-2 hints." },
                { "action.screenhintkeys.title", "Monitor Label Keys" },
                { "action.screenhintkeys.description", "Assign labels in monitor order; up to nine shortcuts." },
                { "action.navigateback.title", "App Back" },
                { "action.navigateback.description", "Sends Alt + Left Arrow to the current app; focus mode disables output." },
                { "action.navigateforward.title", "App Forward" },
                { "action.navigateforward.description", "Sends Alt + Right Arrow to the current app; focus mode disables output." },
                { "action.arrowmodifier.title", "Arrow-Key Mapping Modifier" },
                { "action.arrowmodifier.description", "Hold to make movement keys emit system arrow keys; disabled in focus mode." },
                { "action.alttab.title", "Center After Alt-Tab" },
                { "action.alttab.description", "Move the mouse to the center of the newly active window after switching." },

                { "search.title", "Search Results" },
                { "search.description", "Actions matching \"{0}\"" },
                { "section.count.one", "{0} setting" },
                { "section.count.many", "{0} settings" },
                { "alttab.title", "Enable Auto-Center After Alt-Tab" },
                { "alttab.description", "Keeps other remaps but stops tracking window switches." },
                { "row.empty", "No matching actions found." },

                { "binding.none", "Click to set a shortcut" },
                { "binding.more", "  ·  {0} total" },
                { "binding.clearTip", "Clear all shortcuts for this action" },

                { "button.restore", "Restore Defaults" },
                { "button.reload", "Reload" },
                { "button.save", "Save & Apply" },

                { "status.loading", "Reading configuration…" },
                { "status.ready", "Connected to the configuration file; changes hot-reload after saving." },
                { "status.reloaded", "Configuration reloaded." },
                { "status.loadFail", "Failed to read the configuration file: {0}" },
                { "status.dirty", "There are unsaved changes." },
                { "status.updated", "Updated \"{0}\" (not saved yet)." },
                { "status.saved", "Saved and applied; MouseMaster reloads the configuration automatically." },
                { "status.saveFail", "Save failed: {0}" },
                { "status.restored", "Defaults restored; the previous configuration is in the .gui-backup file." },
                { "status.restoreFail", "Restore failed: {0}" },
                { "status.conflict", "Conflicts found; conflicting keys on older actions were cleared." },

                { "conflict.message", "Kept the new setting for \"{0}\" and cleared conflicts: {1}" },
                { "conflict.detail", "{1} from \"{0}\"" },
                { "conflict.joinChords", ", " },
                { "conflict.joinDetails", "; " },

                { "msg.noSet", "Cannot Set Shortcut" },
                { "msg.conflict", "Key Conflicts Resolved Automatically" },
                { "msg.readFail", "Failed to Read Configuration" },
                { "msg.external", "External Changes Detected" },
                { "msg.external.body", "The configuration file changed outside this program. Merge the current GUI settings into the latest file?" },
                { "msg.saveFail", "Failed to Save Configuration" },
                { "msg.discard", "Discard Unsaved Changes" },
                { "msg.discard.body", "Reloading discards unsaved GUI changes. Continue?" },
                { "msg.restore", "Restore Default Settings" },
                { "msg.restore.body", "mousemaster.properties will immediately be restored to the configurator's built-in defaults.\n\nThe current file is first saved as mousemaster.properties.gui-backup." },
                { "msg.restoreFail", "Failed to Restore Defaults" },
                { "msg.unsaved", "Unsaved Changes" },
                { "msg.unsaved.body", "There are unsaved changes. Close anyway?" },

                { "error.fatal", "MouseMaster Configurator Error" },
                { "error.unknown", "An unknown error occurred." },
                { "error.noConfig", "mousemaster.properties was not found." },

                { "dialog.title", "Set Shortcut" },
                { "dialog.capture", "Click, then press the shortcut" },
                { "dialog.capturing", "Press a shortcut…" },
                { "dialog.hintModifiers", "Ctrl, Shift, Alt and Win combos are supported; release a modifier alone to bind the modifier itself." },
                { "dialog.hintSingle", "This action supports a single key only." },
                { "dialog.hintListening", "Listening… Esc, Tab and Enter can also be used directly as shortcuts." },
                { "dialog.hintReserved", "F24 is reserved by the configurator to mark cleared bindings; choose another key." },
                { "dialog.hintRejectCombo", "Single keys only; release the modifiers and try again." },
                { "dialog.captureMore", "Add Another Shortcut" },
                { "dialog.captureRedo", "Set Shortcut Again" },
                { "dialog.hintMore", "You can keep adding alternates; conflicts are resolved on save." },
                { "dialog.hintDone", "Conflicts are checked and resolved on save." },
                { "dialog.remove", "Remove Selected" },
                { "dialog.clear", "Clear All" },
                { "dialog.cancel", "Cancel" },
                { "dialog.save", "Use These Shortcuts" },
                { "dialog.modifierPending", "{0} (release to use)" },

                { "engine.activateRequired", "At least one activation shortcut is required." },
                { "engine.windowModifier", "Window position mode requires at least one modifier." },
                { "engine.altTabModifier", "Auto-center after Alt-Tab requires a shortcut with a modifier." },
                { "engine.range", "{0} must be between {1} and {2}." },
                { "engine.singleOnly", "{0} supports single keys only, no modifier combos." },
                { "engine.required", "{0} is required and cannot be cleared." }
            };
    }
}
