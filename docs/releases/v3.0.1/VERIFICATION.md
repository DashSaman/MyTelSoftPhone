# MyTel 3.0.1 verification

Verification session: 14–15 November 2026. This record separates observed results from device, server and packaging limits. It does not certify that the inherited application is defect-free.

Application and installed-package acceptance is complete for the checks recorded below. The production install/uninstall/reinstall journey passed for the `6cc4739` application with the `06bb587` installer helper. The final contrast-fix build at `aeb62a0afcbe0750e0a4ba1243043fd004236ff3` then passed native/post-commit checks, the production owned upgrade and installed visual inspection. Publication/tag/checksum verification is a separate gate and is not asserted as completed by this runtime record.

## Source and artifact identity

| Component | Identity and verification state |
|---|---|
| Android application build | `b475363624a66d7ad604e66d37d86a21fcd79dbe`; 3.0.1 / code 30001; package `one.mytel.softphone` |
| Signed Android APK | `MyTel-v3.0.1-android.apk`; 95,755,459 bytes; SHA-256 `f1647dc4aaed9d2108dd5f7061988f49cd3534de4d5c42a3d02d630d62c086b2` |
| Android production certificate | SHA-256 `60ebd14405aeb1fe58e277297182fb48cb77d07f8046982a07e494ba49b4d7f4`; existing signing identity retained |
| Windows final application/source archive revision | `aeb62a0afcbe0750e0a4ba1243043fd004236ff3`; MyTel 3.0.1, x64; native build, post-commit checks and final installed upgrade acceptance passed |
| Windows final application executable | 82,582,528 bytes; SHA-256 `c077af4d508b9136608613d8671ae2108417abc1d2367692c42fadafa31f632c` |
| Windows final production installer | `MyTel-v3.0.1-windows-x64-setup.exe`; 105,774,794 bytes; SHA-256 `d1aad611b3f269d6094613dda51661b2ddf1beea3dedf6b29fe0a1ba7ec88521` |
| Previously installed/tested Windows build | `6cc47392606429c5b58c345080b4ceb350299db6`; 82,582,528 bytes; SHA-256 `e7502ce5740ec0d785d7bc482d6a00d57b4856ad3d3a82d1ce8c2518421a0664` |
| Windows installer/helper baseline | `06bb5871973c50d1116d91d3ad7404ee32e416c6`; its delta after `6cc4739` changes the PowerShell installer helper and documentation. The later `aeb62a0` application/source revision includes the focused secondary-button contrast fix. |

The final repository/tag commit may include release documentation and packaging helpers after the Android APK build. Release manifests must keep the application build revisions distinct from that final repository revision. The accepted installer hash is recorded above; final asset-set/archive correspondence and `SHA256SUMS.txt` are checked separately during publication.

## Automated and build checks

