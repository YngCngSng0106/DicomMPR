# Run any check main in -Pvtk mode (compiles first, then runs).
#
# Paths are derived from this script's location; JAVA_HOME is auto-detected.
# Checks are selected by a short name (see -List) or by full class name.
# When no data dir is given, the bundled test data folder is used if present.
#
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI.
#
# Usage:
#   scripts\run-check.ps1                              # default: smoke (M0ResliceSmoke)
#   scripts\run-check.ps1 center-release               # short name, default data dir
#   scripts\run-check.ps1 center-release "D:\dicom\case1"
#   scripts\run-check.ps1 -Check mpr -Args "D:\dicom\case1"
#   scripts\run-check.ps1 -MainClass com.zlyd.mpr.m2.M3MprCheck -Args "D:\dicom\case1"
#   scripts\run-check.ps1 -List

param(
    [Parameter(Position = 0)][string]$Check = "smoke",
    [Parameter(Position = 1)][string[]]$Args = @(),
    [string]$MainClass  = "",
    [string]$JdkHome    = "",
    [string]$ProjectDir = "",
    [string]$VtkBuild   = "",
    [switch]$List
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

$aliases = [ordered]@{
    "smoke"          = "com.zlyd.mpr.m2.M0ResliceSmoke"
    "volume"         = "com.zlyd.mpr.m2.M2VolumeCheck"
    "mpr"            = "com.zlyd.mpr.m2.M3MprCheck"
    "startup"        = "com.zlyd.mpr.m2.M3StartupCheck"
    "rig"            = "com.zlyd.mpr.m2.M3RigCheck"
    "freeze"         = "com.zlyd.mpr.m2.M3FreezeCheck"
    "crosshair"      = "com.zlyd.mpr.m2.M3CrosshairCheck"
    "measure"        = "com.zlyd.mpr.m2.M3MeasurementCheck"
    "measure-draw"   = "com.zlyd.mpr.m2.M3MeasurementDrawCheck"
    "drag"           = "com.zlyd.mpr.m2.M3DragResidualCheck"
    "drag-gui"       = "com.zlyd.mpr.m2.M3DragGuiCheck"
    "center-release" = "com.zlyd.mpr.m2.M3CenterReleaseCheck"
    "reopen"         = "com.zlyd.mpr.m2.M3ReopenCheck"
    "reslice"        = "com.zlyd.mpr.m2.M3ResliceCheck"
}

if ($List) {
    Write-Host "short name       main class"
    foreach ($entry in $aliases.GetEnumerator()) {
        Write-Host ("  {0,-15} {1}" -f $entry.Key, $entry.Value)
    }
    return
}

if (-not $ProjectDir) { $ProjectDir = $ProjectRoot }
if (-not $VtkBuild)   { $VtkBuild   = $VtkBuildDir }
if (-not $JdkHome)    { $JdkHome    = Resolve-JdkHome }

if ($MainClass) {
    $fqcn = $MainClass
} elseif ($aliases.Contains($Check.ToLowerInvariant())) {
    $fqcn = $aliases[$Check.ToLowerInvariant()]
} elseif ($Check -match "\.") {
    $fqcn = $Check
} else {
    throw "Unknown check '$Check'. Run with -List to see the short names."
}

# Default data dir: avoid accidentally scanning the whole project tree (which
# contains third_party\ and target\) when the caller forgets to pass one.
if ($Args.Count -eq 0) {
    $fallback = Join-Path $ProjectDir "3120221229008001"
    if (Test-Path -LiteralPath $fallback) {
        $Args = @($fallback)
        Write-Host "   args: (default test data) $fallback"
    }
}

$env:JAVA_HOME = $JdkHome

$core = Join-Path $VtkBuild "bin\Release"
$jni  = Join-Path $VtkBuild "lib\java\vtk-Windows-AMD64\Release"
foreach ($p in @($core, $jni)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Missing path: $p" }
}

Push-Location $ProjectDir
try {
    Write-Host "== compile (-Pvtk) =="
    mvn -B -q -Pvtk -DskipTests compile
    if ($LASTEXITCODE -ne 0) { throw "compile failed" }

    if (-not (Test-Path "target\cp-vtk.txt")) {
        Write-Host "== build runtime classpath =="
        mvn -B -q -Pvtk dependency:build-classpath "-Dmdep.outputFile=target\cp-vtk.txt"
        if ($LASTEXITCODE -ne 0) { throw "build-classpath failed" }
    }

    $cp = "target\classes;" + (Get-Content target\cp-vtk.txt -Raw).Trim()

    # jawt.dll (JDK) + core DLLs + JNI libraries must all be discoverable
    $env:PATH = "$core;$jni;$JdkHome\bin;$env:PATH"

    Write-Host "== run $fqcn =="
    & (Join-Path $JdkHome "bin\java.exe") "-Djava.library.path=$core;$jni" -cp $cp $fqcn @Args
    if ($LASTEXITCODE -ne 0) { throw "check failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}
