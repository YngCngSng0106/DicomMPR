# Run any offscreen check main in -Pvtk mode (compiles first, then runs).
#
# Paths are derived from this script's location; JAVA_HOME is auto-detected.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\run-check.ps1 -MainClass com.zlyd.mpr.m2.M0ResliceSmoke -Args J:\data\3120221229008001

param(
    [Parameter(Mandatory = $true)][string]$MainClass,
    [string[]]$Args = @(),
    [string]$JdkHome    = "",
    [string]$ProjectDir = "",
    [string]$VtkBuild   = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

if (-not $ProjectDir) { $ProjectDir = $ProjectRoot }
if (-not $VtkBuild)   { $VtkBuild   = $VtkBuildDir }
if (-not $JdkHome)    { $JdkHome    = Resolve-JdkHome }

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

    Write-Host "== run $MainClass =="
    & (Join-Path $JdkHome "bin\java.exe") "-Djava.library.path=$core;$jni" -cp $cp $MainClass @Args
    if ($LASTEXITCODE -ne 0) { throw "check failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}
