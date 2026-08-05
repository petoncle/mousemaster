# MouseMaster Windows GUI Configurator

[中文说明](README.zh-CN.md)

A community-maintained Windows GUI for editing MouseMaster properties files. It is not an
official component of the upstream MouseMaster project. The configurator targets the
MouseMaster v89 properties format, is written in .NET Framework WinForms, and has no
third-party NuGet dependencies. The UI is bilingual (Simplified Chinese and English),
follows the Windows display language by default, and can be switched at any time from the
drop-down at the bottom of the sidebar; the choice is stored in
`HKCU\Software\MouseMasterConfigurator`.

This directory contains the complete source code, the build script and the regression
tests. Compiled EXE, PDB, log and test runtime files are never committed to Git.

## Feature scope

- 37 actions in six categories can each be rebound.
- Mouse max velocity, mouse acceleration, wheel max velocity and wheel acceleration can
  be edited.
- Depending on the action, single keys, modifier chords, alternate bindings and clearing
  are supported.
- When the exact same shortcut already exists in the same mode scope, the new binding is
  kept, the old binding is cleared, and the change is reported.
- Saving preserves properties and plain comments the GUI does not manage, including
  properties introduced by newer MouseMaster versions.
- Comment-based `mmcfg` metadata records the exact GUI state for lossless reloading.
- A single rolling backup `mousemaster.properties.gui-backup` is written before every
  save.
- The default configuration embedded at build time can be restored on demand.
- Focus mode generates per-mode swallow-all fallback rules for the eight keyboard-mouse
  modes while preserving each mode's existing non-swallowing combos.
- Alt-Tab auto-centering can be toggled independently and its trigger chord edited.

The four velocity inputs replace only the default branch of the dynamic properties and
preserve the initial velocity and the slow / fast / super-slow branches. Max velocity
accepts `1..100000`, acceleration `0..100000`; setting an acceleration to 0 disables it.
The GUI does not edit initial velocities, per-modifier branches, colors, grid sizes,
hint density or other numeric parameters.

## Directory layout

