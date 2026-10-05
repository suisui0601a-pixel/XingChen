# Deployment target (not implemented in Phase 1)

Target one `XingChen Core` Docker container containing Java Core, Web Console, Memory, Social Agent, DSH adapter and OneBot adapter. SnowLuma remains a separate official gateway container. Runtime persistence mounts: `/data/db`, `/data/config`, `/data/prompts`, `/data/logs`, `/data/assets`. A future Compose deployment should be `docker compose up -d`; the Console can use internal port 3200 and must not depend on a fixed domain. Later, a subdomain can route through Caddy HTTPS. Credentials remain external secrets, not image layers or ordinary config.

Phase 1 does not build/publish a production image, open ports, configure DNS/TLS, connect QQ, or deploy anything.
