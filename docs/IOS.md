# MyTelSoftPhone — iOS edition roadmap

The Android app in this repository is ready to build on any machine.
The **iOS counterpart requires macOS with Xcode** — an iOS app cannot be compiled
on Windows or Linux, so no iPhone build is produced from this repository as-is.

## What the iOS app would be

A rebrand of the open-source **linphone-iphone** project, exactly like this
repository rebrands linphone-android:

- Same MyTel identity (navy `#001622`, blue `#0A67FF`, orange `#FF8A00`,
  gradient-ring + handset icon)
- SIP/TLS login against `sbc.mytel.one`
- CallKit + PushKit (APNs VoIP push) for native incoming-call experience
- Same release versioning (MyTelSoftPhone 1.x)

## Steps to produce it (on a Mac)

1. Clone `https://gitlab.linphone.org/BC/public/linphone-iphone`
2. `pod install` (pulls the Linphone iOS SDK from the Belledonne podspecs)
3. Rebrand:
   - `Info.plist`: bundle display name → `MyTelSoftPhone`, bundle id → `one.mytel.softphone`
   - Assets: replace `Assets.xcassets/AppIcon` with the MyTel icon set
     (sources in this repo: `app/src/main/res/mipmap-*` PNGs)
   - Accent color → `#0A67FF`, dark background → `#06101C`
   - Default domain → `sbc.mytel.one`
4. Open the workspace in Xcode, select a Team/signing certificate, `Product > Archive`
5. Export or upload to TestFlight / App Store

## Server-side requirements for iOS incoming calls

APNs VoIP push certificate or token-based key (.p8), associated by your backend
with each extension, so incoming calls wake the app (PushKit) before CallKit
presents the call. Without it, iOS will suspend the app in background and
incoming calls will only be received while the app is open.
