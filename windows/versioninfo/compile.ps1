# Compiles mousemaster.rc into the .res file that pom.xml passes to the native-image linker.
# rc.exe comes with the Windows SDK, which native-image already needs to link the executable.
param([Parameter(Mandatory)] [string] $version,
      [Parameter(Mandatory)] [string] $outputFile)
$ErrorActionPreference = 'Stop'
$rc = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\rc.exe" |
        Sort-Object FullName | Select-Object -Last 1
if (-not $rc) {
    throw "rc.exe not found in the Windows SDK: cannot compile the version information of the executable"
}
$outputDirectory = Split-Path -Parent $outputFile
@"
#define MOUSEMASTER_VERSION $version
#define MOUSEMASTER_VERSION_STRING "$version"
"@ | Set-Content "$outputDirectory\mousemaster-version.h" -Encoding ascii
& $rc.FullName /nologo /i $outputDirectory /fo $outputFile "$PSScriptRoot\mousemaster.rc"
if ($LASTEXITCODE -ne 0) {
    throw "rc.exe failed with exit code $LASTEXITCODE"
}
