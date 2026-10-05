FROM caddy:2.10.2-alpine
# High-port, non-root proxy with an empty capability bounding set. The upstream
# binary carries cap_net_bind_service, which causes exec EPERM under cap_drop ALL.
RUN setcap -r /usr/bin/caddy && test -z "$(getcap /usr/bin/caddy)"
LABEL org.opencontainers.image.title="XingChen isolated high-port Caddy"
