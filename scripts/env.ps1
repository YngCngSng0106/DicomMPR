# Shared environment discovery: project root, JDK, CMake.
# Dot-sourced by build-vtk.ps1 / run-m0.ps1 / run-app.ps1.
# Every path is derived from this file's location (no hard-coded drive letters).
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI.

$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
$script:VtkWorkDir  = Join-Path $ProjectRoot "third_party"
$script:VtkBuildDir = Join-Path $VtkWorkDir "vtk-build"

# Returns the JDK major version, or 0 when the directory is not a usable JDK.
function Get-JdkMajorVersion {
    param([string]$JdkHome)

    $javac = Join-Path $JdkHome "bin\javac.exe"
    if (-not (Test-Path -LiteralPath $javac)) {
        return 0
    }

    # javac -version writes to stderr; keep that from aborting a Stop-preferring caller.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = (& $javac -version 2>&1 | Out-String).Trim()
    } finally {
        $ErrorActionPreference = $previous
    }

    if ($output -match "javac\s+(\d+)") {
        return [int]$Matches[1]
    }
    return 0
}

# Searches, in order: -Preferred, JAVA_HOME, common install roots, then PATH.
function Resolve-JdkHome {
    param(
        [string]$Preferred = "",
        [int]$MinimumMajor = 17
    )

    $patterns = @()
    if (-not [string]::IsNullOrWhiteSpace($Preferred)) {
        $patterns += $Preferred
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $patterns += $env:JAVA_HOME
    }
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if ([string]::IsNullOrWhiteSpace($root)) {
            continue
        }
        foreach ($vendor in @("Eclipse Adoptium\jdk*", "Java\jdk*", "Microsoft\jdk*", "Amazon Corretto\jdk*")) {
            $patterns += (Join-Path $root $vendor)
        }
    }

    foreach ($pattern in $patterns) {
        $dirs = if ($pattern -match "\*") {
            Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending
        } else {
            Get-Item -LiteralPath $pattern -ErrorAction SilentlyContinue
        }
        foreach ($dir in $dirs) {
            if ((Get-JdkMajorVersion $dir.FullName) -ge $MinimumMajor) {
                return $dir.FullName
            }
        }
    }

    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) {
        $home = Split-Path -Parent (Split-Path -Parent $javac.Source)
        if ((Get-JdkMajorVersion $home) -ge $MinimumMajor) {
            return $home
        }
    }

    throw "No JDK $MinimumMajor+ found. Set JAVA_HOME or pass -JdkHome."
}

# Searches, in order: -Preferred, common install roots, then PATH.
function Resolve-CMake {
    param([string]$Preferred = "")

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($Preferred)) {
        $candidates += $Preferred
    }
    foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ([string]::IsNullOrWhiteSpace($root)) {
            continue
        }
        $candidates += (Join-Path $root "CMake\bin\cmake.exe")
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    $cmd = Get-Command cmake.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    throw "CMake not found. Install CMake or pass -CMake with the full path to cmake.exe."
}
