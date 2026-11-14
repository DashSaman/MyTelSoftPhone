# Optional Qt video renderer

The inherited Windows SDK omitted its optional `ENABLE_QT_GL` module. MytellPhone's
`CameraGui` expects the `MSQOGL` filter to return a Qt framebuffer renderer pointer;
the generic Windows display filter does not implement that contract. A real video
call previously terminated at the SDK's missing-filter fatal error.

This CMake project builds the **unmodified upstream**
`mediastreamer2/src/videofilters/msqogl.cpp` and its adjacent header against the
installed SDK import libraries and Qt 6.10.0, with MSVC x64. It reproduces the
upstream module target without rebuilding/replacing the media engine or codecs.
The corresponding source is included in the companion SDK source archive at
SDK commit `4d00285c92511acbeba7b2c7c9c621599192584f`. Preserve the upstream AGPLv3
notice and license in that source tree.

Use `build.cmd` on the inspected build machine, or configure CMake with
`MYTEL_SDK_ROOT`, `MYTEL_MS2_SOURCE` and `CMAKE_PREFIX_PATH` adjusted for your host.
Install the resulting `libmsqogl.dll` to `lib/mediastreamer/plugins` inside the
packaged application. The package must contain matching Qt Core/Gui/Quick/OpenGL
DLLs. No network download or TLS-verification bypass is used in this build.

Run the deterministic native SDK discovery/ABI acceptance check against the
packaged directory:

```powershell
python smoke.py --runtime C:\path\to\MytellPhone
```

It requires Python 3 and the packaged runtime, does not use SIP accounts or audio
devices, and isolates the exact deployed Qt module from unrelated audio plugins.
It checks native module discovery and fifty real filter creation/destruction
cycles. It cannot establish that a camera picture is displayed; real cross-app
video negotiation and visible-picture acceptance are separate checks.

Qt's framebuffer renderer requires the OpenGL scene-graph backend; the app already
selects `QQuickWindow::setGraphicsApi(QSGRendererInterface::OpenGL)` in `App.cpp`.
See the [official Qt migration notes](https://doc.qt.io/qt-6/quick-changes-qt6.html).
The SDK bundle version (5.5.39) and component project version (5.5.0) are separate
version namespaces. Missing dependency source archives and prebuilt dependency
correspondence that has not been independently established remain documented in
`SDK-SOURCE-MANIFEST-v3.0.0.json`; this bounded module build does not resolve or
conceal those separate source gaps.
