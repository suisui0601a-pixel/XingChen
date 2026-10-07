# Native OneBot production cutover — sanitized record

This is a sanitized operational record. It does not publish production identifiers, secrets, private prompt content or infrastructure endpoints.

## Result

A production Core candidate was switched to the native OneBot path with:

```text
DSH=false
OneBot=true
Model=true
Social=true
```

Observed after cutover:

- Core healthy;
- restart count remained stable;
- SocialRuntime started;
- one intended Core instance was active;
- OneBot forward-WebSocket transport was connected;
- dependent services remained healthy;
- private-message flow was operational.

The initial acceptance remained incomplete until access-control configuration for the target group was corrected. That follow-up is recorded separately in `2026-10-08_ACCESS_CONTROL_PRODUCTION.md`.

No public report includes private Persona/Simulation text, hashes, QQ account IDs, OneBot tokens, provider keys, database contents or private network addresses.
