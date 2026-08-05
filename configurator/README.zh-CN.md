# MouseMaster Windows 个性化配置器

[English README](README.md)

这是 `Rezetyan/mousemaster` fork 提供的社区 GUI，不是上游 MouseMaster 的官方组件。
配置器面向 MouseMaster v89 properties，使用 .NET Framework WinForms 编写，不依赖
第三方 NuGet 包。界面支持简体中文和 English，默认跟随系统语言，可在侧栏底部随时切换；
选择结果保存在 `HKCU\Software\MouseMasterConfigurator`。

本目录包含完整源码、构建脚本和回归测试。编译产生的 EXE、PDB、日志和测试运行时不会
提交到 Git。

## 功能边界

- 六个分类中的 37 项操作均可编辑对应按键。
- 可以编辑鼠标最高速度、鼠标加速度、滚轮最高速度和滚轮加速度。
- 按操作能力支持单键、组合键、备用键和清空。
- 同一模式作用域中出现完全相同的快捷键时，新设置保留，旧绑定清除并提示。
- 保存时保留未由 GUI 管理的属性和普通注释。
- 使用注释形式的 `mmcfg` 元数据准确回读 GUI 状态。
- 每次写入前生成单份滚动备份 `mousemaster.properties.gui-backup`。
- 可以恢复构建时嵌入的默认配置。
- 专注模式为八个键盘鼠标模式分别生成兜底吞键规则，同时保留该模式已有的非吞键组合。
- Alt-Tab 自动居中可以独立开关并修改触发组合。

四个速度输入只替换动态属性的普通档分支，并保留初始速度及慢速、快速、超慢速分支。
最高速度范围为 `1..100000`，加速度范围为 `0..100000`；加速度设为 0 可关闭加速。
GUI 不负责编辑初始速度、各修饰键分支、颜色、网格尺寸、Hint 密度等其他数值参数。

## 目录结构

```text
configurator/
  app.manifest
  build.ps1
  LICENSE
  README.md
  src/
    BindingCatalog.cs
    ConfigDocument.cs
    ConfiguratorEngine.cs
    DpiHelper.cs
    KeyCaptureDialog.cs
    Localization.cs
    MainForm.cs
    Models.cs
    Program.cs
    SelfTests.cs
    ThemeAndControls.cs
  tests/
    FocusModeIntegrationTest.cs
```

主要职责：

| 文件 | 职责 |
|---|---|
| `BindingCatalog.cs` | 六个分类、37 项操作、4 项数值设置、默认值和冲突作用域 |
| `Models.cs` | 快捷键模型、数值设置、状态模型与冲突解析 |
| `ConfigDocument.cs` | 保留式 properties 编辑、元数据、原子写入和嵌入默认值 |
| `ConfiguratorEngine.cs` | 把 GUI 状态转换为实际 MouseMaster 属性与逐模式专注兜底规则 |
| `DpiHelper.cs` | 96 DPI 设计尺寸到当前显示器 DPI 的像素换算 |
| `Localization.cs` | 中英文案字典、当前语言、注册表语言偏好 |
| `KeyCaptureDialog.cs` | Windows 按键捕获和 v89 键名转换 |
| `MainForm.cs` | 主界面、按键与数值输入、保存、重新读取、冲突提示和恢复流程 |
| `SelfTests.cs` | 无测试框架依赖的内置回归测试 |
| `FocusModeIntegrationTest.cs` | 向真实前台窗口注入按键的 v89 端到端测试 |

## 环境要求

- Windows 10 或 Windows 11。
- .NET Framework 4.x；当前在 .NET Framework 4.8 上构建验证。
- 64 位 .NET Framework C# 编译器：
  `C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`。
- 仓库中存在 `configuration/neo-mousekeys-ijkl.properties`，构建时将其嵌入为恢复默认值。
- 仅运行端到端测试时，需要在仓库根目录放置 MouseMaster v89 `mousemaster.exe`。

