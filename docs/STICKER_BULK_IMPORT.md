# Bulk sticker import / 批量表情包导入

This guide describes a public, reusable workflow for importing a large curated sticker pack without bypassing Console authentication or writing the Sticker database directly.

本文说明如何在不绕过 Console 认证、不直接写 Sticker 数据库的前提下，批量导入已整理好的表情包。

## Runtime model / 运行时模型

The managed sticker root is normally:

```text
/data/assets/stickers
```

The database stores metadata such as path, SHA-256, tags, note, usage and source. Image bytes remain filesystem assets; they are not stored as SQLite BLOBs.

受管 Sticker 根目录通常为：

```text
/data/assets/stickers
```

数据库保存 path、SHA-256、tags、note、usage、source 等元数据；图片本体保存在文件系统，不作为 SQLite BLOB 写入。

Supported import formats are PNG, JPEG, GIF and WebP. The current per-file limit is 10 MiB.

支持 PNG、JPEG、GIF、WebP；当前单文件上限 10 MiB。

## Do not / 禁止

Do not:

- copy files directly into `library/`;
- insert rows directly into the `stickers` table;
- disable CSRF or Console authentication;
- re-encode curated assets merely to make an import pass;
- chmod application data to 777;
- put Console credentials in the manifest, script, shell command line, log, or resume-state file.

禁止：

- 直接把图片复制进 `library/`；
- 直接 INSERT `stickers` 表；
- 关闭 CSRF 或 Console 认证；
- 为了“导入成功”而自动转码已整理素材；
- chmod 777；
- 把 Console 凭据写入 manifest、脚本、命令行、日志或断点状态。

## Recommended pack format / 推荐包格式

A curated ZIP may contain:

```text
manifest.csv
category-a/...
category-b/...
```

The manifest should provide at least:

```text
id
relative_path
category
sha256
```

Before any production write:

1. verify every manifest row has exactly one file;
2. verify no orphan files;
3. verify IDs/paths are unique;
4. recompute SHA-256 for every file;
5. stop if the manifest and ZIP disagree.

正式写入前应确保 manifest 行、文件、ID、路径与 SHA-256 一一对应；任何不一致都应先停止。

## Backup gate / 备份门槛

Take and verify a fresh production backup before the first upload.

When using Docker named volumes, do not assume the host-side Docker volume path is traversable by UID 10001. A safe deployment may expose the live data and backup parent through a temporary reviewed bind mount/helper path while preserving the real volume ownership and modes.

第一张上传前必须创建并验证新的生产备份。

使用 Docker named volume 时，不要假定 UID 10001 能遍历宿主机 Docker 卷父目录。可以用临时、经过审核的 bind mount/helper 路径暴露 live data 与 backup parent，同时保持真实卷权限不变。

A failed backup blocks the import. Do not “continue because the Core is healthy.”

备份失败即阻止导入，不能因为 Core healthy 就跳过。

## Console authentication / Console 认证

A batch client should use the same supported flow as the browser:

```text
GET  /api/auth/csrf
POST /api/auth/login
GET  /api/auth/session
POST /api/stickers/admin/upload
```

Keep a single cookie jar/session. Send the current CSRF token on state-changing requests.

批量客户端应使用和浏览器相同的认证链路，并维持同一 Cookie Session；所有写请求携带当前 CSRF token。

Credentials should be entered interactively into a local terminal, kept only in process memory, and never persisted.

用户名/密码应只由操作者在本机终端交互输入，并仅保留在进程内存中。

## Upload semantics / 上传语义

For each manifest entry:

1. recompute local SHA-256 and compare with the manifest;
2. map the manifest category to one or more explicit tags;
3. `POST /api/stickers/admin/upload` as multipart:
   - `file`
   - `tags`
4. validate the returned metadata:
   - SHA-256 equals the manifest;
   - expected tags are present;
   - `enabled=true`;
   - the path belongs to the managed library.

逐条上传时应重新核对 SHA、带上 manifest 分类 tag，并检查服务端返回的 SHA / tags / enabled / path。

The service itself performs extension/content checks, file-size enforcement, safe-path checks and SHA-256 deduplication. Do not reimplement those rules in a second database writer.

正式业务层已经负责格式、大小、安全路径和 SHA 去重，不要另写一套数据库导入器绕开它。

## Batching and resume / 分批与断点

Prefer serial upload or very low concurrency.

Checkpoint every 25-50 files:

- successful/imported;
- already existing by SHA;
- rejected;
- current Core health;
- restart count;
- disk space.

建议串行或极低并发，并每 25～50 张保存一次非敏感断点状态。

A resume-state file may contain:

```text
asset id
relative path
sha256
category
result
returned sticker id
timestamp
```

It must not contain credentials, cookies or CSRF tokens.

断点文件不得包含用户名/密码、Cookie 或 CSRF token。

## Error policy / 错误策略

- `401`: stop and re-authenticate.
- `403`: stop and fix authentication/CSRF; do not bypass.
- validation/size rejection: record the asset as rejected; do not auto-convert.
- repeated `5xx`: pause the batch and investigate.
- uncertain network completion: query/deduplicate by SHA before blindly retrying.

遇到 401/403 应停下处理认证问题；校验失败应记录拒绝，不自动转码；连续 5xx 应暂停；网络结果不确定时先按 SHA 确认服务端状态。

## Completion invariant / 数量闭合

For an input set of `N` assets:

```text
IMPORTED + ALREADY_EXISTING + REJECTED = N
UNACCOUNTED = 0
```

任何导入任务最后都应满足数量闭合，`UNACCOUNTED` 必须为 0。

## Post-import verification / 导入后验收

After the batch:

1. page through `GET /api/stickers/admin`;
2. verify category coverage, SHA and enabled state;
3. preview a sample from every category via `/api/stickers/{id}/content`;
4. send a few stickers through the real OneBot path in an authorized conversation;
5. confirm no duplicate sends, permission errors or “asset unavailable” failures.

完成后应检查后台列表、每类抽样预览，并在授权 QQ 会话中实发少量图片。

## Report template / 报告模板

```text
Expected:
Manifest verified:
Imported:
Already existing:
Rejected:
Unaccounted:
Categories:
Tags preserved:
SHA mismatches:
Preview:
QQ send:
Backup:
Backup verify:
Core health:
Restart count:
Credentials exposed: NO
Status: COMPLETE / PARTIAL / BLOCKED
```
