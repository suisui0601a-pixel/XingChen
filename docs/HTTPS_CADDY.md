# HTTPS / Caddy boundary

Phase 5 公网接入已实际验证；当前证据见 PHASE5_PRODUCTION_GO_LIVE.md，实际拓扑
与 reload 操作见 DEPLOYMENT.md 的 PRODUCTION OPERATIONS。下面的 Phase 4C staging
和“DOCUMENTED，非 TESTED”段落是历史验证范围，不代表当前公网状态。

默认 Core Compose 仍只绑定宿主机 `127.0.0.1:3200`。SnowLuma 不打包、不下载、不分发；Gateway 由用户独立管理。Caddy 必须连接自己的专用 Core network，不能连接不相关 production 服务。

## 安全假设

- `server.forward-headers-strategy: none`：Core 不信任任何 Forwarded / X-Forwarded-*，不能用任意客户端的 XFP=https 假冒 TLS。
- 明确设置 `XINGCHEN_PUBLIC_BASE_URL=https://PLACEHOLDER.example`；启动读取有效持久化配置，旧 DB 中显式值优先 env，修改后需要重启自己的 Core。
- session cookie 和 CSRF cookie 均 Secure、HttpOnly、SameSite=Strict；HTTPS 是明确配置而非转发头推断。HTTP 页面不能用于生产登录，Caddy HTTP 只 redirect。
- Caddy 固定 Host/XFH/XFP 并删除外来的 Forwarded，不从请求头接受“可信代理身份”。当前不实现 forwarded client IP，登录限流可能按代理 IP 聚合；不得开启未经审核的 forwarded trust。
- publicBaseUrl 当前是 Console 的持久化公开 URL 配置，不承诺所有业务链接均由它生成；依实际功能测试，不虚构 redirect API。

## 配置示例

`deploy/Caddyfile.example` 使用 `CONSOLE_DOMAIN`、`ACME_EMAIL` 环境变量 PLACEHOLDER，反代 `xingchen-core:3200`。独立运营 Caddy 时持久化 `/data` 与 `/config` 的证书状态，保护 CA/account keys，使用明确 official image/version/digest。不要挂 Docker socket。Core 的既有 read-only、non-root、cap_drop、no-new-privileges 和必要 data volume 保持不变。

没有强制 production overlay：已有反向代理/端口管理各异，不能提供自动抢占 80/443 的默认 Compose。本轮服务器的既有 production Caddy完全不变。对真实部署，管理员自行评审 domain/DNS/network/证书/公网认证边界后加载示例。

## 本轮 staging

`deploy/Caddyfile.staging` 使用 internal TLS：HTTPS localhost:13220、HTTP localhost:13221 redirect。Docker 只发布 `127.0.0.1` 高端口。导出 local CA 的**公开 root certificate**到私有测试目录，客户端使用该 CA 验证服务器证书；不以 `curl -k` 或浏览器 ignoreHTTPSErrors 证明 TLS。

高端口 non-root staging 使用 `deploy/Caddy.Dockerfile`：官方 Caddy 2.10.2 binary 带有 cap_net_bind_service，配合 cap_drop ALL 会 exec EPERM。在独立 image 构建期去掉不需要的 file capability，而不是给容器加 capability 或 privileged；Core image/security 不变。这仅用于独立高端口代理，不改变任何已有 production Caddy。

测试目标：HTTPS /health、Console HTML、未登录 API 401、CSRF、login/session/logout、安全 headers/cookies、HTTP redirect，以及恶意 Forwarded 请求不改变有效安全配置。测试后移除 staging 容器/网络/卷/local CA 数据。

本轮浏览器 smoke 使用精确服务器 leaf SPKI pin（Chromium）及公开 CA（Node API requests），不是全局 ignoreHTTPSErrors；链和 hostname 另外由标准 TLS client 校验。不会修改管理员系统 trust store。结果及清场见 `PHASE4C2_EVIDENCE_AUDIT.md`。

Phase 4C-3 的独立复验使用 HTTPS `13320` / HTTP `13321`，不是复用 4C-2 的运行容器。自己的测试 Caddyfile 必须同步替换 site port 和 redirect destination；Core 仍经专用 Docker service alias 访问 `3200`。先通过当前 HTTP 管理 API 持久化 `https://localhost:13320`，再重启自己的 Core，使 Secure Cookie 的启动快照生效；不要在切换之后继续用 HTTP 登录来绕过 Cookie 策略。跨 SSH browser tunnel 只导出公开 CA，TLS 私钥不能复制到客户端或 Git。标准 TLS client 校验 chain/hostname，Chrome 仅 pin 本次 leaf SPKI，测试完停止自己的隧道并删除临时公开 CA。实际验收与非公网 ACME 的范围见 `PHASE4C3_EVIDENCE_AUDIT.md`。

## Header 职责

Core：X-Content-Type-Options=nosniff，X-Frame-Options=DENY，Referrer-Policy=no-referrer。Caddy：仅在真实 HTTPS production 设置 HSTS。internal staging 不启用 HSTS，避免污染浏览器。CSP 尚未完成产品级设计，明确 deferred；不临时加会破坏 Console 的策略来冒充完整 CSP。

## Actual public ACME：DOCUMENTED，非 TESTED

真实域名的 A/AAAA 必须指向正确主机；确认 IPv6 不指错。HTTP-01 需要公网 80，TLS-ALPN-01 需要公网 443，DNS challenge 需要额外审核的 provider 配置/凭据；本轮不申请证书、不改变 DNS/firewall、也不抢 production 80/443。

将 Caddy certificate/account storage 持久化、私有备份；保持续期网络畅通、时钟正确，监控证书到期与 renewal 错误。发布前单独审查 firewall：公网仅审核后的 HTTPS，后台 Core 与 Gateway 管理口禁止裸露。所有账号/API/Gateway 凭据通过受保护运行配置提供，不能放进 Caddyfile/Dockerfile/Git。
