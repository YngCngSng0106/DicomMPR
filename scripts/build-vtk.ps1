# Build VTK with Java wrapping (needed by M0 / M3) and produce vtk.jar.
#
# Note: the Visual Studio generator does NOT produce vtk.jar for VTK's Java
#       wrapping, so this script builds with CMake and then packages vtk.jar
#       manually using the JDK's javac + jar.
#
# All paths are derived from this script's location; JAVA_HOME and the default
# CMake install location are auto-detected. Override with -JdkHome / -CMake /
# -WorkDir only on an unusual machine.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\build-vtk.ps1

param(
    [string]$JdkHome   = "",
    [string]$WorkDir   = "",
    [string]$VtkTag    = "v9.6.2",
    [string]$Generator = "Visual Studio 17 2022",
    [string]$CMake     = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

if (-not $WorkDir) { $WorkDir = $VtkWorkDir }
if (-not $JdkHome) { $JdkHome = Resolve-JdkHome }
if (-not $CMake)   { $CMake   = Resolve-CMake }

$vtkSrc   = Join-Path $WorkDir "VTK"
$vtkBuild = Join-Path $WorkDir "vtk-build"

if (-not (Test-Path -LiteralPath (Join-Path $JdkHome "bin\javac.exe"))) {
    throw "Invalid JAVA_HOME (javac not found): $JdkHome"
}

if (-not (Test-Path -LiteralPath $vtkSrc)) {
    Write-Host "== clone VTK $VtkTag into $vtkSrc =="
    git clone --depth 1 --branch $VtkTag https://github.com/Kitware/VTK.git $vtkSrc
    if ($LASTEXITCODE -ne 0) { throw "git clone failed" }
} else {
    Write-Host "== reuse existing source: $vtkSrc =="
}

$env:JAVA_HOME = $JdkHome

Write-Host "== configure CMake =="
& $CMake -S $vtkSrc -B $vtkBuild -G $Generator -A x64 `
    -DVTK_WRAP_JAVA=ON `
    -DVTK_BUILD_TESTING=OFF `
    -DVTK_BUILD_EXAMPLES=OFF `
    -DCMAKE_BUILD_TYPE=Release `
    "-DCMAKE_POLICY_VERSION_MINIMUM=3.5" `
    -DVTK_MODULE_ENABLE_VTK_RenderingOpenGL2=YES `
    -DVTK_MODULE_ENABLE_VTK_RenderingUI=YES `
    -DVTK_MODULE_ENABLE_VTK_InteractionWidgets=YES `
    -DVTK_MODULE_ENABLE_VTK_ImagingCore=YES `
    -DVTK_MODULE_ENABLE_VTK_ImagingSources=YES `
    -DVTK_MODULE_ENABLE_VTK_IOImage=YES `
    -DVTK_MODULE_ENABLE_VTK_IOLegacy=YES
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed" }

Write-Host "== build (takes a while) =="
& $CMake --build $vtkBuild --config Release --parallel
if ($LASTEXITCODE -ne 0) { throw "build failed" }

Write-Host "== package vtk.jar (manual javac + jar) =="
$javaSrcDir = Join-Path $vtkBuild "Wrapping\Java"
$classesDir = Join-Path $vtkBuild "java-classes"
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

$argFile = Join-Path $env:TEMP "vtkjava-args.txt"
Get-ChildItem -LiteralPath $javaSrcDir -Recurse -Filter *.java |
    ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' } |
    Set-Content -LiteralPath $argFile -Encoding ASCII

& (Join-Path $JdkHome "bin\javac.exe") -J-Xmx2g --release 8 -encoding UTF-8 -nowarn -d $classesDir "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$jarDir  = Join-Path $vtkBuild "lib\java\Release"
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null
$jarPath = Join-Path $jarDir "vtk.jar"
Remove-Item -LiteralPath $jarPath -Force -ErrorAction SilentlyContinue
& (Join-Path $JdkHome "bin\jar.exe") cf $jarPath -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed" }

Write-Host ""
Write-Host "== done. Artifacts =="
Write-Host ("vtk.jar      : " + $jarPath)
Write-Host ("JNI dir      : " + (Join-Path $vtkBuild "lib\java\vtk-Windows-AMD64\Release"))
Write-Host ("Core DLL dir : " + (Join-Path $vtkBuild "bin\Release"))
Write-Host ""
Write-Host "Next: run scripts\run-m0.ps1 to verify the environment."