`app.manifest` 使用 `asInvoker`，配置器不会主动请求管理员权限，并声明 Per-Monitor
V2 DPI 感知。所有界面像素尺寸按 96 DPI 设计，经 `DpiHelper` 换算到当前显示器 DPI，
字体以磅为单位随 DPI 自动放大，因此在 125% / 150% 等缩放下文字不会被裁剪；窗口移到
不同 DPI 的显示器时会按新 DPI 重建界面。

## 构建

在根目录打开 PowerShell：

```powershell
Set-Location C:\path\to\mousemaster
.\configurator\build.ps1
```

构建脚本会：

1. 按文件名排序编译 `configurator/src/*.cs`。
2. 引用 .NET Framework 自带的 System、Drawing 和 WinForms 程序集。
3. 把 `configuration/neo-mousekeys-ijkl.properties` 作为
   `MouseMasterConfigurator.DefaultProperties` 嵌入资源。
4. 在根目录生成：

```text
MouseMasterConfigurator.exe
MouseMasterConfigurator.pdb
```

构建不会修改任何 properties。两个构建产物已由根目录 `.gitignore` 忽略。

## 运行

默认打开与 EXE 同目录的 `mousemaster.properties`。开发时先复制一份配置，避免直接修改
仓库跟踪的示例：

```powershell
Copy-Item .\configuration\neo-mousekeys-ijkl.properties .\mousemaster.properties
.\MouseMasterConfigurator.exe
```

打开指定配置：

```powershell
.\MouseMasterConfigurator.exe --config "D:\path\to\mousemaster.properties"
```

目标文件必须已存在。配置器只写入指定 properties，不负责启动 MouseMaster。根目录
开发副本 `mousemaster.properties` 及其 `.gui-backup` 已被 Git 忽略。

## 内置自检

WinForms 可执行文件提供无界面的自检入口：

```powershell
.\MouseMasterConfigurator.exe --self-test `
  .\configurator\build\self-test.log `
  .\configurator\build\acceptance.properties
```

参数依次为：

1. 必需标志 `--self-test`。
2. 可选测试报告路径；省略时写到可执行文件目录。
3. 可选验收配置路径；仅在全部断言通过时写出。

因为主程序是 WinForms `winexe`，自动化脚本应显式等待进程退出，再检查退出码和日志。
当前自检覆盖：

- 个性化 Vim 移动键、`Ctrl+M` 激活和四向滚动输出。
- 组合键解析、左右修饰键、无障碍显示文本和 F24 保留规则。
- 完整冲突报告与“新设置优先”行为。
- 37 项目录操作都会改变真实配置正文。
- 四项速度设置的导入、范围检查、动态分支保留、元数据回读和初始速度保护。
- v89 特殊键名和小键盘无布局别名。
- 专注模式八个逐模式兜底别名、非吞键组合排除、关闭后的清理与宏恢复。
- 元数据回读、属性唯一性和非空检查。
- 原子恢复、默认内容一致性及 `.gui-backup` 内容。
- 标题与说明间距、专注说明与开关间距、开关不透明绘制，以及四个数值框的界面布局。
- 中英文界面即时切换、切换后标题与导航文案，以及两种语言下所有固定尺寸标签的
  文字适配检查（防止高 DPI 或长文案裁剪）。

成功时日志最后一行形如：

```text
PASS: 299 assertions
```

## v89 专注模式集成测试

`FocusModeIntegrationTest.cs` 会启动真实 `mousemaster.exe`，创建一个输入接收窗口，并验证：

1. 空闲模式中的 `X` 能到达活动应用。
2. `Ctrl+M` 进入键盘鼠标模式。
3. `F2` 仍到达活动应用，并作为已配置的透传退出组合切换回空闲模式。
4. `F2` 后的 `B` 能到达应用，证明模式切换已经执行。
5. 再次进入后，字母、数字和 `Ctrl+S` 均被吞掉。
6. `Q` 退出后，`B` 再次到达活动应用。

这里的 `Ctrl+M` 是自检生成的隔离验收配置所使用的激活键，不代表官方
`neo-mousekeys-ijkl` 配置的默认激活键。

编译测试：

