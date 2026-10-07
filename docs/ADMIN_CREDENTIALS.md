# Administrator credentials / 管理员凭据

This document explains where XingChen Console administrator credentials come from and, just as importantly, where they **cannot** be recovered from.

本文说明 XingChen Console 管理员账号/密码从哪里产生，以及哪些地方**无法**找回明文密码。

## Short answer / 一句话结论

- XingChen has **no public default administrator password**.
- The first administrator is created either by the bootstrap environment variables on an empty database or by the protected local initialization flow.
- After creation, the server stores a BCrypt hash. The original password cannot be read back from SQLite, Console APIs, logs, or SecretStore.
- If you forget the password, do **not** “look it up in the database” and do not replace the hash manually.

- XingChen **没有公开默认管理员密码**。
- 首个管理员由“空数据库上的 bootstrap 环境变量”或“受保护的本机初始化流程”创建。
- 创建后服务端只保存 BCrypt 哈希，无法从 SQLite、Console API、日志或 SecretStore 反查原密码。
- 忘记密码时，不要尝试“去数据库找密码”，也不要直接手改哈希。

## Creation paths / 创建方式

### 1. Bootstrap on an empty credential table / 空库 bootstrap

`ConsoleAdminBootstrap` accepts:

```text
XINGCHEN_CONSOLE_ADMIN_USER
XINGCHEN_CONSOLE_ADMIN_PASSWORD
```

Only when `console_admin_credentials` is empty can these values create the first account. The password must satisfy the application length checks. Once a persisted administrator exists, the stored BCrypt hash is the authoritative credential.

`ConsoleAdminBootstrap` 可读取：

```text
XINGCHEN_CONSOLE_ADMIN_USER
XINGCHEN_CONSOLE_ADMIN_PASSWORD
```

仅当 `console_admin_credentials` 为空时，这组值才能创建首个账号。已有持久化管理员后，数据库中的 BCrypt 哈希才是认证依据。

Do not keep a real production password in a committed `.env`, Compose file, Dockerfile, image build argument, support log, issue, or chat message.

不要把真实生产密码提交进 `.env`、Compose、Dockerfile、镜像构建参数、支持日志、Issue 或聊天消息。

### 2. Protected local initialization / 受保护的本机初始化

When explicitly enabled, `/api/auth/initialize` can create the first administrator from a loopback-only initialization session. See `docs/ADMIN_INITIALIZATION.md`.

显式开启初始化能力后，可以通过仅允许本机回环访问的 `/api/auth/initialize` 创建首个管理员。详见 `docs/ADMIN_INITIALIZATION.md`。

Initialization does not log the user in automatically. The new account must authenticate normally afterwards.

初始化成功不会自动获得登录 Session，之后仍需通过正常登录流程认证。

## Where is the username? / 用户名在哪里

If already logged in:

```text
GET /api/auth/session
```

returns the authenticated username.

The database also stores the username in `console_admin_credentials`. Reading the username for recovery diagnostics is different from reading a password; the password column contains only a hash.

如果已经登录，`GET /api/auth/session` 会返回当前认证用户名。

数据库的 `console_admin_credentials` 也保存用户名。用户名可以用于恢复诊断，但密码列只有哈希，不存在可读取的原始密码。

## Where is the password? / 密码在哪里

The original password is **not stored**.

The credential table stores a BCrypt cost-12 hash. The application verifies a submitted password against that hash; it does not decrypt the hash.

原始密码**不会被保存**。

凭据表保存 BCrypt cost-12 哈希。应用只会验证“用户输入的密码是否匹配哈希”，不会也不能把哈希解密成原密码。

Provider API keys and OneBot credentials are separate secrets and must not be confused with the Console administrator password.

模型 API Key、OneBot HTTP/WS Token 与 Console 管理员密码是不同的凭据，不要混用。

## Changing a known password / 已知当前密码时修改

The authenticated Console provides a password-change operation. It requires the current password and a confirmed new password. On success, the credential generation changes and existing sessions are invalidated.

已登录 Console 提供管理员密码修改功能。修改时必须提交当前密码和两次一致的新密码。成功后凭据 generation 会变化，旧 Session 会失效并要求重新登录。

## Forgotten password / 忘记密码

The current public runtime does not expose a “show password” or unauthenticated remote reset endpoint.

当前公开 Runtime 不提供“显示密码”或“远程免认证重置密码”的入口。

Until a dedicated, audited maintenance reset workflow is implemented:

1. do not modify `console_admin_credentials` by hand;
2. do not delete the credential row to reopen initialization on a live production database;
3. do not disable authentication;
4. prefer a verified recovery path or an explicitly reviewed maintenance procedure.

在专门的、带审计的维护重置流程实现前：

1. 不要手工改 `console_admin_credentials`；
2. 不要通过删除凭据行来让生产库重新进入首次初始化；
3. 不要关闭认证；
4. 应优先使用已验证的恢复方案，或单独审核过的维护流程。

## Notes for deployment agents / 给部署 Agent 的说明

An AI/automation agent should never ask the operator to paste production credentials into chat. It should pause at the credential boundary and instruct the operator to enter them into a local terminal or browser form.

AI/自动化 Agent 不应要求操作者把生产凭据贴进聊天。遇到凭据边界时，应暂停并要求操作者只在本机终端或浏览器表单中输入。
