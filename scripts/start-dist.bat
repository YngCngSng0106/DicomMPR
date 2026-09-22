@echo off
rem ===========================================================================
rem  DicomMPR launcher for the self-contained package.
rem
rem  Double-click this file to start (then pick a DICOM folder in the UI), or
rem  drag a DICOM folder onto it, or run:  start.bat "D:\path\to\dicom\folder"
rem
rem  Everything (Java runtime, VTK native libraries, dependencies) ships inside
rem  this folder, so no JDK or Maven is required. Keep the folder structure.
rem ===========================================================================
setlocal enableextensions
cd /d "%~dp0"

if not exist "runtime\bin\java.exe" (
    echo [ERROR] runtime\bin\java.exe not found - keep the package folder intact.
    pause
    exit /b 1
)
if not exist "lib" (
    echo [ERROR] lib folder not found - keep the package folder intact.
    pause
    exit /b 1
)
if not exist "vtk" (
    echo [ERROR] vtk folder not found - keep the package folder intact.
    pause
    exit /b 1
)

rem vtk holds the VTK core/JNI DLLs (plus the MSVC runtime), runtime\bin holds
rem java.exe and jawt.dll; both must be discoverable by the loader.
set "PATH=%~dp0vtk;%~dp0runtime\bin;%PATH%"

echo Starting DicomMPR ...
"%~dp0runtime\bin\java.exe" -Xmx2g -Dfile.encoding=UTF-8 ^
    "-Djava.library.path=%~dp0vtk" ^
    -cp "%~dp0lib\*" ^
    com.zlyd.mpr.App %*

set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" (
    echo.
    echo [ERROR] DicomMPR exited with code %EXITCODE%.
    pause
)
exit /b %EXITCODE%
