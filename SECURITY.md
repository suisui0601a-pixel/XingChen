# Security policy

Do not publish credentials, personal chat data, production databases or exploit
details in a public issue. Use the repository's private vulnerability reporting
feature when enabled. Otherwise request a private contact channel from the
maintainer without including sensitive details.

The current development line receives fixes; no multi-version support SLA is
promised. Reports should include the affected commit, a synthetic reproduction,
expected behavior and impact. Never test against another person's deployment.

## Deployment boundary

- Core: non-root, read-only root filesystem, dropped capabilities, no Docker
  socket or host-root mount. Keep the data volume private and backed up.
- Establish the first administrator through the explicit loopback-only browser
  initialization flow over trusted SSH; do not place passwords in Compose.
- Public access requires reviewed HTTPS routing, persisted administrator auth,
  CSRF and Secure/HttpOnly/SameSite cookies. Never expose the Core port publicly.
- Forwarded client headers are not trusted by Core. The edge replaces origin
  metadata; client-IP login limits currently aggregate at the proxy address.
- Gateway software and QQ login lifecycle are independently managed. Gateway
  management, OneBot and noVNC ports must not be public.
- Provider and Gateway secrets are entered through the protected Console and
  stored using the existing SecretStore. Never attach production secrets to CI.
- OneBot HTTP and forward-WebSocket credentials are independent SecretStore
  entries. Token APIs return only configured status; do not create a second live
  WebSocket subscription just to test its state.
- Offline recovery bundles include sensitive application state. Protect them and
  keep an off-host copy. A checksum is integrity evidence, not encryption.

No claim of absolute security is made. See docs/HTTPS_CADDY.md and
docs/ADMIN_INITIALIZATION.md for tested boundaries and remaining limitations.
