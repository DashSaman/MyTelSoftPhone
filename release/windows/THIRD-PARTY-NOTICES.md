# Third-party components

MytellPhone is derived from Belledonne Communications' Linphone applications under GPLv3 or later. The application license and the SDK's retained AGPLv3 license are included. Upstream copyrights, component notices and licenses remain applicable. The release supplies matching application source and an SDK source record; see its manifest for exact component versions, source locations and any source-recovery limitations.

Qt 6.10.0 runtime libraries are distributed as replaceable dynamic libraries. Their applicable GPL/LGPLv3 and third-party licenses remain in force. See `LICENSE-LGPL-3.0.txt`, `LICENSE-GPL-3.0.txt` and `qt-sbom/*.spdx.json` for the installed vendor's component-level licensing records. Qt has not been modified for this release.

Official matching Qt source archives:
- https://download.qt.io/archive/qt/6.10/6.10.0/submodules/qtbase-everywhere-src-6.10.0.tar.xz
- https://download.qt.io/archive/qt/6.10/6.10.0/submodules/qtdeclarative-everywhere-src-6.10.0.tar.xz
- https://download.qt.io/archive/qt/6.10/6.10.0/submodules/qtsvg-everywhere-src-6.10.0.tar.xz
- https://download.qt.io/archive/qt/6.10/6.10.0/submodules/qtimageformats-everywhere-src-6.10.0.tar.xz
- https://download.qt.io/archive/qt/6.10/6.10.0/submodules/qtnetworkauth-everywhere-src-6.10.0.tar.xz

The optional OpenH264 codec binary is downloaded separately from Cisco and is not included in this distribution. Its binary licensing notice and controls apply when enabled: https://www.openh264.org/BINARY_LICENSE.txt . VP8 support does not require that optional download.

The Microsoft Visual C++ runtime redistributable is an unmodified Microsoft installer and retains its own terms. Install it only if the runtime is absent on the destination computer.
