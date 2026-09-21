# Run the M0 smoke test: install vtk.jar into the local Maven repo, compile,
# set up native library search paths, then launch.
#
# Paths are derived from this script's location; JAVA_HOME is auto-detected.
# Override with -JdkHome / -ProjectDir / -VtkBuild only on an unusual machine.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\run-m0.ps1 -CheckOnly   # verify only, no window
#   powershell -ExecutionPolicy Bypass -File scripts\run-m0.ps1             # show the sphere window

param(
    [string]$JdkHome    = "",
    [string]$ProjectDir = "",
    [string]$VtkBuild   = "",
    [string]$VtkVersion = "9.6.2",
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

if (-not $ProjectDir) { $ProjectDir = $ProjectRoot }
if (-not $VtkBuild)   { $VtkBuild   = $VtkBuildDir }
if (-not $JdkHome)    { $JdkHome    = Resolve-JdkHome }

$env:JAVA_HOME = $JdkHome
$core = Join-Path $VtkBuild "bin\Release"
$jni  = Join-Path $VtkBuild "lib\java\vtk-Windows-AMD64\Release"
$jar  = Join-Path $VtkBuild "lib\java\Release\vtk.jar"

foreach ($p in @($jar, $core, $jni)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Missing path: $p (run build-vtk.ps1 first)" }
}

Push-Location $ProjectDir
try {
    Write-Host "== install vtk.jar into local Maven repo =="
    mvn -B -q install:install-file "-Dfile=$jar" "-DgroupId=org.vtk" "-DartifactId=vtk" "-Dversion=$VtkVersion" "-Dpackaging=jar"
    if ($LASTEXITCODE -ne 0) { throw "install-file failed" }

    Write-Host "== compile (-Pvtk) =="
    mvn -B -q -Pvtk -DskipTests compile
    if ($LASTEXITCODE -ne 0) { throw "compile failed" }

    Write-Host "== build runtime classpath =="
    mvn -B -q -Pvtk dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
    if ($LASTEXITCODE -ne 0) { throw "build-classpath failed" }

    $cp = "target\classes;" + (Get-Content target\cp.txt -Raw).Trim()

    # jawt.dll (JDK) + core DLLs + JNI libraries must all be discoverable
    $env:PATH = "$core;$jni;$JdkHome\bin;$env:PATH"

    $javaArgs = @("-Djava.library.path=$core;$jni", "-cp", $cp, "com.zlyd.mpr.m0.VtkSmokeTest")
    if ($CheckOnly) { $javaArgs += "--check-only" }

    Write-Host "== run M0 smoke test =="
    & (Join-Path $JdkHome "bin\java.exe") @javaArgs
} finally {
    Pop-Location
}
