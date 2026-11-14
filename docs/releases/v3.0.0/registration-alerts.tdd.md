# Cancelled-login registration alert regression

Android runtime QA reproduced: account 301 remains Online; a new account login
to an unreachable test server is cancelled and its form becomes editable; a
later callback nevertheless displays “Account(s) connection error”.

The main model previously added alerts directly from every registration
callback, including callbacks for accounts already removed. Removal also
cleared all account alerts and refreshed surviving accounts, allowing the old
failure to reappear while temporarily masking genuine failures elsewhere.

The fix ignores callbacks for accounts no longer configured and derives both
account alerts from current account snapshots. Removing an attempt excludes
that account without refreshing or modifying the remaining accounts. Genuine
default and other-account failures remain visible; disabled secondary accounts
do not masquerade as failed connections.

`MyTelRegistrationAlertsTest` was compiled before implementation, failing on the
missing resolver API (RED checkpoint `04d8abd`). The same tests then compiled
with the implementation; cached Kotlin 2.4.20/JUnit 4.13.2 reported:

```text
JUnit version 4.13.2
......
Time: 0.027

OK (6 tests)
```

The six cases cover removal cleanup, preservation of another failed account,
preservation of a genuine default failure, disabled secondary accounts,
disabled defaults across network loss and an empty configured-account list.
This focused run measured no coverage percentage. Full Android compile and
repeating the cancellation flow on the rebuilt APK are separate release checks.
