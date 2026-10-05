# 备份数据模型（format 1）

适用：当前 schema 28、标准容器布局。恢复工具是 `tools/recovery.py`，使用 Python 3 标准库；生产操作仅支持 Linux/POSIX。它不是在线热备服务。

| 组件 | 实际权威位置 | 处理 |
| --- | --- | --- |
| SQLite / Flyway | `/data/db/xingchen.db` | Online Backup API 快照；验证 integrity、FK、schema |
| WAL / SHM | DB 相邻文件 | 不单独复制；已提交 WAL 由 Backup API 纳入快照，输出转为 DELETE journal |
| Person / conversation / relationship / memory | SQLite | 包含；保持稳定身份、scope、来源及索引 |
| Persona / simulation / active version | SQLite prompt_profiles / prompt_versions | 包含全部历史与 active pointer；不是 `/data/prompts` 文件 |
| Admin authentication | SQLite console_admin_credentials | 包含密码哈希，不记录到 manifest；HTTP session 不恢复 |
| publicBaseUrl / access / pricing / provider metadata | SQLite | 包含；实际 URL 与 Secure cookie 配置启动时重新加载 |
| SecretStore | `/data/config/*.secret` 与 `*.cleared` | 包含文件；CLEAR tombstone 必须恢复，阻止 env bootstrap 复活 |
| Sticker metadata | SQLite stickers | 包含 |
| Sticker assets | `/data/assets/stickers` | 文件与 DB 同一离线时间点备份 |
| Other assets / reserved prompts | `/data/assets`、`/data/prompts` | 包含普通文件，拒绝 symlink/hardlink/special files |
| Development secrets | `/data/secrets` | 若存在也纳入，但当前工具要求正式 config 路径仍为 `/data/config` |
| stdout / operational ring | Docker 日志 / 内存 | 可选运维信息，不属于恢复权威状态；不包含 |
| `/data/logs` | 当前保留目录 | 排除；需要保留时另做受控日志归档 |
| cache / temporary / maintenance lock | 可重建 | 排除；锁文件在恢复后重新创建 |
| recovery-layout.json | 应用启动生成 | 记录 canonical 布局布尔值，不含 secret；非 canonical 布局拒绝备份 |

DB、config、Sticker 的 external override 仍可用于开发；工具不会猜测外部路径或默默漏备份。启用这些 override 的部署不受 format 1 支持，必须先制定独立的恢复方案。

整个 bundle 含个人数据、密码哈希和可能的明文 SecretStore 文件。目录 0700、文件 0600、同 UID 所有权；它**不是加密格式**。离机存储前必须采用组织认可的加密、认证传输和密钥管理；不得上传公开对象或提交 Git。SHA-256 检测意外损坏，不提供抵御主动篡改的签名认证。

一致性边界：先停止 Core，确认进程退出，再获得同一 DB 的 byte-range maintenance lease。锁覆盖 SQLite、文件、manifest 与 finalize；不是仅锁数据库事务。旧版本没有此锁，不能与该工具并发使用。
