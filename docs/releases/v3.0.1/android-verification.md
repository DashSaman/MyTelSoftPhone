# Android 3.0.1 verification

Scope: Android source commit `b475363624a66d7ad604e66d37d86a21fcd79dbe`, package `one.mytel.softphone`, version **3.0.1 / 30001**. Runtime checks used the existing Android 15/API 35 x86_64 emulator with ARM translation and the isolated SIP account 301. The physical Android phone using account 2 was not available through adb. After receiving the RC.1 preview with the same APK hash recorded below, the user explicitly confirmed installing and running it and being logged in as account 2. This is a user-attested installation; the physical package/version was not independently queried through adb. Subsequent physical calls passed in both directions, including hold/resume, and the user accepted the audio. Fresh bidirectional Persian text was protocol-acknowledged without an INVITE. See the [overall verification record](VERIFICATION.md) for the exact evidence and scope.

## Changes and regression evidence

The Android Telecom bridge now reserves one platform session per SDK Call object. This prevents repeated `OutgoingProgress` callbacks during SIP retries from registering another platform call when the SIP Call-ID changes. It serializes asynchronous platform setup, checks terminal SDK state before late attachment, uses `setActive()` for an already connected outgoing call, uses supported disconnect causes, and releases the exact session and its listeners/collectors when the platform call ends. SDK access remains on the existing core thread.

An independent read-only review checked the pinned SDK's generated JNI wrapper and the installed AndroidX Core-Telecom implementation. The Java Call wrapper is reused while referenced, so the session map's strong reference preserves its identity across SIP Call-ID changes. The review found no introduced blocking regression. The pre-existing immediate AndroidX acknowledgement of asynchronously queued answer/disconnect work was left unchanged; runtime evidence did not establish it as the cause of the reported failure.

Nine policy tests cover repeated progress, retry identity, terminal state before attachment, a distinct next call, stale cleanup, shutdown, and incoming/outgoing/ended platform actions. RED checkpoint `6f87c69` failed compilation because the new production policy API did not yet exist; GREEN checkpoint `bcf5f94` passed all nine cases. These are extracted-policy tests, not a claim that the reported native dialog was reproduced by a unit test.

The reported SIP media incompatibility and message-routing configuration were diagnosed and corrected at the PBX layer. This Android change adds no PBX IP address, extension-specific codec exception, or server-routing workaround. PBX configuration findings and physical-phone retesting are separate evidence in the overall verification record.

## Build, tests, signing and CI

The following full local gate completed successfully in **10m 34s**:

