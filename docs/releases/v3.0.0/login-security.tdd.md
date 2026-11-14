# SIP login lifecycle and privacy checks

Journeys were derived from the v3 request and confirmed source defects:

- Repeated login taps reserve one attempt before queued core work runs.
- Cancelling, timing out or leaving the assistant releases only the attempt's
  newly created account and authentication, so the settings can be corrected.
- Late registration/timeout callbacks cannot complete or cancel a newer retry.
- Password whitespace is preserved and user input is snapshotted before SDK work.
- Directory errors do not log full access-bearing URLs.
- SIP credentials, message history and recordings are excluded from Android
  cloud backup and device transfer by the manifest and both XML rule formats.

## Executed automated evidence

`SipLoginAttemptTest` compiled with the cached Kotlin 2.4.20 compiler and JUnit
4.13.2 on the Android Studio JBR. The first compile failed on the missing
`SipLoginAttempt` API before implementation (test commit `77f6771`). The same
test file compiled after implementation and JUnit reported:

```text
JUnit version 4.13.2
.....
Time: 0.029

OK (5 tests)
```

Implementation checkpoint: `017241a`.

The five tests cover repeated submission, cancellation plus retry, a stale
timeout, single completion and twelve competing calls across four threads.
The project suite also includes these tests under:

```text
gradlew.bat testDebugUnitTest --tests org.linphone.utils.SipLoginAttemptTest
```

Both backup XML files were parsed successfully as XML; the owned diff passed
`git diff --check`. These checks do not prove SIP registration, media delivery,
device backup behavior or the visual placement of the cancellation button.
See the v3 platform/release validation report for those executed target checks.

No coverage percentage was measured for this focused run.
