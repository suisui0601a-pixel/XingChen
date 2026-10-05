# 浏览器首次管理员初始化

此流程不读取操作者的凭据文件，不在 Docker/Compose、Git、日志、命令行或部署脚本中配置真实管理员密码。用户名和密码由操作者在浏览器中手动输入；服务端只存 BCrypt cost 12 哈希。浏览器提交不会自动授予认证 session。

## 阶段一：受限本机初始化

使用独立、空的生产数据卷，运行正式镜像。临时初始化 Compose 使用 `network_mode: host`，应用明确绑定 `127.0.0.1`，仅监听配置的 loopback 端口。容器 profile 的 `server.address` 默认是固定网卡地址，因此必须传入已实测的应用参数 `--server.address=127.0.0.1`，不能只设置 `XINGCHEN_CONSOLE_BIND`。不要定义 `ports:`，不要把初始化容器连接到公网 edge network。此阶段保留 non-root、read-only、cap_drop ALL、no-new-privileges、`/tmp` tmpfs；不得挂宿主根目录、Docker socket 或 SSH key。

非敏感配置：`XINGCHEN_CONSOLE_INITIALIZE_ENABLED=true`、`XINGCHEN_CONSOLE_USE_PERSISTED_ADMIN=false`。`XINGCHEN_CONSOLE_ADMIN_USER/PASSWORD` 必须缺省；publicBaseUrl 暂不设为 HTTPS，Cookie Secure 暂为 false，以便 loopback HTTP 经加密 SSH 隧道传输。真实 HTTPS 配置在初始化后生效，不在 HTTP 上绕过 Secure Cookie。

操作者建立仅本机监听的 SSH 隧道，浏览器进入 `/initialize`，手动输入账号、密码及确认密码。密码至少 14 字符、至多 72 UTF-8 字节，并包含至少四种不同字符。不要向部署执行者发送真实凭据。

初始化 API 默认关闭，只在显式启用时注册。除了既有 CSRF，服务端要求真实 socket 的 local/remote 地址均为 loopback、Host 为 loopback/localhost，并校验 POST 的同源 Origin；cross-site fetch 被拒绝。`Forwarded`、`X-Forwarded-*` 不参与授权。SQL 原子条件插入保证并发请求至多建立一个管理员；凭据和审计在同一事务提交。成功后 GET/POST 均关闭为 404；保持同一数据卷重启仍关闭，即使初始化配置尚未取消。

**到此停止，等待操作者确认“管理员初始化已完成，可以继续验证”。** 自动化不得替操作者提交生产账号、读取凭据文件或继续登录验证。

## 阶段二：公开 HTTPS 上线

确认手动初始化后，停自己的 Core，保留原卷；以同一镜像和同一卷重建 bridge-network 正式 Core。

禁用初始化：`XINGCHEN_CONSOLE_INITIALIZE_ENABLED=false`。设置 `XINGCHEN_CONSOLE_USE_PERSISTED_ADMIN=true`、`XINGCHEN_CONSOLE_ALLOW_REMOTE=true`，应用可在容器内监听网卡，守卫必须实际确认 Flyway 初始化后的数据库含管理员凭据；空库仍拒绝 remote bind。此模式不使用环境变量中的管理员密码作为绑定许可。

设置 canonical HTTPS publicBaseUrl 与 Secure Cookie，只让正式 edge proxy 访问 Core，不公开 application 端口。Caddy 必须明确阻断 `/initialize` 和 `/api/auth/initialize`，再应用安全代理 headers。初始化路由在应用层也关闭；不是依靠 Caddy 一层保护。

首次登录、session/CSRF/Cookie、重启持久化、正式备份及公开 HTTPS 验收，在操作者确认后单独执行。Gateway、Model、Owner 的账户配置仍由操作者通过已有后台完成。

## 边界

loopback 初始化把已认证 SSH 账户及可信宿主机本地进程视为受信任边界；不防御恶意 root 或已获服务器本地执行权限的进程。不能把 `initialize-enabled` 当作绕过认证的 remote-bind 许可。此文档不表示生产首次初始化或 Phase 5 已完成。