```powershell
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleRelease --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

- **84 unit tests passed**, with zero failures, errors or skipped tests: 75 existing cases plus nine new Telecom policy cases.
- Strict debug lint's actual XML contains **zero Error/Fatal findings and 126 warnings**. `abortOnError=true` remains enforced. Three additional warnings identify the unused old onboarding illustrations after their replacement; no lint baseline or blanket suppression was added.
- The signed release was rebuilt from the clean committed source with `assembleRelease`, which completed successfully in 7s. AGP's generated version-control-info records the exact source revision above.
- [Clean Android CI run 43636465168](https://github.com/DashSaman/MyTelSoftPhone/actions/runs/43636465168) completed successfully at the same `b475363624a66d7ad604e66d37d86a21fcd79dbe` source commit.
- Coverage percentage was not measured. These checks do not establish that the inherited application is defect-free.

| Artifact property | Verified value |
|---|---|
| Local build output | `app/build/outputs/apk/release/MytellPhone-release-v3.0.1.apk` |
| Public APK basename | `MyTel-v3.0.1-android.apk` — byte-identical copy of the signed output |
| Size | 95,755,459 bytes |
| APK SHA-256 | `f1647dc4aaed9d2108dd5f7061988f49cd3534de4d5c42a3d02d630d62c086b2` |
| Production certificate SHA-256 | `60ebd14405aeb1fe58e277297182fb48cb77d07f8046982a07e494ba49b4d7f4` |
| Minimum / target Android API | 28 / 37 |

`apksigner verify --verbose --print-certs` exited zero and verified the v2 signature with the existing production certificate. Package metadata confirms version 3.0.1/code 30001. Signing identity continuity permits the tested in-place upgrade without clearing account data.

## Executed runtime checks

| Journey | Observed result and limit |
|---|---|
| Old 3.0.0 baseline | The isolated runtime-only 8898 rejection fixture produced the expected media error, followed by a connected call to the existing `*43` echo service. The reported native “another call connecting” dialog did **not** reproduce in this baseline timing. |
| Upgrade to the final signed APK | `adb install -r` succeeded; version became 3.0.1/30001, retaining the configured 301 identity and history. A private app-data backup was taken before the upgrade. |
| Deliberate rejection on 3.0.1 | 301 to the isolated 8898 fixture displayed “Incompatible media parameters.” The PBX log confirms the fixture executed `Hangup(58)`. Telecom briefly showed a connecting call during teardown, then its active/ringing/holding call lists were empty after the ended-call UI returned to main. |
| Subsequent echo redial | 301 to `*43` connected without the reported native dialog. The saved Telecom snapshot shows one ACTIVE call, and the PBX log confirms Answer. The call was ended and the current-call lists became empty. |
| Incoming real Windows call | Windows account 1 called emulator 301. The actual Android notification Answer action accepted the call; the connected call screen reached 00:40 and Telecom showed one ACTIVE call. The microphone was muted near the end, then Android's call UI ended the call. A snapshot 1.3 seconds later showed no active, dialing/connecting, ringing or holding calls and a null foreground call. |
| Incoming timeout | An earlier attempt timed out before the observer answered and produced a missed-call notification. That attempt does not establish a notifier defect. |

The rejection-to-echo check was a **subsequent redial**, not a sub-second stress test. The PBX log clock records rejection at 21:23:27 and echo Answer at 21:24:08, a **41-second gap** including UI captures and tool roundtrips. Both the old and new builds completed the subsequent echo call. The runtime baseline therefore does not prove a before/after reproduction of the intermittent native Telecom race; the source/API correction, policy tests and completed runtime paths are the evidence for this change.

The emulator's Telecom dump retained an old `mPendingCall` identifier from the 3.0.0 baseline across the upgrade, including when all current-call lists were empty. That identifier alone was not classified as an active call or proof that the reported race had reproduced. No live RTP snapshot was captured during the echo call, and no subjective microphone/speaker or end-to-end acoustic-quality result is claimed from these Android checks.

The temporary 8898 fixture was removed afterward by the PBX owner; it did not change a persistent configuration file. Account 1/2 server changes were not performed by the Android test process.

## MyTel onboarding and appearance

The promotional slides now use existing MyTel artwork and product copy, with the visible [MyTel website](https://mytel.one). “Stay connected” replaces the unsupported generic “Secured” title, and “Discover MyTel” replaces the mismatched “Open source” promotion. About, legal and license notices were preserved.

The final signed APK was visually inspected in portrait light and dark themes. The full-color MyTel badge, titles, body text, website link and navigation were visible and readable. Static resource inspection verified the website destination and the phone, landscape and tablet layout variants; all variants compiled and passed strict lint. Landscape/tablet runtime rendering and browser handoff were not marked as passed.

The application theme preference was unchanged and light appearance was restored after the check. After the final call, the application was stopped, its data backed up privately, and the emulator was cleanly closed before Windows compilation resumed. Credentials, raw SIP logs, app-data backups and private media must not be included in public release assets.

An optional combined website-click/rotation/restore action was automatically rejected before execution with the generic reason **“blocked by policy.”** No more specific reason was returned. None of the actions in that rejected batch was counted as executed; the necessary light restoration and return to main were completed separately through allowed scoped actions.