| Check | Executed result |
|---|---|
| Android full local gate | Signed release, debug unit tests and strict lint passed; 84 tests, zero failures/errors/skips |
| Android lint | Actual XML: zero Error/Fatal findings, 126 warnings. Three additional warnings identify replaced onboarding illustrations that are now unused. No blanket suppression or lint baseline was added. |
| Android committed-source release refresh | `assembleRelease` passed; AGP's embedded version-control-info records the exact Android build revision above. `apksigner verify` exited zero with the existing production certificate. |
| Android clean CI | [Run 43636465168](https://github.com/DashSaman/MyTelSoftPhone/actions/runs/43636465168) completed successfully at `b475363624a66d7ad604e66d37d86a21fcd79dbe`. |
| Windows native build | Full configured 3.0.1 build and the window-title increment passed at `6cc4739`. The final `aeb62a0` native build and post-commit checks also passed; the accepted executable hash is recorded above. App and source archive use the same `aeb62a0` revision with no post-build source delta. |
| Windows Node regression suite | 225 passed, zero failed or skipped |
| Native Qt Transport popup checks | 12/12 passed across light/dark and selection states |
| Focused native Qt secondary-button checks | RED: 6/12 passed before the fix; GREEN: 12/12 passed after the focused normal/hover/pressed text-and-icon contrast fix |
| Native icon/render-cache checks | 6/6 passed with existing MyTel artwork |
| Actual NSIS ownership fixtures | 12/12 passed, including rejecting unowned destinations, accepting an owned upgrade, preserving unknown files and blocking unsafe target roots |
| Production installer journey on `6cc4739` / `06bb587` | Install → uninstall → reinstall passed: all 1,523 payload hashes matched; all ten existing profile files remained byte-identical, with zero new profile files; three shortcuts and Installed Apps showed MyTel 3.0.1 with the correct icons. Root and nested unknown-file sentinels survived uninstall and were then moved to private storage. Reinstall again matched all payload hashes. |
| Final `aeb62a0` installed upgrade | Actual owned `/S` upgrade exited zero. All 1,523 installed payload hashes matched. A fresh baseline taken after the physical chat tests showed all ten profile files byte-identical and zero new profile files after upgrade. Installed Apps showed MyTel 3.0.1 / publisher MyTel; all three shortcuts had the correct icons and targets. Final installed visual acceptance passed. |

Coverage percentage was not measured. Passing fixtures and build checks do not substitute for a clean-machine compatibility test.

## Observed application behavior

### Android

[Android-specific verification](android-verification.md) records the source/TDD details and exact limits. The final signed APK upgraded in place while retaining the existing test identity and history. The deliberately rejected PBX route produced the expected media error; a subsequent echo call connected and native Telecom showed one ACTIVE call. The rejection-to-echo gap was **41 seconds**, including UI captures/tool roundtrips. The old 3.0.0 baseline also completed this subsequent redial, and the reported native “another call connecting” dialog did not reproduce in that timing. This is not a sub-second race reproduction.

A real Windows-to-emulator incoming call was accepted through Android's notification Answer action. The connected call screen reached 40 seconds; a snapshot 1.3 seconds after ending through Android showed empty current-call lists and no foreground call. Existing MyTel artwork, replacement onboarding copy, visible `mytel.one` link and navigation were inspected in portrait light/dark themes. About, legal and license notices were retained.

After receiving the RC.1 preview with the same APK hash, the user explicitly confirmed installing/running it on the physical phone and being logged in as account 2. This is a user-attested installation; the package/version was not independently queried through adb. Subsequent physical calls in both directions were connected and accepted by the user as described below. The earlier messaging capture used an unversioned SIP User-Agent and preceded the installation confirmation, so that capture does not establish which APK was installed during the earlier text test.

### Windows identity and appearance

The installed `6cc4739` application displayed **MyTel** in both the main and call window titles and used its own MyTel icon. The Transport selection popup was inspected in light and dark themes: text and selected UDP state were readable. Appearance was restored to System afterward.

After the final `aeb62a0` owned upgrade, the operator launched the executable from the actual per-user Programs/MyTel installation. The main window showed MyTel and its icon, account 1 was Online, and both older and freshly tested chat history remained. Actual Appearance labels were white and readable in dark mode and blue and readable in light mode. The operator restored System appearance and returned to Chats.

Safe login captures with a blank password and example server: [dark Transport](windows-dark-transport.jpg), [light Transport](windows-light-transport.jpg).

Safe captures from the final installed executable: [dark Appearance](windows-final-appearance-dark.jpg), [light Appearance](windows-final-appearance-light.jpg).

The actual final installer GUI was also reviewed: welcome/logo, MyTel/site identity, license, components and destination pages were visually correct. That review exited before the GUI Install action; the authorized command-line owned upgrade had already completed and was hash/profile-verified as recorded above.

The actual per-user notification registration was inspected: DisplayName is MyTel, the existing `MytellPhone` application identifier remains stable, and IconUri points to a MyTel PNG whose artwork was visually checked. A native notification banner was **not** captured in this verification; registry/icon inspection is the evidence recorded here.

On the `6cc4739` Windows executable, `*43` echo connected with ulaw. A bounded PBX snapshot showed 553 received / 494 transmitted RTP packets and zero loss in that snapshot. This establishes signaling and packet flow for that echo snapshot; the later physical-call feedback is recorded separately.

### Focused secondary-button contrast correction

Actual Appearance controls exposed an additional contrast defect: secondary labels used brand blue on the dark surface, giving 3.4378:1 in normal state and about 2.46:1 on hover. Light-mode hover was about 4.14:1. The controls use 18px DemiBold text, so the [normal-text 4.5:1 criterion](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) is the relevant threshold.

The focused fix changes secondary text/icon state colors while preserving the primary fill/border palette. The native Qt check reads the actual text and icon colors in normal, hover and pressed states:

| Theme | Normal | Hover | Pressed |
|---|---:|---:|---:|
| Light | 4.776:1 | 5.603:1 | 4.776:1 |
| Dark | 13.419:1 | 10.438:1 | 4.776:1 |

The focused suite moved from 6/12 passing to 12/12 passing. These measurements cover those controls/states; they are not a claim of whole-application WCAG conformance. The `aeb62a0` native build, final production installer, owned upgrade and installed light/dark visual checks passed.

## Real PBX and physical-phone checks

The authorized account 1/2 PBX repair was scoped and durable. Their inherited WebRTC/mandatory-DTLS/AVPF profile did not match the native SIP client's plain RTP offer. Their MESSAGE context also fell back to the voice dialplan. The owner backed up and corrected the relevant account profiles and message context while preserving authentication, identities, AORs, codecs, ordinary voice routing, trunks and unrelated accounts. The broken profiles were not restored after testing.

Real plain UTF-8 text passed in both directions between Windows account 1 and physical Android account 2: the unique Windows message was forwarded and acknowledged by the phone; two Persian replies appeared on Windows and were acknowledged. The bounded trace contained **zero INVITE requests involving those accounts** and three successful message-send events. This confirms text delivery for that session without claiming the physical phone's APK version.

After the user-attested installation of the supplied `b475363` Android APK, a fresh 75-second phone-to-Windows capture confirmed one unique Persian 24-byte valid UTF-8 plain-text message forwarded to Windows and acknowledged with 200. A separate 45-second reverse capture confirmed the new Windows reply was forwarded once and acknowledged with 200 by the physical phone. Both capture windows contained zero INVITEs involving accounts 1/2, with two successful message-send events and zero voice channels afterward. The operator also visually checked the new received text and outgoing reply. SIP logging was restored off. This post-install result uses installed Windows `6cc4739`; the final `aeb62a0` application delta does not change SIP code.

Optional `message/imdn+xml` and `application/im-iscomposing+xml` requests received 415 from the PBX. Read receipts and composing notifications are therefore **unsupported on the tested PBX path**. Their failure was not reclassified as failure of the separately verified plain text. Android's Basic-room UI still exposes outgoing status icons and Delivery status tabs, and it invokes composing notifications automatically; those controls do not prove server support.

The user's first real PSTN attempt rang but had an audio problem. A later, separate test used a controlled file-audio SDK peer to send generated alternating tones through the PBX/PSTN path. The user confirmed the tones were clear and continuous. This verifies the controlled tone path; no microphone-source patch was made on the basis of an inferred cause.

After the user-attested new Android APK installation, physical Android account 2 called the installed Windows `6cc4739` account 1. The user accepted the call before the operator's observation; no operator Accept action or automatic-answer behavior is inferred. The PBX recorded ANSWERED, 47 billable seconds / 52 total seconds, ulaw on both legs and sampled packet counts of 2,350 / 2,318 with zero loss. In response to the connection/audio question, the user reported no problem.

The reverse Windows account 1 → physical Android account 2 call reached 1:14 in the observed connected UI. Hold changed the action to Resume, resuming restored Pause, muting changed the microphone action to Activate Microphone, and unmuting restored Mute. The operator ended the call and the server then had zero voice channels. The user subsequently described the reverse-call audio as excellent after the hold/resume test. CDR correlation confirms ANSWERED, 90 total seconds / 87 billable seconds. The post-resume media returned from hold music's slin to ulaw; a bounded snapshot recorded Windows-leg 4,204 RX / 4,167 TX packets and phone-leg 4,170 RX / 4,205 TX packets, with zero loss on both legs in that snapshot.

After resume, both observed Windows WASAPI sessions were unmuted; playback peak was 0.13405466 and capture peak was 0.03911214. These meters show activity, and user feedback establishes acceptance of these two observed physical calls. They are not a calibrated audio measurement or a test matrix across other microphones, speakers, headsets or networks. The fresh post-install plain-text exchange is visually and protocol-confirmed above. The calls were exercised on installed `6cc4739`; the later `aeb62a0` delta changes secondary-button style getters and their tests, with unchanged SIP application code. User audio and camera data are not public release assets.

## Distribution, licensing and remaining limits

- Windows application and installer binaries are not Authenticode signed. Android APK signing continuity was verified separately.
- The unchanged SDK source archive and its manifest are reused byte-for-byte under their existing v3.0.0 names. The manifest explicitly records 26 unavailable exact external source pins and prebuilt dependency correspondence that was not independently established. This is not a complete, proven offline-rebuildable SDK source distribution.
- Existing GPL/AGPL component licenses and upstream copyright notices remain applicable. MyTel branding does not relicense the SDK or remove its notices. The SDK distribution's bundle/component version labels must be interpreted through its recorded provenance, not assumed to be a mismatch solely because their labels differ.
- The user has attested to installing the supplied Android preview and accepted the observed physical calls in both directions; fresh post-install plain text also passed visually and in the bounded protocol traces. Independent physical package/version inspection, a broad/calibrated audio hardware matrix and a captured native notification banner are not claimed as completed here.
- The production `6cc4739` / `06bb587` install/uninstall/reinstall journey and final `aeb62a0` production owned upgrade passed with payload/profile preservation checks. These workstation results do not constitute a clean-machine or all-supported-Windows-version compatibility matrix.
- Rotated/tablet Android resources compiled and passed lint, but runtime rendering and website browser handoff were not marked as tested. An optional combined URL-click/rotation/restore batch was automatically rejected before execution with the generic reason “blocked by policy”; no more specific reason was returned. Allowed scoped actions restored the required light appearance and main screen.
- Private profiles, phone numbers, server addresses, credentials, signing material, raw account-bearing traces and private media must be excluded from public documentation and assets. Temporary test peers/routes were cleaned up; the intended account 1/2 repair remains.

The [v3.0.0 verification record](../v3.0.0/VERIFICATION.md) contains the earlier bidirectional video, transfer, contacts, network-loss/recovery, DTMF and recording journeys. Those results are historical evidence; they were not all repeated for 3.0.1.

Final publication checks verify the asset set, manifest correspondence, checksums and tag/CI state after the documentation commit. Their outcome is recorded by the publication process; no unexecuted publication result is claimed here. These integrity checks do not expand the behavioral evidence or compatibility limits above.
