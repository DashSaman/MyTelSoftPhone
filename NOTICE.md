# NOTICE

MyTelSoftPhone is based on the upstream open-source project
**linphone-android** by Belledonne Communications:
https://gitlab.linphone.org/BC/public/linphone-android

Upstream code is licensed under GNU GPL v3 (LICENSE.txt). This repository keeps
the upstream license intact and distributes its modifications under the same
terms. All upstream copyright notices remain valid.

## Modifications applied in this rebrand

- Application id changed to `one.mytel.softphone`, display name to `MyTelSoftPhone`
- Brand palette remapped to the mytel.one identity
  (`orange_main_500` → `#0A67FF`, `orange_main_100` → `#D7E7FF`,
  dark backgrounds → MyTel navy)
- New launcher / adaptive / round / splash / welcome icons generated from the
  mytel.one logo concept (gradient ring + white handset on navy `#001622`)
- Splash screen background switched to MyTel navy with a branded icon
- User-visible "Linphone" strings renamed to "MyTelSoftPhone"
- Release signing uses a project-specific keystore (`app/mytelsoftphone.keystore`,
  not committed)
- Output APK renamed to `MyTelSoftPhone-<buildType>-<version>.apk`

The Android Linphone SDK binaries are consumed from the pinned official
Belledonne Maven dependency recorded in `gradle/libs.versions.toml`.
The separate Windows release uses the Linphone SDK dependency distribution
with a locally rebuilt liblinphone/C++ wrapper. Its exact source revision,
local build patch, corresponding SDK source archive and license are supplied
with the release. The manifest explicitly lists unavailable external source
pins; prebuilt dependency correspondence has not been independently established.
The snapshot is not represented as a complete offline-rebuildable SDK distribution.
The SDK source contains AGPLv3-licensed components; all
component notices and third-party licenses remain applicable. Application
licensing must not be read as relicensing SDK components.

MytellPhone v3 modifications include call/input routing, Basic SIP chat storage,
registration lifecycle and cancellation, theme/layout corrections, directory
request safety, backup exclusions and release validation. The Windows package
does not bundle the separately downloaded Cisco OpenH264 binary. Its use is
subject to the [Cisco binary license](https://www.openh264.org/BINARY_LICENSE.txt).

"MyTel", "mytel.one" and the MyTel logo are trademarks of their owner; the logo
imagery in this repository is a derivative rendering of the public site identity
for the MyTel softphone product.
