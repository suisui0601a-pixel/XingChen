# Phase 5 production infrastructure evidence

Evidence date: 2026-10-05. Credentials, cookies, real backups and private server
inspection data are excluded from this source document.

## Exact code / deployment identity

- Sealed 4C baseline: `28d41c1f236aae6435ef007620dc35c21e346d3d`.
- Built application revision: `b4a8997da6a0c7982a1111b7cdd6afbf534f3de7`.
- Exact local OCI image ID:
  `sha256:cf875a8f855d65cca0b379b32b93676e330d91f1802c02ba10defa6333521e89`.
- Formal multi-stage Dockerfile build; 144,365,812-byte image. No prebuilt jar
  shortcut or live source patch. This is a local image, not a public image release.
- Subsequent configuration/docs commits are **not** falsely attributed to the
  application image. The reviewed deployment Compose is applied separately.
- Core `xingchen-core`, persisted volume `xingchen-prod-data`, subpath `live`.
- Shared edge bridge `xingchen-prod-edge`: Core and existing Caddy only. No Core
  host port mapping; no public 3200. Existing Zetu API/Web retain their networks.

## Administrator initialization and acceptance

Added explicit, one-time, real-loopback browser initialization; no fake login or
production password passed by automation. The user manually initialized their
administrator over SSH and later confirmed public login, Overview, Memory,
Operations, Security, logout and re-login all normal.

Synthetic isolated Docker verification passed real CSRF, tunneled Host/Origin
port handling, spoof rejection, atomic first-admin claim, protected API 401,
closed initialization after restart, persisted-admin remote bind and Secure cookie.

Two harness issues were corrected without changing application semantics: tmpfs
needed mode 1777; a request-cache-created anonymous JSESSIONID does not imply
authentication. The final loopback check accepts the equivalent IPv4-mapped IPv6
representation `[::ffff:127.0.0.1]:3200`, not a wildcard listener.

Production same-volume recreate and planned Core-only restart both reached healthy
with an unchanged administrator password hash. No username/hash/password values
are recorded here. No bootstrap username/password exists in the container env.
Post-restart manual login confirmation remains a separate acceptance item; do not
replace it with the assertion that an unchanged hash is a logged-in session.

## Public HTTPS / proxy

- Production domain and canonical IDNA hostname are intentionally omitted from
  this public record.
- DNS A verified against deployment target; no AAAA address found. DNS unchanged.
- Existing Caddy was validated and **hot reloaded**, not restarted in this switch.
  Only the XingChen site changed; original Zetu site text remained untouched.
- Caddy network attachment persisted in its existing domain Compose overlay.
- Core ignores forwarded headers. Edge fixes Host/XFH/XFP and removes Forwarded
  and external XFF; Origin is not rewritten to bypass CSRF.
- Actual standard TLS chain and hostname verification passed. Existing managed
  Let's Encrypt issuer YE2 certificate was reused, not newly claimed as issued:
  `notBefore 2026-09-29 07:39:56 UTC`, `notAfter 2026-12-28 07:39:55 UTC`.
- Existing certificate/account storage and automatic HTTPS renewal remain enabled.
- Public HTML 200, protected API 401, initialization page/API 404, Secure /
  HttpOnly / SameSite=Strict CSRF cookie; malicious forwarding headers do not
  grant access. Independent anonymous Chrome browser smoke had zero page errors
  and zero failed requests. It did not enter production credentials.
- Session/CSRF controls, rotation, rate limiting and proxy boundary have isolated
  test evidence. Forwarded client IP is deliberately not trusted; rate limiting
  can aggregate by Caddy's IP. CSP remains documented as deferred.

## Backup and resources

First offline format-1, schema-28 bundle:
`/opt/xingchen-production/backups/public-switch-20261005T073927Z/bundle`.
Unmodified recovery tooling backup and verify passed. A stopped-Core offline copy
was used to satisfy strict owner checks without changing live-volume permissions.
The temporary copy was removed after verification. Root-private backup is outside
the production data volume. **OFF-HOST COPY REQUIRED**; no off-host copy claimed.

Formal build: root free start 18,501,185,536 bytes; minimum 15,856,164,864;
finish 18,000,416,768. Lowest available RAM 1,828,921,344 bytes. No resource warning,
no OOM observed and no swap created. Dedicated builder was removed.

Public-switch validation: root free start 17,976,352,768 bytes;
minimum 17,975,459,840; finish 17,975,488,512. Lowest available RAM 2,676,592,640.
WARN 14 GiB / HARD STOP 12 GiB retained. Isolated stage container/network/volume
were removed after testing; production image and volume remain.

## Existing production / deferred configuration

Zetu API/Web IDs, images, startup timestamps, networks, health and restart counts
unchanged during public switch and Core restart. Caddy ID, image, startup timestamp
and restart count unchanged; its only network addition is the reviewed edge.
Zetu public HTTPS returned 200. A previously authorized single Caddy admin-enable
restart predates this switch and is documented separately; it is not hidden.

Old DSH / qq-bridge / SnowLuma environment remains decommissioned; no old archive
was modified. Core contains no proprietary Gateway or QQ component.

QQ / Model: **USER CONFIGURATION DEFERRED**, not real E2E PASS. XingChen does not
fetch or refresh QQ login QR codes. QQ login requires independently deployed
SnowLuma official WebUI/noVNC/QQ GUI; never synthesize a logged-in status.

## Regression / acceptance at the earlier infrastructure checkpoint

Application-code regression: backend 350/350, frontend 41/41, typecheck and bootJar
PASS; full browser fixture suite 17 passed / 2 opt-in container tests skipped.
Separate real-browser initialization and real Docker initialization/restart
checks passed. Deployment/licensing docs do not change the tested application code.

At that checkpoint, infrastructure was ACTIVE, Phase 5 remained NOT COMPLETE, and
there was no real QQ/model E2E claim. The later operator-reported tool-mapping
acceptance is recorded below; it does not by itself close the remaining Phase 5
publication, backup, or final-audit gates. No final PDF or next-phase work is
claimed here.

## Subsequent tool-mapping deployment acceptance

On 2026-10-05 the operator reported that application revision
`264ebde26329d6a2cf48e8f4ac867daa71c58bd9` was deployed and the production
DeepSeek model `deepseek-v4-flash` completed the following acceptance:

- Provider-local reversible mapping: internal `qq.send`, `qq.readRecent`, and
  `memory.search` serialize as `qq_send`, `qq_readRecent`, and `memory_search`.
- Request tool definitions, non-stream response reverse mapping, historical
  assistant `tool_calls`, fragmented streaming tool calls, and the second-turn
  tool-history round trip were reported PASS.
- Real QQ message/tool round-trip through QQ → SnowLuma → OneBot → XingChen Core
  → DeepSeek → Core → QQ was reported PASS by the operator.

This mapping changes only provider transport spelling. Core canonical tool
contracts and permission checks are unchanged. OneBot, SnowLuma, Zetu, and Caddy
were reported unchanged by this application fix. No account IDs, message text,
prompt, request body, credentials, cookies, or production hostname are recorded.

This is an operator-provided production acceptance update; this documentation
sync did not independently send or inspect a live production conversation.
