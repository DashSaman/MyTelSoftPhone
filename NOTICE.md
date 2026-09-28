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

The Linphone SDK binaries are consumed unmodified from the official Belledonne
maven repository (https://download.linphone.org/maven_repository).

"MyTel", "mytel.one" and the MyTel logo are trademarks of their owner; the logo
imagery in this repository is a derivative rendering of the public site identity
for the MyTel softphone product.
