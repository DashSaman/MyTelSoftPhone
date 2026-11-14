@echo off
setlocal
call "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 1
set "CMAKEEXE=C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
set "MYTEL_PARENT=C:\Users\HajSaman\Documents\GLM\SoftPhone"
"%CMAKEEXE%" -S "%~dp0." -B "%~dp0..\..\build-msqogl" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_PREFIX_PATH=C:/Qt/6.10.0/msvc2022_64 -DMYTEL_SDK_ROOT="%MYTEL_PARENT%/win/sdk/linphone-sdk/win64" -DMYTEL_MS2_SOURCE="%MYTEL_PARENT%/linphone-sdk/mediastreamer2"
if errorlevel 1 exit /b 1
"%CMAKEEXE%" --build "%~dp0..\..\build-msqogl" --parallel 2
exit /b %errorlevel%
