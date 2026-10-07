# Concurrency validation record — 2026-10-08

Status: `NON_REPRODUCIBLE_HISTORICAL_FAILURE`

A historical stress run once observed fewer successful sends than the test expected. The original failure artifact was no longer available, so the event could not be root-caused from the original XML/log.

Subsequent evidence:

- isolated original stress test: 100/100 passed on the public baseline;
- public full backend suite: 10/10 repeated runs passed;
- sanitized/private-equivalent full backend suite: 20/20 repeated runs passed;
- original three-send stress assertion: 200/200 passed without weakening the assertion;
- startup-gate → stress sequence: 100/100 passed in the same worker JVM;
- test boundary diagnostics observed no active prior test threads at the measured boundary.

A separate, reproducible issue was found in a private SQLite test cleanup helper: teardown could fail with a non-empty temporary directory. That test-fixture cleanup race was corrected and regression-verified. It was not proven to be the cause of the historical send-count anomaly.

The final classification is therefore:

```text
NON_REPRODUCIBLE_HISTORICAL_FAILURE
```

This must not be rewritten as “Runtime send-loss bug fixed,” because no Runtime bug was identified or fixed.

Private prompt content, production credentials, account identifiers and deployment endpoints are not part of this report.
