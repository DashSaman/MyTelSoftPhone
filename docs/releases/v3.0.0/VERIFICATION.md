# MytellPhone 3.0.0 verification

Verification date: 2026-11-14. This record distinguishes checks actually executed from capabilities that depend on devices, servers or services. It is not a guarantee that an inherited application has no defects.

## Environment and scope

- Windows: existing Windows x64 workstation, Qt 6.10.0 / MSVC 2022, retained Linphone SDK distribution and existing user profile backed up before laboratory changes.
- Android: Android 15 / API 35 x86_64 emulator with ARM translation; package `one.mytel.softphone`, version `3.0.0` / `30000`, production signing identity retained.
- Interoperability: authorized local Issabel / Asterisk 20.12 PBX, isolated test accounts and a temporary file-audio test peer. Production extensions and their passwords were preserved.
- Sources and binary checksums are identified in the release manifests. Private profiles, signing material, handoff credentials and account-bearing SDK logs are excluded from public assets.

## Automated and build checks

| Area | Evidence |
|---|---|
| Android regression suite | 75 unit tests: managed configuration 17, connection status 4, directory parser 16, destination normalization 19, login coordination 5, registration alerts 6, account-error notification ownership 8. See [Android evidence](android-verification.md). |
| Android release signing | Existing production certificate continuity checked with `apksigner`; release packaging fails without the private production signing configuration. |
| Android lint/build | The inherited build ignored lint failures. v3 enables `abortOnError=true`, fixes seven real SDK thread violations and removes invalid plural overrides in unshipped locales. Fresh lint: zero errors, 123 warnings. Local signed release and [clean GitHub unit/lint/debug build](https://github.com/DashSaman/MyTelSoftPhone/actions/runs/43621419867) passed at `f4e46dee0d0519909dc74bcb76430fca8fcc6015`. The supported UI resource language is English; Unicode message content remains supported. |
| Windows regressions | 225 passing Node cases cover destination validation, account lifecycle, conversation visibility/storage, contacts, theme surfaces, history selection and call controls. Login ordering/race harness executed 502 scenarios; actual Qt contact harnesses executed seven deduplication and ten bounded-removal cases. Eight native Qt input cases preserve raw dial strings, cursor/selection and width bindings under Persian input direction. Source-linked native Qt navigation tests improved from 2/21 to 21/21 across page hide/show, feature visibility, geometry and exclusive selection. Seven source-linked incoming-window cases cover DND, account policy, lock state, queued ended calls, background-window preference and manual answer semantics. |
| Windows themes | Live Qt light/dark palette switching and popup inheritance checked, in addition to visual inspection of the packaged application. |
| Windows video renderer | A real video attempt exposed the inherited missing `MSQOGL` renderer and a process crash. The exact SDK Qt renderer source was compiled into `libmsqogl.dll`, packaged in its plugin directory, discovered by the actual SDK, and passed fifty create/destroy cycles. Visible video is a separate acceptance check. |
| Windows package | Static import closure examined across 136 PE modules: no missing required import on the build host. The correct CA bundle path parses 118 certificates. OpenH264 is optional and is not bundled. This is not a clean-machine installation test. |
| Privacy | Tracked source and selected publication inputs scanned for the known private handoff/signing credentials; unchanged public upstream cryptographic test fixtures were reviewed separately. |

## Executed user journeys

| Journey | Observed result |
|---|---|
| Android upgrade | Installed over the prior version, retained the test identity, existing call history and chat database; production signer matches. |
| Light/dark main screens and chat | Both clients inspected visually. Readable input fields, navigation, incoming/outgoing message bubbles and Persian/emoji text. Defects found in call surfaces, history cards, contact editors, popups and the Android DTMF inset were corrected. |
| Bidirectional chat | Windows and Android exchanged ASCII, Persian, multiline and emoji SIP messages through the PBX, without presenting an incoming-call screen. |
| Message persistence | An Android outgoing-only conversation survived force-stop/restart; Windows retained its conversation and messages after process restart. |
| Message sharing/deletion | Android text sharing populated a draft through the normal recipient flow, then explicit Send delivered it. Deletion used confirmation and removed only a synthetic laboratory conversation. |
| Contacts and contact chat | Both clients created, edited, favourited and explicitly deleted only a synthetic laboratory contact. The Windows contact survived application restart; its Message action opened the correct SIP chat and a unique marker arrived once at the controlled peer. |
| Windows incoming call | The final Windows build opened its own ringing window with Accept/End controls. Manual rejection produced no answered media and the window closed; a second call connected only after explicit Accept, with bidirectional RTP. This supplements the native Windows toast; no claim is made that OS notification banners were absent. |
| Windows history selection | Two new incoming records were inserted while an older peer was selected; the selected peer stayed unchanged. Android reverse-call records were confirmed stored; scrolling to the newest history rows revealed them. |
| Windows navigation | Add-account Back returned to the existing account. The final native app retained separated navigation buttons, readable account/main popups and a usable menu after returning; the account gear correctly identified extension 302. The ongoing-call pill now uses translatable English; its final string-only change passed five focused expression cases. |
| Offline/recovery | Android network loss displayed Offline; restoring networking recovered Online. |
| Bounded/cancelled login | Both platforms exposed Cancel during a pending connection to a reserved unreachable address. Cancelling preserved the existing registered account and discarded the temporary identity. |
| Late Android error notification | A new dummy account reproduced a delayed failure notice 30–40 seconds after cancellation. The final APK was tested through both primary and third-party cancellation flows, then observed for more than 45 seconds: original account Online, no stale banner and zero Account Error notifications. Startup orphan reconciliation has unit/source evidence; the framework itself cleared the seeded legacy notification during package replacement. |
| Audio and PBX routing | Internal laboratory calls and the PBX `*43` Echo service connected. Server RTP counters showed traffic in both directions with zero loss in the observed snapshots. This does not establish subjective handset sound quality. |
| Hold, mute and DTMF | Both platforms entered Paused and resumed. Mute state changed visibly. Sending `#` from the in-call keypad made the PBX leave Echo, play its completion prompt and hang up. |
| Real video pictures | With the two laboratory endpoints limited to common VP8, Android displayed the Windows camera and Windows displayed the emulator's synthetic camera. Windows observed 640×480 / 14.84 fps sent and 480×640 / 11.54 fps received, with zero reported video loss in that snapshot. The repaired Qt renderer remained stable. Private camera frames are excluded from public assets. |
| Blind internal transfer | A manually accepted 3099→302 call was transferred from the Windows controls to 301 after a destination confirmation. Android answered caller 3099, its timer advanced, the Windows call window closed, and the PBX showed a live 3099↔301 bridge in `from-internal-xfer`. |
| Reverse video call | Android 301 initiated to Windows 302. The Windows ringing window was explicitly accepted; enabling local video produced the emulator picture on Windows and the real Windows camera on Android. PBX streams were SendRecv VP8 with zero audio loss in the observed snapshot. Android explicitly ended the call. |
| Recording received audio | With each client microphone muted, the controlled peer's received tone was recorded through the actual app controls. Android: 25.081-second Opus file, decoded 440 Hz tone. Windows: 32.241-second Opus file, decoded stable 441.748 Hz tone, RMS −19.412 dBFS and zero clipped samples. The Windows decode emitted a minor final timestamp warning; decoding completed. Own test recordings are kept privately. |

## Service requirements and verification limits

- External phone numbers require a functioning PBX outbound route and trunk. No real PSTN destination was selected or dialled, so a completed external carrier call is not established by these tests.
- Basic SIP chat requires PBX `MESSAGE` routing. Server acknowledgement is not a read receipt. Offline store-and-forward, group chat, encryption services and conferencing require their corresponding server infrastructure.
- Attachments and voice messages require a separately configured file-transfer service. New installations do not silently select a public upload host. Existing configured services are retained; no attachment was uploaded during these tests.
- No physical Android handset, OEM battery/reboot matrix, headset/Bluetooth matrix, long-duration background registration or clean Windows machine was tested. Emulator and workstation checks cannot prove these conditions.
- The emulator's initial receive-only video remained black until its camera sent media; the guest advertised a private NAT address without ICE. This is consistent with a media pinhole problem, but packet-level NAT causality was not proven. The successful video check used SendRecv. Mixed H.264/VP8 negotiation also produced a black return direction; the isolated common-VP8 run rendered both directions. Configure compatible codecs on peers and the PBX; [Asterisk does not provide video transcoding](https://docs.asterisk.org/Operation/Asterisk-Audio-and-Video-Capabilities/).
- The SDK may record an aborted fallback attempt followed by the successful call in history; this was also present before v3. The inspected PBX trace showed one real call, rather than proof of an application double-dial.
- The supplied SDK source snapshot explicitly documents 26 unavailable exact external gitlink sources and prebuilt dependency correspondence that was not independently established. It is not described as a fully reproducible offline SDK source distribution. See `SDK-SOURCE-MANIFEST-v3.0.0.json` and the Windows build notes.
- No coverage percentage was measured. No claim of zero bugs, universal compatibility, uninterrupted service or end-to-end encryption is made.

## Final acceptance record

Acceptance was completed on 2026-11-14. The signed Android APK was built at `f4e46dee0d0519909dc74bcb76430fca8fcc6015`; its strict CI, 75 unit cases and enforcing lint passed. Windows final source is `a1e2ae355d0824e261854ecbf7444375b8f6a48c`; the full native build and subsequent increments passed, with 225 Node cases and the native Qt checks described above. Incoming answer/rejection, transfer and reverse video were accepted at `f4d104f`; the only subsequent production change translates the ongoing-call label and passed five focused expression cases plus a seven-task rebuild.

The temporary laboratory peer and its routes were removed. The test-only 301/302 codec changes were restored with a scoped backup, leaving unrelated PBX configuration intact; no real call remained. Android retained its original 301 test identity and data, with a private final backup and the emulator closed. The verified Windows runtime was installed at the existing application location after preserving the previous runtime, and the full original user profile was restored with verified critical-file hashes. A launch from that installed location retained the original identity and history and showed Online in the original System appearance mode. Camera frames, recordings, credentials and private test databases are excluded from release assets.

The release includes exact app-source archives, the documented partial Windows SDK source snapshot, runtime/source manifests and `SHA256SUMS.txt`. The public `RELEASE-MANIFEST-v3.0.0.json` records the final repository/tag commit separately from the APK build commit; only documentation and Windows release helpers changed in the Android repository after the verified APK build. These file-integrity records do not expand the behavioral acceptance or compatibility limits above.
