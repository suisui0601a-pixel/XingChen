# Contributing

Please read [LICENSE](LICENSE) and [SECURITY.md](SECURITY.md) first. XingChen is
source-available under the terms in LICENSE; commercial use requires prior
written authorization. Contributions must be your work or include compatible
license and attribution evidence. Do not copy proprietary Gateway components or
assets.

## Development environment

- JDK 21 and the checked-in Gradle Wrapper (Gradle 9.8.0).
- Node.js 24.19.0 and npm 11.17.0 for the frontend.
- Docker Compose v2 only for the isolated container and integration checks.

Install and check the frontend:

```powershell
cd frontend
npm ci
npm run typecheck
npm test -- --run
npm run build
```

Run the backend suite and package:

```powershell
./gradlew.bat test
./gradlew.bat bootJar
```

Run browser/container E2E only when its documented local prerequisites are
available. Use synthetic fixtures. Never connect CI or routine tests to
production QQ, a paid provider, or the production server. Live model tests are
opt-in and must not receive production credentials.

## Design boundaries

- Keep domain contracts provider-neutral. Provider-specific quirks belong in
  that provider adapter. For example, DeepSeek's function-name spelling is
  translated at the DeepSeek boundary; Core canonical tool names and capability
  policy remain unchanged.
- Do not bypass AgentToolCatalog, CapabilityPolicy, authentication, CSRF, path
  guards, or the remote-bind guard to make a test pass.
- Keep OneBot and external Gateway lifecycle outside the model-provider layer.
- Add tests for behavior changes, migrations, restart/persistence behavior, and
  security boundaries affected by the change.

## Pull requests and commits

Describe the problem, scope, tests, security impact, migration/rollback impact,
and any UI screenshot. Screenshots must be scrubbed of credentials, account
identifiers, private conversations, and infrastructure details. Use focused
changes and meaningful commit subjects, for example:

```text
fix(deepseek): map canonical tool names at transport boundaries
docs: refresh project documentation
```

Never commit `.env`, API keys, tokens, passwords, cookies, QQ login state,
production databases/backups, generated bundles, `node_modules`, or local
development caches. Keep useful architectural comments; do not mechanically
watermark files or modify third-party code to insert a signature.
