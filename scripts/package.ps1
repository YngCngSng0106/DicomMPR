# Build a self-contained, runnable Windows package of the app.
#
# Output: dist\dicom-mpr-<version>-win64\  (folder, plus a .zip next to it)
#   lib\        application jar + runtime dependencies (incl. vtk.jar)
#   vtk\        VTK native DLLs (core + Java JNI), flattened
#   runtime\    trimmed Java runtime built with jlink
#   start.bat   double-click launcher (no JDK / Maven required on the target PC)
#
# Paths are derived from this script's location; JDK and the VTK build tree are
# auto-detected. Override with -JdkHome / -VtkBuild / -OutDir only if unusual.
#
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\package.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\package.ps1 -NoZip

param(
    [string]$JdkHome  = "",
    [string]$VtkBuild = "",
    [string]$OutDir   = "",
    [switch]$NoZip,
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

if (-not $VtkBuild) { $VtkBuild = $VtkBuildDir }
if (-not $JdkHome)  { $JdkHome  = Resolve-JdkHome }
if (-not $OutDir)   { $OutDir   = Join-Path $ProjectRoot "dist" }

$env:JAVA_HOME = $JdkHome

$version = ([xml](Get-Content -LiteralPath (Join-Path $ProjectRoot "pom.xml"))).project.version
$name    = "dicom-mpr-$version-win64"
$stage   = Join-Path $OutDir $name
$zipPath = Join-Path $OutDir "$name.zip"

$coreDir = Join-Path $VtkBuild "bin\Release"
$jniDir  = Join-Path $VtkBuild "lib\java\vtk-Windows-AMD64\Release"

foreach ($path in @($coreDir, $jniDir)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing VTK build path: $path" }
}
foreach ($tool in @("bin\javac.exe", "bin\jlink.exe", "jmods")) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome $tool))) {
        throw "JDK is incomplete (missing $tool): $JdkHome"
    }
}

Push-Location $ProjectRoot
try {
    Write-Host "== build app (mvn -Pvtk package) =="
    mvn -B -q -Pvtk -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "maven package failed" }

    Write-Host "== collect runtime dependencies =="
    mvn -B -q -Pvtk dependency:build-classpath `
        "-Dmdep.includeScope=runtime" "-Dmdep.outputFile=target\cp-runtime.txt"
    if ($LASTEXITCODE -ne 0) { throw "build-classpath failed" }

    Write-Host "== stage $stage =="
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    foreach ($dir in @("lib", "vtk")) {
        New-Item -ItemType Directory -Path (Join-Path $stage $dir) -Force | Out-Null
    }

    $appJar = Join-Path $ProjectRoot "target\dicom-mpr-demo-$version.jar"
    if (-not (Test-Path -LiteralPath $appJar)) { throw "Missing application jar: $appJar" }
    Copy-Item -LiteralPath $appJar -Destination (Join-Path $stage "lib") -Force

    $depJars = ((Get-Content -LiteralPath "target\cp-runtime.txt" -Raw).Trim() -split ";") |
        Where-Object { $_ }
    foreach ($jar in $depJars) {
        Copy-Item -LiteralPath $jar -Destination (Join-Path $stage "lib") -Force
    }
    Write-Host ("   lib: {0} jars" -f (1 + $depJars.Count))

    Write-Host "== copy VTK native libraries =="
    $vtkStage = Join-Path $stage "vtk"
    foreach ($dll in @(Get-ChildItem -LiteralPath $coreDir -File -Filter *.dll) +
                      @(Get-ChildItem -LiteralPath $jniDir -File -Filter *.dll)) {
        Copy-Item -LiteralPath $dll.FullName -Destination $vtkStage -Force
    }
    # MSVC runtime: ship app-local copies so the package also runs on PCs without
    # the Visual C++ 2015-2022 redistributable (VTK DLLs link against it).
    $system32 = Join-Path $env:WINDIR "System32"
    $runtimeDlls = @()
    foreach ($dll in @("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll")) {
        $source = Join-Path $system32 $dll
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination $vtkStage -Force
            $runtimeDlls += $dll
        }
    }
    Write-Host ("   vtk: {0} dlls (+ {1} MSVC runtime)" -f
        (Get-ChildItem -LiteralPath $vtkStage -File -Filter *.dll).Count, $runtimeDlls.Count)

    Write-Host "== build trimmed runtime (jlink) =="
    $runtime = Join-Path $stage "runtime"
    $modules = "java.base,java.desktop,java.logging,java.xml,java.naming,java.sql," +
               "java.management,java.prefs,java.datatransfer,jdk.charsets,jdk.localedata," +
               "jdk.unsupported,jdk.zipfs"
    & (Join-Path $JdkHome "bin\jlink.exe") --add-modules $modules --output $runtime `
        --strip-debug --no-header-files --no-man-pages --compress=2
    if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

    # jawt.dll lives in the JDK bin and is not part of the jlink image; VTK's JNI
    # layer needs it next to java.exe.
    $jawt = Join-Path $JdkHome "bin\jawt.dll"
    if (Test-Path -LiteralPath $jawt) {
        Copy-Item -LiteralPath $jawt -Destination (Join-Path $runtime "bin") -Force
    }

    Write-Host "== write start.bat =="
    $launcher = Join-Path $ProjectRoot "scripts\start-dist.bat"
    Copy-Item -LiteralPath $launcher -Destination (Join-Path $stage "start.bat") -Force

    $sizeMb = ((Get-ChildItem -LiteralPath $stage -Recurse -File |
        Measure-Object Length -Sum).Sum / 1MB)
    Write-Host ("== package ready: {0} ({1:N1} MB) ==" -f $stage, $sizeMb)

    if (-not $SkipVerify) {
        Write-Host "== verify package (offscreen check with packaged runtime) =="
        $dataDir = Join-Path $ProjectRoot "3120221229008001"
        if (-not (Test-Path -LiteralPath $dataDir)) {
            Write-Host "   skipped: no test data at $dataDir"
        } else {
            $env:PATH = "$vtkStage;$(Join-Path $runtime "bin");$env:PATH"
            $env:JAVA_HOME = $runtime
            $log = Join-Path $env:TEMP "dicom-mpr-package-check.log"
            & (Join-Path $runtime "bin\java.exe") "-Dfile.encoding=UTF-8" `
                "-Djava.library.path=$vtkStage" -cp "$(Join-Path $stage 'lib')\*" `
                com.zlyd.mpr.m2.M0ResliceSmoke $dataDir *> $log
            $exit = $LASTEXITCODE
            $line = (Select-String -LiteralPath $log -Pattern "S0" |
                Select-Object -Last 1).Line
            if ($exit -ne 0 -or $line -notmatch "true") {
                Get-Content -LiteralPath $log -Tail 30 | ForEach-Object { Write-Host "   $_" }
                throw "packaged runtime check failed (exit $exit)"
            }
            Write-Host "   packaged check: $line"
        }
    }

    if (-not $NoZip) {
        Write-Host "== zip =="
        if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
        Compress-Archive -Path $stage -DestinationPath $zipPath -CompressionLevel Optimal
        Write-Host ("   {0} ({1:N1} MB)" -f $zipPath, ((Get-Item -LiteralPath $zipPath).Length / 1MB))
    }
} finally {
    Pop-Location
}
