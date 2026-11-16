---
paths:
  - "**/src/test/**"
  - "**/src/androidTest/**"
  - "benchmark/**"
---

# Tests

- Names in backticks describing behaviour: `` fun `shows an error when offline`() ``.
- Use `FakeTdlibClient` and `TdApiFixtures` from `core:testing`; never mock TDLib's `Client`.
- Never delete, disable or loosen a test, a golden, `benchmark/budgets.json` or a baseline to
  make a check pass. If one is wrong, say so in the PR and leave it for the owner.
- Re-record Roborazzi goldens only for an intended visual change, only in the affected
  module, and list the changed goldens in the PR description.
- Every new control gets an `assertAccessible()` check.
