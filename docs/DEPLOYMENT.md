# Deployment

XingChen Core is a Java application with a protected Admin Console. The independent OneBot gateway (for example, a separately installed OneBot v11 implementation) owns QQ login and event transport. Gateway software, QQ credentials, and provider keys are not bundled in the Core image.

## Build and run

Requirements: Docker Engine with Compose v2, plus outbound access to the registries and artifact repositories used by the pinned build stages. The Dockerfile performs the multi-stage frontend and Java build; the host does not need Java or Node installed.

For a local development build:

```sh
./gradlew test
./gradlew bootJar
```

For a container build, select an explicit release/version and image tag; do not use `latest` for a controlled deployment. Inspect the rendered Compose configuration locally and do not publish it, since local environment values may contain deployment-specific data.

The Console defaults to loopback. If the application is bound to its container interface for trusted service-to-service access, publish the host port only on loopback unless a separately reviewed authenticated HTTPS reverse proxy is in place. Never expose the management port directly to the public Internet. Complete the administrator initialization through the documented protected flow; do not put a password in a Dockerfile, image build argument, committed Compose file, or support log.

## Data and secrets

Use a persistent local Docker volume for application data. Keep the database and SecretStore directory private, back them up before upgrades, and verify backups before relying on them. Do not use a shared network filesystem for SQLite without a separately validated locking model. Never delete the active volume as an upgrade shortcut.

Admin credentials and provider/OneBot credentials are runtime configuration. Enter credentials through the authenticated Console or a supported secret mechanism; never commit them, put them in image layers, or print them into logs or command history. OneBot HTTP and WebSocket credentials are separate; see [OneBot adapter](ONEBOT_ADAPTER.md).

## OneBot network connection

OneBot integrations are opt-in. For normal SocialRuntime processing, the effective switches are Social + OneBot + Model. DSH is an independent integration, not a prerequisite for ordinary social messages. A DSH-free configuration can therefore use:

```text
DSH=false
OneBot=true
Model=true
Social=true
```

This is an example, not a requirement for every installation. Disable integrations that are not configured.

When Core and the gateway are in separate Docker containers, attach only the intended services to a trusted network and use the gateway's Compose service name or configured network alias, for example:

```text
HTTP: http://gateway-service:3000/
WS:   ws://gateway-service:3001/
```

Inside a container, `127.0.0.1` refers to that container itself; it does not refer to another service. Do not publish OneBot HTTP/WS ports to the public host. The adapter rejects non-loopback endpoints by default; enabling a separately reviewed service-DNS endpoint requires the explicit non-loopback opt-in. Keep the gateway isolated from public ingress and publish only its required local administration interface on host loopback.

The Core uses the HTTP credential only for HTTP actions and the WS credential only for its forward-WebSocket subscription. HTTP connection testing uses read-only `get_login_info`; WS status reports the existing subscription and must not create a second production consumer. Status APIs never return token values.

## Upgrade and rollback

Before an upgrade, record the current image digest/revision and create a verified backup using the repository's recovery procedure. Stop the Core before taking a raw SQLite copy. Deploy an explicit candidate image with the existing data volume only after isolated validation. Schema migrations are forward-only; do not attach a migrated database to an older image as an in-place downgrade. Rollback requires the verified pre-upgrade backup restored into a separate data volume and the matching previous image. Preserve the original volume until recovery has been verified.

## Verification boundary

Normal tests use temporary data and fake or disabled external integrations. Isolated acceptance can validate a real model provider with a Fake OneBot, but it does not prove a production QQ login or production message round-trip. A production native-OneBot cutover is a separate controlled deployment and must not be described as complete until the active authenticated WebSocket, HTTP action, and real message path have each been verified.
