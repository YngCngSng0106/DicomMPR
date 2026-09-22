@echo off
rem ===========================================================================
rem  DicomMPR dev launcher: compile from source with Maven and run.
rem  Requires JDK 17+ and Maven on PATH, plus the local VTK build tree
rem  (third_party\vtk-build, see scripts\build-vtk.ps1).
rem
rem  Usage: start.bat                     (pick a DICOM folder in the UI)
rem         start.bat "D:\path\to\dicom"   (open it on startup)
rem
rem  For an end-user package without JDK/Maven, run scripts\package.ps1.
rem ===========================================================================
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-app.ps1" %*
exit /b %ERRORLEVEL%
