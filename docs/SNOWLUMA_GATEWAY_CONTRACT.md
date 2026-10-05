# SnowLuma Gateway Contract (Phase 4B-2a)

Research snapshot: 2026-10-02. Sources: `SnowLuma/SnowLuma` `main` README, SDK README and EULA; `SnowLuma/SnowLuma.Docker.Framework` `main` README, `docker-compose.yml`, and `supervisord.conf`. The upstream pages do not expose an immutable commit SHA in the reviewed view. The prior XingChen deployment history refers to SnowLuma v1.14.20, but no local release artifact or source checkout is present to bind the behavior below to that binary. Therefore version-specific undocumented behavior is not assumed.

## Verified architecture

The Docker Framework starts the Linux QQ client under Xvfb and supervisord, and separately starts the SnowLuma Node runtime (`/app/runtime/index.mjs`). Its published noVNC endpoint (6081) is documented as the browser remote desktop used to scan the QQ login QR code. This makes the QR an artifact of the QQ GUI; the reviewed official material does not document a QR HTTP/WS/IPC API or a supported API to refresh it. XingChen must not synthesize a challenge from a guessed endpoint.

SnowLuma's own WebUI (default 5099) is the documented administrator path. Its README describes a guided flow to attach the QQ process and configure OneBot; the capability table advertises account status and logs. No supported public WebUI management API, machine-readable login-state enum, logout action, or reconnect action was identified in the reviewed source/docs. Those abilities are treated as unsupported until the owner publishes a contract.

The Framework compose exposes 3000 HTTP and 3001 WebSocket as OneBot listeners. The OneBot v11 `get_login_info` action returns `user_id` and `nickname`; it proves account identity only when that authenticated action succeeds. It does not distinguish QR_REQUIRED, WAITING_SCAN, AUTHENTICATING, or a QQ GUI logged-out state. An authenticated WebSocket connection / heartbeat is transport status, not proof of QQ login.

## Evidence index

| Repository/source | File or API | Evidence and scope |
| --- | --- | --- |
| `SnowLuma/SnowLuma` main | `README.md`, Quick Start / capability table | Launcher → WebUI 5099; WebUI guided QQ process and OneBot setup; advertised account status/logs. No documented QR/admin REST contract. |
| `SnowLuma/SnowLuma` main | `packages/sdk/README.md` | `SnowLumaHttpClient.getLoginInfo()`; OneBot HTTP client and token; OneBot configuration in `config/onebot_<uin>.json`. This is an SDK over OneBot, not a login-control API. |
| `SnowLuma/SnowLuma.Docker.Framework` main | `README.md` | 6081 is noVNC for QR scanning; 5099 WebUI; 3000 HTTP and 3001 WS; data/config persistence. |
| `SnowLuma/SnowLuma.Docker.Framework` main | `docker-compose.yml` | Ports and volumes; `SYS_PTRACE`, `seccomp=unconfined`; WebUI and OneBot host environment configuration. |
| `SnowLuma/SnowLuma.Docker.Framework` main | `supervisord.conf` | QQ and SnowLuma are separate supervised processes sharing the GUI environment. No login-control RPC is defined there. |
| `SnowLuma/SnowLuma` main | `EULA.md` v1.1, effective 2026-07-21 | Official binary and native components have additional terms; §5.4 requires prior written authorization for inclusion in third-party installers or Docker images/automated deployment. XingChen will not package the proprietary runtime. |
| OneBot v11 | `get_login_info` | Authenticated action returns account user ID and nickname. Does not expose GUI challenge or login lifecycle controls. |
| Existing XingChen history | `docs/ONEBOT_ADAPTER.md`, `docs/DEPLOYMENT_TARGET.md`, `docs/LEGACY_FEATURE_MATRIX.md` rows 5–9, `application.yml` | Prior design treats SnowLuma as a separate gateway; its OneBot URLs/token feed the existing HTTP/WS adapter. This is configuration/runtime history, not evidence of a SnowLuma login-control API. |

## State mapping

XingChen only reports observations supported by its own configuration and OneBot connection:

* `DISABLED`: local OneBot integration switch is off.
* `NOT_CONFIGURED`: required endpoint setting is absent/invalid.
* `CONNECTED`: OneBot WebSocket is connected and heartbeat is fresh. This is transport status only.
* `DISCONNECTED`: configured OneBot transport is not connected.
* `ERROR`: a sanitized status/action failure occurred.

QQ QR-required, waiting-scan, authenticating, and logged-out states are **not observable** through the documented OneBot contract and are not represented as confirmed states. Account identity is present only after a successful `get_login_info` response.

## Management decisions

* QR acquisition/refresh, QQ logout, and QQ login reconnect: `UNSUPPORTED` by the reviewed official machine API. Admin Console links/frames to an operator-configured SnowLuma WebUI/noVNC origin only; it never proxies arbitrary destinations or claims an action succeeded. The WebUI and noVNC remain separate origins and retain SnowLuma's own authentication.
* Account identity: query OneBot `get_login_info`; keep this QQ bot identity distinct from XingChen `Person` membership.
* OneBot status: use the existing OneBot gateway connection/heartbeat plus `get_login_info`; never infer QQ UI state from WebSocket alone.
* OneBot endpoints and non-secret settings are owned by `ConfigService`; updates reconfigure the live OneBot v11 client and reconnect it in place, without restarting the JVM. Token set/replace/clear follows the same apply path.
* OneBot access token is stored outside SQLite in a private local secret file (owner-only directory/file permissions); reads disclose only `configured`. Request bodies and audit rows never log token values. Deployments must mount that secret path as persistent private storage.
* Endpoint policy defaults to loopback. Remote endpoints need a separate explicit opt-in; only `http`, `https`, `ws`, and `wss` schemes are accepted in their corresponding fields. Redirects are disabled in the OneBot client; browser-facing management origins are never fetched server-side (prevents SSRF).
* Gateway-management origin is treated as an operator-provided external WebUI URL, not a backend fetch target. No cookies, credentials, or QR bytes are copied into XingChen.

## Unsupported assumptions / gaps

No supported QR API, QR expiry contract, login state API, account avatar API, programmatic QQ logout, or programmatic QQ reconnect contract was found in reviewed official material. The Docker Framework's supervisor restarts processes as an infrastructure policy, not as a documented user login API. A versioned official API or a separately authorized helper is needed before these controls can be implemented honestly.
