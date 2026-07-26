$ErrorActionPreference = 'Stop'

$configuratorRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = Split-Path -Parent $configuratorRoot
$sourceRoot = Join-Path $configuratorRoot 'src'
$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$manifest = Join-Path $configuratorRoot 'app.manifest'
$defaultConfiguration = Join-Path $workspaceRoot 'configuration\neo-mousekeys-ijkl.properties'
$output = Join-Path $workspaceRoot 'MouseMasterConfigurator.exe'

if (-not (Test-Path -LiteralPath $compiler)) {
    throw "C# compiler not found: $compiler"
}

if (-not (Test-Path -LiteralPath $defaultConfiguration)) {
    throw "Default configuration was not found: $defaultConfiguration"
}

$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.cs' -File |
    Sort-Object Name |
    Select-Object -ExpandProperty FullName)

if ($sources.Count -eq 0) {
    throw "No C# sources found in $sourceRoot"
}

$arguments = @(
    '/nologo',
    '/target:winexe',
    '/platform:anycpu',
    '/optimize+',
    '/debug:pdbonly',
    "/out:$output",
    "/win32manifest:$manifest",
    "/resource:$defaultConfiguration,MouseMasterConfigurator.DefaultProperties",
    '/r:System.dll',
    '/r:System.Core.dll',
    '/r:System.Drawing.dll',
    '/r:System.Windows.Forms.dll'
) + $sources

& $compiler $arguments
if ($LASTEXITCODE -ne 0) {
    throw "Build failed with exit code $LASTEXITCODE"
}

Write-Host "Built $output"