```powershell
$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:exe /platform:anycpu /optimize+ `
  /out:.\configurator\build\FocusModeIntegrationTest.exe `
  /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll `
  .\configurator\tests\FocusModeIntegrationTest.cs
```

测试必须使用隔离目录，其中配置文件名必须是 `mousemaster.properties`。不要把自动化验收
配置覆盖到根目录正式配置：

```powershell
$runtime = '.\configurator\build\v89-runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
Copy-Item .\mousemaster.exe "$runtime\mousemaster.exe" -Force
Copy-Item .\configurator\build\acceptance.properties `
  "$runtime\mousemaster.properties" -Force

.\configurator\build\FocusModeIntegrationTest.exe `
  "$runtime\mousemaster.exe" `
  "$runtime\mousemaster.properties"
```

测试会短暂把自己的接收窗口设为前台并注入键盘事件，完成或失败后都会终止它启动的
MouseMaster 子进程。运行期间不要同时启动另一份 MouseMaster。

成功输出：

```text
OK: idle-mode input reaches the active application
OK: focus mode preserves a configured pass-through combo
OK: the configured pass-through combo still performs its mode switch
OK: focus mode eats ordinary typing
OK: focus mode eats application shortcuts
OK: input resumes after leaving keyboard-mouse mode
PASS: focus mode preserved configured combos, swallowed unhandled input, and restored input after Q.
```

## 保存格式与兼容性

GUI 管理的状态写入文件末尾、由注释包围的 `mmcfg` 元数据块。MouseMaster 会忽略该块。
再次保存前，配置器先移除旧元数据并基于磁盘最新正文重建，未知属性和普通注释会保留。

被清空的 MouseMaster 别名使用 `F24` 作为 v89 可解析的内部占位；捕获器因此禁止用户
分配 F24。小键盘键不能参与 v89 的 `.us-qwerty` 跨布局转换，生成器会自动把包含
`numpad*` 或 `numlock` 的别名迁移为无布局后缀形式。

专注模式为八个模式分别生成一个完整吞键别名，并从每个别名中排除该模式已有 `#`
非吞键组合所使用的按键和已按下前置条件。别名不带布局后缀，否则 v89 会尝试转换
小键盘键，并可能在非 `us-qwerty` 活动布局上拒绝整个配置。

普通模式的入口组合（模式切换、退出、透传、导航宏、滚轮启动）按"同主键互斥"生成：
无修饰键的绑定（裸 `+key` 在按住修饰键时也会匹配）会自动排除同主键绑定上的修饰键，
例如屏幕 Hint 设为 `Ctrl+F`、UI Hint 设为 `F` 时生成 `_{leftctrl} +f` 与
`^{leftctrl} +f`，二者不再同时命中。带修饰键的绑定本身已被 `_{...}` 严格语义
保护（多按一个键即不匹配），无需额外排除。

启用专注模式时，普通模式中会向操作系统发送按键的应用前进 / 后退、方向键映射和
Alt-Tab 监听会被注释。关闭专注模式后，前两类属性会恢复，Alt-Tab 监听则继续服从独立
的自动居中开关。Alt-Tab 自动居中关闭时，从入口到
`center-on-active-window-mode` 返回规则的完整属性链都会被注释，避免产生 v89
不允许的孤立模式。

## 发布前检查

至少执行以下检查：

1. 重新运行 `configurator/build.ps1`，确认编译退出码为 0。
2. 运行完整 `--self-test`，确认报告没有 `FAIL`。
3. 用未经手工修改的验收配置启动本机 v89，确认输出
   `Loaded configuration file mousemaster.properties`。
4. 运行专注模式集成测试并确认六项 `OK`。
5. 确认测试结束后没有遗留 MouseMaster、配置器或集成测试进程。
6. 确认 `git status` 中没有 EXE、PDB、个人 properties、备份、日志或测试运行时。

## 许可证

`configurator/` 目录中的新配置器源码采用 [MIT License](LICENSE)。该许可只覆盖此目录
中的新增内容，不改变仓库其他上游文件的授权状态。
