# One-stop verification of the current project state.
#
# Runs, in order:
#   1. standards scan   (UTF-8 BOM, System.out/err, printStackTrace, catch(Exception|Throwable))
#   2. unit tests       (mvn test)
#   3. -Pvtk compile
#   4. offscreen checks (only those whose class exists; skipped with -SkipChecks)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\verify.ps1 -DataDir J:\data\3120221229008001
#   powershell -ExecutionPolicy Bypass -File scripts\verify.ps1 -StandardsOnly
#   powershell -ExecutionPolicy Bypass -File scripts\verify.ps1 -DataDir <dir> -SkipChecks

param(
    [string]$DataDir    = "",
    [switch]$StandardsOnly,
    [switch]$SkipChecks,
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
$failures = @()

# --- 1. standards scan ------------------------------------------------------
Write-Host "== [1/4] standards scan =="
$sources = Get-ChildItem -LiteralPath (Join-Path $ProjectDir "src") -Recurse -Filter *.java
$bomFiles = @()
foreach ($file in $sources) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $bomFiles += $file.FullName
    }
}
$hits = @($sources | Select-String -Pattern 'System\.(out|err)|printStackTrace|catch\s*\(\s*(Exception|Throwable)')
Write-Host ("files={0} BOM={1} violations={2}" -f $sources.Count, $bomFiles.Count, $hits.Count)
if ($bomFiles.Count -gt 0 -or $hits.Count -gt 0) {
    $bomFiles | ForEach-Object { Write-Host "  BOM: $_" }
    $hits     | ForEach-Object { Write-Host ("  {0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim()) }
    $failures += "standards"
}

if ($StandardsOnly) {
    if ($failures.Count -gt 0) { exit 1 }
    Write-Host "standards OK"
    exit 0
}

Push-Location $ProjectDir
try {
    # --- 2. unit tests ------------------------------------------------------
    Write-Host "== [2/4] unit tests =="
    $testLog = mvn -B test 2>&1 | Out-String
    ($testLog -split "`n") | Where-Object { $_ -match "Tests run: \d+, Failures" } |
        ForEach-Object { Write-Host $_.Trim() }
    if ($LASTEXITCODE -ne 0) { $failures += "unit tests"; Write-Host "unit tests FAILED" }

    # --- 3. -Pvtk compile ---------------------------------------------------
    Write-Host "== [3/4] compile (-Pvtk) =="
    mvn -B -q -Pvtk -DskipTests compile
    if ($LASTEXITCODE -ne 0) { $failures += "vtk compile"; Write-Host "compile FAILED" }

    # --- 4. offscreen checks ------------------------------------------------
    if (-not $SkipChecks) {
        Write-Host "== [4/4] offscreen checks =="
        if (-not (Test-Path "target\cp-vtk.txt")) {
            mvn -B -q -Pvtk dependency:build-classpath "-Dmdep.outputFile=target\cp-vtk.txt"
            if ($LASTEXITCODE -ne 0) { throw "build-classpath failed" }
        }
        $core = Join-Path $VtkBuild "bin\Release"
        $jni  = Join-Path $VtkBuild "lib\java\vtk-Windows-AMD64\Release"
        $cp   = "target\classes;" + (Get-Content target\cp-vtk.txt -Raw).Trim()
        $env:PATH = "$core;$jni;$JdkHome\bin;$env:PATH"

        # Data directory is optional: checks fall back to the working directory.
        $dataArgs = @()
        if ($DataDir) { $dataArgs = @($DataDir) }

        $checks = @(
            @{ Class = "com.zlyd.mpr.m2.M2VolumeCheck";  Args = $dataArgs },
            @{ Class = "com.zlyd.mpr.m2.M3MprCheck";     Args = $dataArgs },
            @{ Class = "com.zlyd.mpr.m2.M3StartupCheck"; Args = @() },
            @{ Class = "com.zlyd.mpr.m2.M0ResliceSmoke"; Args = $dataArgs },
            @{ Class = "com.zlyd.mpr.m2.M3RigCheck";     Args = $dataArgs },
            @{ Class = "com.zlyd.mpr.m2.M3FreezeCheck"; Args = $dataArgs },
            @{ Class = "com.zlyd.mpr.m2.M3ResliceCheck"; Args = $dataArgs }
        )
        foreach ($check in $checks) {
            $classFile = Join-Path "target\classes" (($check.Class -replace "\.", "\") + ".class")
            if (-not (Test-Path -LiteralPath $classFile)) {
                Write-Host ("-- skip {0} (not built yet)" -f $check.Class)
                continue
            }
            Write-Host ("-- {0}" -f $check.Class)
            & (Join-Path $JdkHome "bin\java.exe") "-Djava.library.path=$core;$jni" -cp $cp `
                $check.Class @($check.Args) 2>&1 |
                Where-Object { $_ -match "INFO|WARN|ERROR" } |
                ForEach-Object { Write-Host ("   " + $_.Trim()) }
            if ($LASTEXITCODE -ne 0) {
                $failures += $check.Class
                Write-Host ("   {0} FAILED (exit {1})" -f $check.Class, $LASTEXITCODE)
            }
        }
    }
} finally {
    Pop-Location
}

Write-Host ""
if ($failures.Count -gt 0) {
    Write-Host ("VERIFY FAILED: " + ($failures -join ", "))
    exit 1
}
Write-Host "VERIFY OK"