```text
configurator/
  app.manifest
  build.ps1
  LICENSE
  README.md
  README.zh-CN.md
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

Main responsibilities:

| File | Responsibility |
|---|---|
| `BindingCatalog.cs` | Six categories, 37 actions, four numeric settings, defaults and conflict scopes |
| `Models.cs` | Shortcut model, numeric settings, state model and conflict resolution |
| `ConfigDocument.cs` | Preserving properties editing, metadata, atomic writes and the embedded default |
| `ConfiguratorEngine.cs` | Converts GUI state into actual MouseMaster properties and per-mode focus fallbacks |
| `DpiHelper.cs` | Converts 96-DPI design metrics to the current monitor DPI |
| `Localization.cs` | Chinese/English text dictionary, current language, registry language preference |
| `KeyCaptureDialog.cs` | Windows key capture and v89 key-name conversion |
| `MainForm.cs` | Main window, key and numeric inputs, save, reload, conflict prompts and restore flow |
| `SelfTests.cs` | Built-in regression tests with no test-framework dependency |
| `FocusModeIntegrationTest.cs` | End-to-end v89 test that injects keys into a real foreground window |

## Requirements

- Windows 10 or Windows 11.
- .NET Framework 4.x; currently built and verified on .NET Framework 4.8.
- The 64-bit .NET Framework C# compiler:
  `C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`.
- `configuration/neo-mousekeys-ijkl.properties` must exist in the repository; it is
  embedded at build time as the restore-default configuration.
- A MouseMaster v89 `mousemaster.exe` at the repository root is only needed to run the
  end-to-end test.

`app.manifest` uses `asInvoker` (the configurator never requests administrator rights)
and declares Per-Monitor V2 DPI awareness. All layout metrics are designed for 96 DPI
and converted by `DpiHelper` to the current monitor DPI; fonts scale automatically
because they are measured in points, so text is not clipped at 125% / 150% scaling.
Moving the window to a monitor with a different DPI rebuilds the layout for the new DPI.

## Build

Open PowerShell at the repository root:

```powershell
Set-Location C:\path\to\mousemaster
.\configurator\build.ps1
```

The build script:

1. Compiles `configurator/src/*.cs` ordered by file name.
2. References the System, Drawing and WinForms assemblies shipped with .NET Framework.
3. Embeds `configuration/neo-mousekeys-ijkl.properties` as the
   `MouseMasterConfigurator.DefaultProperties` resource.
4. Produces at the repository root:

```text
MouseMasterConfigurator.exe
MouseMasterConfigurator.pdb
```

The build never modifies any properties file. Both artifacts are covered by the root
`.gitignore`.

## Run

By default the configurator opens `mousemaster.properties` next to the EXE. During
development, copy a configuration first so the tracked samples are not modified:

```powershell
Copy-Item .\configuration\neo-mousekeys-ijkl.properties .\mousemaster.properties
.\MouseMasterConfigurator.exe
```

Open a specific configuration:

```powershell
.\MouseMasterConfigurator.exe --config "D:\path\to\mousemaster.properties"
```

The target file must already exist. The configurator only writes to the given
properties file; it does not start MouseMaster. The development copies
`mousemaster.properties` and its `.gui-backup` at the repository root are ignored by
Git.

## Built-in self-tests

The WinForms executable provides a headless self-test entry point:

```powershell
.\MouseMasterConfigurator.exe --self-test `
  .\configurator\build\self-test.log `
  .\configurator\build\acceptance.properties
```

The arguments are:

1. The required `--self-test` flag.
2. An optional test report path; defaults to the executable directory.
3. An optional acceptance configuration path, written only when every assertion passes.

Because the main program is a WinForms `winexe`, automation scripts should explicitly
wait for the process to exit and then check the exit code and the log. Current
coverage:

- Customized Vim movement keys, `Ctrl+M` activation and four-direction wheel output.
- Chord parsing, left/right modifiers, accessibility display text and the F24
  reservation rule.
- Complete conflict reporting and "new binding wins" behavior.
- All 37 catalog actions change the real configuration text.
- Import, range checking, dynamic-branch preservation, metadata round-trip and
  initial-velocity protection for the four velocity settings.
- v89 special key names and layout-independent numpad aliases.
- Focus mode's eight per-mode fallback aliases, non-swallowing combo exclusion, and
  cleanup plus macro restoration after focus mode is turned off.
- Metadata round-trip, property uniqueness and non-blank checks.
- Atomic restore, byte-identical default content and `.gui-backup` contents.
- Title/description spacing, focus description/toggle spacing, opaque toggle painting
  and the layout of the four numeric inputs.
- Instant Chinese/English UI switching, post-switch title and navigation text, and a
  text-fit check of every fixed-size label in both languages (guarding against
  high-DPI or long-text clipping).

On success the last log line looks like:

```text
PASS: 305 assertions
```

## v89 focus-mode integration test

`FocusModeIntegrationTest.cs` starts a real `mousemaster.exe`, creates an input
receiver window and verifies:

1. `X` in idle mode reaches the active application.
2. `Ctrl+M` enters keyboard-mouse mode.
3. `F2` still reaches the active application and switches back to idle mode as a
   configured pass-through exit combo.
4. The `B` after `F2` reaches the application, proving the mode switch happened.
5. After entering again, letters, digits and `Ctrl+S` are all swallowed.
6. After `Q` exits, `B` reaches the active application again.

`Ctrl+M` here is the activation key of the isolated acceptance configuration produced
by the self-test, not the default activation key of the official
`neo-mousekeys-ijkl` configuration.

Compile the test:

```powershell
$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:exe /platform:anycpu /optimize+ `
  /out:.\configurator\build\FocusModeIntegrationTest.exe `
  /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll `
  .\configurator\tests\FocusModeIntegrationTest.cs
```

The test must use an isolated directory in which the configuration file is named
`mousemaster.properties`. Do not overwrite the production configuration at the
repository root with the automated acceptance configuration:

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

The test briefly brings its receiver window to the foreground and injects keyboard
events; it kills the MouseMaster child process it started when it finishes or fails.
Do not run another MouseMaster instance while it runs.

Expected output:

```text
OK: idle-mode input reaches the active application
OK: focus mode preserves a configured pass-through combo
OK: the configured pass-through combo still performs its mode switch
OK: focus mode eats ordinary typing
OK: focus mode eats application shortcuts
OK: input resumes after leaving keyboard-mouse mode
PASS: focus mode preserved configured combos, swallowed unhandled input, and restored input after Q.
```

## Save format and compatibility

The GUI-managed state is written to an `mmcfg` metadata block at the end of the file,
surrounded by comments. MouseMaster ignores this block. Before every save the
configurator removes the old metadata and rebuilds from the latest on-disk content;
unknown properties and plain comments are preserved.

Cleared MouseMaster aliases use `F24` as an internal placeholder that v89 can parse,
so the capture dialog forbids assigning F24. Numpad keys cannot participate in v89's
`.us-qwerty` cross-layout conversion; the generator automatically migrates aliases
containing `numpad*` or `numlock` to the layout-independent form.

Focus mode generates a complete swallowing alias per mode for the eight modes, and
excludes from each alias the keys used by that mode's existing `#` non-swallowing
combos and pressed preconditions. The aliases carry no layout suffix, otherwise v89
would try to convert numpad keys and might reject the whole configuration on an
active layout other than `us-qwerty`.

Normal-mode entry combos (mode switches, exit / pass-through, navigation macros,
wheel start) are generated with mutual same-key exclusion: a modifier-free binding
(a bare `+key`, which would otherwise match even while modifiers are held)
automatically excludes the modifiers of same-key bindings. For example, with Screen
Hint on `Ctrl+F` and UI Hint on `F`, the generated combos are `_{leftctrl} +f` and
`^{leftctrl} +f`, which can no longer fire together. Bindings that have modifiers are
already protected by the strict `_{...}` semantics (any extra held key breaks the
match) and need no exclusion.

While focus mode is enabled, the normal-mode properties that send keys to the
operating system (navigate back / forward, arrow-key remapping and the Alt-Tab
listener) are commented out. When focus mode is disabled, the first two are restored,
while Alt-Tab continues to follow its own independent auto-centering toggle. When
Alt-Tab auto-centering is off, the full property chain from the entry point to the
`center-on-active-window-mode` return rules is commented out, avoiding the orphaned
modes that v89 forbids.

## Pre-release checks

Run at least the following checks:

1. Re-run `configurator/build.ps1` and confirm the compiler exit code is 0.
2. Run the full `--self-test` and confirm the report contains no `FAIL`.
3. Start a local v89 with the unmodified acceptance configuration and confirm it
   prints `Loaded configuration file mousemaster.properties`.
4. Run the focus-mode integration test and confirm all six `OK` lines.
5. Confirm no MouseMaster, configurator or integration test process is left running.
6. Confirm `git status` shows no EXE, PDB, personal properties, backup, log or test
   runtime files.

## License

The new configurator source code in `configurator/` is licensed under the
[MIT License](LICENSE). This license only covers the new content in this directory
and does not change the licensing of the other upstream files in the repository.
