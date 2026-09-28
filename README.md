<p align="center"><img src="docs/banner.png" alt="MyTelSoftPhone"></p>

# MyTelSoftPhone

Professional SIP softphone for **MyTel** ([mytel.one](https://mytel.one)) — Android edition.

MyTelSoftPhone is a full-featured, production-grade VoIP client: audio & video calls,
multi-call management, transfer, conference, voicemail, call history, contacts,
encrypted SIP over TLS, and persistent background registration for reliable
incoming calls.

Built on the [Linphone](https://linphone.org) engine and rebranded for the MyTel.one
visual identity (navy `#001622`, blue `#0A67FF`, orange `#FF8A00`).

---

<div dir="rtl" align="right">

## فارسی

**مای‌تل سافت‌فون** — نرم‌افزار تلفن سازمانی مای‌تل برای اندروید.

- تماس صوتی و تصویری با کیفیت بالا
- پشتیبانی از پروتکل SIP روی اتصال امن TLS
- مدیریت چند تماس، انتقال تماس و کنفرانس
- دریافت تماس حتی زمانی که برنامه بسته است (سرویس پیش‌زمینهٔ اندروید)
- نامبرگ و هویت بصری مطابق سایت مای‌تل

برای نصب، فایل نصبی اندروید را از بخش «Releases» دانلود و روی گوشی نصب کنید.
سپس با شمارهٔ داخلی و گذرواژهٔ خود وارد شوید.

</div>

---

## Download

Grab the latest APK from the [Releases](../../releases) page and sideload it on any
Android 9+ device (arm64 or armeabi-v7a).

## Features

- SIP account login (username / password) — works with any standard SIP PBX/SBC,
  including `sbc.mytel.one`
- Audio codecs: Opus, G.722, G.711 (µ/a), SILK, AMR-WB…
- Video codecs: H.264, H.265, VP8, AV1 (device permitting)
- SRTP media encryption, SIP over TLS, certificate validation
- Multi-call, blind & attended transfer, conference, call waiting
- Contacts integration, favorites, call history, voicemail (MWI)
- Android Telecom self-managed calling, call-style notifications
- Light/dark theme following MyTel brand colors

## Push notifications (optional)

Incoming-call push wakeup requires Firebase Cloud Messaging. To enable it, place
your own `google-services.json` in `app/` and rebuild. Without it, the app keeps a
persistent foreground registration (classic always-on SIP mode).

## Building from source

Requirements: JDK 17+, Android SDK (platform 37). The Linphone SDK is fetched
automatically from the official Belledonne maven repository.

```bash
# one-time: create your signing config
cp keystore.properties.example keystore.properties   # then fill values + keystore

# build
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/MyTelSoftPhone-release-*.apk
```

The release keystore is **not** committed. Keep `app/mytelsoftphone.keystore` and
`keystore.properties` safe — updates must be signed with the same key.

## Licensing & credits

This project is a rebrand of [linphone-android](https://gitlab.linphone.org/BC/public/linphone-android)
by [Belledonne Communications](https://www.belledonne-communications.com),
licensed under the **GNU GPL v3** (see [LICENSE.txt](LICENSE.txt) and
[NOTICE.md](NOTICE.md)). The MyTel name and logo belong to mytel.one and are used
here for the MyTel product family.
