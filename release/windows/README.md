# Windows release tooling

The Android repository hosts release records for both applications. Windows application source is supplied as a separate exact-source ZIP in each v3 release; it is not pushed to the upstream Linphone repository.

Use Visual Studio 2022 x64 C++ tools, CMake/Ninja, and Qt 6.10.0 MSVC2022 x64. Put Windows application source at `BASE/linphone-desktop`, and the matching dependency SDK at `BASE/win/sdk/linphone-sdk/win64`. The SDK source asset and release manifest record the engine revision and local SOCI configuration patch. SDK dependencies and the rebuilt liblinphone/C++ wrapper must match each other.

After building/installing the required Qt renderer as described below:

```
configure-build.cmd C:\src\SoftPhone C:\Qt\6.10.0\msvc2022_64
powershell -File package-windows.ps1 -BaseDirectory C:\src\SoftPhone -StageDirectory C:\output\MyTel-v3.0.1 -CrtDirectory "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Redist\MSVC\14.44.35112\x64\Microsoft.VC143.CRT" -SourceCommit <FULL_WINDOWS_SOURCE_SHA>
```

`VCVARS64` can select another Visual Studio 2022 edition. Packaging requires the Qt video module and Qt OpenGL runtime, refuses an existing staging directory, preserves local profiles, includes the trust-root bundle at the path used by the application, and copies application/SDK licenses. It never copies an account profile, chat database, signing key, build log or credentials.

The 3.0.1 build flags select the MyTel friendly product/vendor and retain the internal `mytellphone` executable/profile and `MytellPhone` Windows notification identity. `SourceCommit` identifies the full Windows archive revision in the source manifest. The accepted executable was built at `aeb62a0afcbe0750e0a4ba1243043fd004236ff3`; record the executable build revision separately with `ApplicationSourceSha`. Use the standard x64 Microsoft CRT distribution, not OneCore or debug DLLs. The Microsoft notice references the vendor terms; it is not a replacement EULA or a licensing-entitlement audit. See [Microsoft redistribution documentation](https://learn.microsoft.com/en-us/cpp/windows/redistributing-visual-cpp-files).

The matching Windows source ZIP includes `cmake/install/windows/Build-MyTelInstaller.ps1`, `Assert-MyTelBranding.ps1` and the MyTel NSIS template/artwork. With an official NSIS compiler:

```powershell
powershell -File C:\src\SoftPhone\linphone-desktop\cmake\install\windows\Build-MyTelInstaller.ps1 -PayloadDirectory C:\output\MyTel-v3.0.1 -OutputDirectory C:\output\MyTel-setup -MakeNsis C:\tools\nsis\makensis.exe -ApplicationSourceSha aeb62a0afcbe0750e0a4ba1243043fd004236ff3 -Version 3.0.1
```

The per-user setup uses `%LOCALAPPDATA%\Programs\MyTel`, branded shortcuts and an Apps/Uninstall entry. It rejects unsafe roots and nonempty directories without its ownership marker, including silent `/D` targets. The uninstaller checks that marker and deletes only its known files, retaining unknown files and the separate account profile. The app and setup are not Authenticode-signed; unknown-publisher or SmartScreen warnings may occur. A separate actual installation/uninstallation acceptance check is still required.

Audio and VP8 video are available through the SDK. H.264 on Windows uses the separately downloaded Cisco codec through the application's existing codec controls. It is not bundled in this ZIP. See [Cisco's binary license](https://www.openh264.org/BINARY_LICENSE.txt) and [Linphone codec documentation](https://wiki.linphone.org/xwiki/wiki/public/view/Lib/Features/Codecs/).

These scripts document the release build setup; a clean Windows installation/build must be verified separately from the developer workstation build.

## Required Qt video renderer

The inherited SDK archive omits the Qt framebuffer renderer required by this desktop application. Before packaging, build the unmodified `msqogl.cpp` from the companion SDK source snapshot with [the module project](msqogl-plugin/README.md), then install `libmsqogl.dll` under the SDK's `lib/mediastreamer/plugins` directory. The application package must retain this plugin at the same relative path. `smoke.py` validates actual SDK discovery and fifty filter lifecycle cycles against the packaged directory; it does not replace a real video-call check.

The companion SDK manifest lists 26 unavailable exact external source pins. The SDK bundle version (5.5.39) and component project version (5.5.0) are separate namespaces, not evidence of a source/binary mismatch. Prebuilt dependency correspondence has not been independently established. These scripts and the available source snapshot do not establish a full offline SDK rebuild. The module build itself uses the supplied upstream renderer source and does not replace the media engine or codecs.
