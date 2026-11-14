@echo off
setlocal
REM Layout: BASE/linphone-desktop and BASE/win/sdk/linphone-sdk/win64.
if "%~1"=="" (
  echo Usage: configure-build.cmd BASE_DIRECTORY [QT_DIRECTORY]
  exit /b 2
)
set "MYTEL_BASE=%~f1"
set "MYTEL_QT=%~2"
if "%MYTEL_QT%"=="" set "MYTEL_QT=C:\Qt\6.10.0\msvc2022_64"
if not defined VCVARS64 set "VCVARS64=C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS64%" (
  echo Set VCVARS64 to your Visual Studio 2022 x64 build environment script.
  exit /b 2
)
call "%VCVARS64%" >nul
if errorlevel 1 exit /b 1
set "PATH=%MYTEL_QT%\bin;%PATH%"
set "MYTEL_SDK=%MYTEL_BASE%\win\sdk\linphone-sdk\win64"
cmake -S "%MYTEL_BASE%\linphone-desktop" -B "%MYTEL_BASE%\win\build" -G Ninja ^
 -DCMAKE_BUILD_TYPE=RelWithDebInfo -DLINPHONE_QT_ONLY=ON ^
 -DLINPHONE_OUTPUT_DIR="%MYTEL_SDK%" -DQTKEYCHAIN_OUTPUT_DIR="%MYTEL_SDK%" ^
 -DCMAKE_PREFIX_PATH="%MYTEL_QT%;%MYTEL_SDK%" ^
 -DCMAKE_PROJECT_INCLUDE="%~dp0preload-deps.cmake" -DMYTEL_SDK_PREFIX="%MYTEL_SDK%" ^
 -DMediastreamer2_PLUGINS_DIR="%MYTEL_SDK%\lib\mediastreamer\plugins" ^
 -DENABLE_APP_PACKAGING=OFF -DENABLE_CRASH_HANDLER=OFF -DENABLE_UPDATE_CHECK=OFF ^
 -DENABLE_LDAP=OFF -DENABLE_QT_KEYCHAIN=OFF -DENABLE_APP_OAUTH2=OFF ^
 -DLINPHONEAPP_APPLICATION_NAME=MytellPhone -DLINPHONEAPP_EXECUTABLE_NAME=mytellphone ^
 -DLINPHONEAPP_VERSION=3.0.0
if errorlevel 1 exit /b 1
cmake --build "%MYTEL_BASE%\win\build" --parallel 2
exit /b %errorlevel%
