# XingChen Core

XingChen 是独立开发的 Java AI 社交运行时与双语 Admin Console。核心提供 provider-neutral Agent/Tool 契约、DeepSeek HTTP/SSE 适配器、OneBot v11 HTTP/WS、可恢复会话、身份/关系/记忆、权限策略、usage 记录及受保护的运维后台。数据持久化使用 SQLite 和独立文件目录。

架构：QQ → 独立 OneBot Gateway → XingChen Core → Model Provider。SnowLuma、QQ、旧 qq-bridge 和 DSH 不打包进 Core 镜像。QQ 登录由独立 Gateway 的受支持管理界面完成；模型与 Gateway 凭据由用户在受保护后台录入。默认关闭真实外部连接，测试使用 fake fixtures。

当前处于 `0.1.0-SNAPSHOT` 开发阶段，已完成隔离 Docker、持久化、恢复及后台测试；真实生产 QQ/Model E2E 不因基础设施上线而自动视为通过。Voice 转录/TTS 等未实现能力在界面中明确标记。没有规模、可用性或“绝对安全”保证。

## 许可与商业授权

原始项目代码使用 [XingChen Community Source-Available License](LICENSE)：允许个人、学习和非商业使用及修改；商业部署、SaaS、转售、商业集成和基于本项目的收费支持需事先书面授权。这不是 OSI 开源许可。第三方代码保留各自许可，见 [第三方声明](THIRD_PARTY_NOTICES.md)。商业授权请通过正式仓库联系维护者；本许可文本不是法律意见。

## 后台与部署入口

后台覆盖会话/运行状态、身份/关系/记忆、人格/preset、社交设置、模型、贴纸、usage、权限、安全与运维。后台能力和限制以现有实现与页面提示为准。

首次管理员初始化见 [ADMIN_INITIALIZATION](docs/ADMIN_INITIALIZATION.md)，生产 HTTPS 与维护见 [DEPLOYMENT](docs/DEPLOYMENT.md)，备份/恢复见 [BACKUP_RESTORE](docs/BACKUP_RESTORE.md)。Gateway/模型由操作者独立配置；不要向维护者发送真实凭据。

## 本地构建

要求本机已安装并配置 JDK 21（`JAVA_HOME`/`PATH`）。`.toolchain` 只用于本机开发，不纳入 Git，不能作为克隆仓库的构建前提。仓库提交了 Gradle Wrapper 9.8.0 及发行包 SHA-256 校验；首次运行 `gradlew.bat` 需要访问 `services.gradle.org` 下载发行包，之后可从 Gradle 用户目录缓存使用。离线构建仅在 Wrapper 发行包和项目依赖均已缓存时可行；缺少缓存时 Wrapper 会明确失败，不要改用未校验发行包或修改固定版本。

```powershell
./gradlew.bat clean test
./gradlew.bat bootJar
```

真实 API 测试独立使用 `./gradlew.bat liveTest`，仅在明确设置 `XINGCHEN_LIVE_TEST=1` 并提供 `XINGCHEN_DEEPSEEK_API_KEY` 后执行；普通测试不会读取密钥或访问网络。

探活：`GET /health`。

## Quick Docker Start

容器打包、首次部署、持久化目录及外部 OneBot Gateway 连接说明见 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)。XingChen Core 镜像不包含 SnowLuma。

架构与阶段边界见 `docs/ARCHITECTURE.md`、`docs/PHASE3_REPORT.md`。本地运行默认绑定 `127.0.0.1:3200`；容器部署的应用监听容器网卡，宿主默认仅发布到 loopback，并保留显式远程绑定授权与管理认证检查。
