# DeepSeek 工具名边界修复

## 范围

修复仅位于 `adapter/deepseek`。内部 canonical tool names、AgentToolCatalog、CapabilityPolicy、ToolCall、执行器、Prompt 契约和 SQLite 数据均保持不变；不修改 OneBot、SnowLuma、生产凭据或生产容器。

## 双向映射

每次 `complete` / `stream` 为对应 ModelRequest 创建一个不可变 ToolNameCodec 快照，HTTP 重试复用该快照。没有 JVM 全局可变映射。

出站候选 spelling 将 `.` 转为 `_`，但反向解析仅查显式 Map，不进行 `_` 转 `.` 的猜测。例如 `qq.send` ↔ `qq_send`、`memory.search` ↔ `memory_search`。

- 构造时校验 `[A-Za-z0-9_-]{1,128}`，并检查全量 wire 唯一性。
- `abc.def` 与 `abc_def` 同时存在会 fail-fast，网络请求尚未发送。
- 历史 assistant tool_calls 和 TOOL 消息名称纳入出站映射；这不会将历史工具加入本轮可调用集合。
- 仅本轮 request.toolDefinitions 中提供的工具允许入站解码。未知/未提供的 wire 名称返回固定、脱敏的 ModelProviderException，不进入 Core 执行器。
- 已返回 Core 的 ModelToolCall 仅包含 canonical 名称；后续权限判断仍由既有 catalog/capability 机制负责。

四个边界：tools definition 出站、历史 assistant tool_calls 出站、非流式响应入站、流式碎片拼装完成后的入站。TOOL 结果消息的名称也做出站编码，tool_call_id 不变。

Prompt 文本中合法出现的 canonical 名称不被全局替换；修复的是 JSON function.name transport 字段，而不是人格/记忆/身份数据。

## HTTP 错误诊断

保留 HTTP status，并尝试解析最多 8 KiB 的 JSON error。只有固定白名单中的 error.type/error.code 可进入异常字段与安全摘要；未知值、畸形/超长 JSON 及所有自由文本 error.message 均丢弃。流式非 2xx 的 body 在重试前关闭。

不记录 Authorization、API Key、request body、Prompt、messages 或 tool arguments。不保留包含 provider 原文的异常 cause 链。此修复不新增公共错误信息接口，也不放宽后台认证。

## 验证与部署边界

隔离 HTTP fixture 覆盖合法工具目录、双向恢复、fragmented name/arguments、历史消息、碰撞、未知工具和错误脱敏。fixture 仅监听本机且使用虚构 key；不得把它写成真实 DeepSeek 生产工具调用或 QQ E2E 已通过。

旧 DeepSeekProviderContractTest 的 provider 响应 fixture 改为合法 wire spelling，内部 canonical 断言及原有重试/取消/用量等语义保留。

生产部署需另行授权。部署后必须重新验证真实私聊、工具循环历史与上下文，不能仅凭连接测试或本地回归认定原生产 HTTP 400 已消失。

## 本地回归记录（2026-10-05）

- 后端 `test`：380 tests，0 failures/errors/skips；不执行 liveTest。
- 原有 DeepSeekProviderContractTest：15/15。
- 新增 ToolNameCodecTest：6/6；DeepSeekToolMappingTest：10/10。
- 前端 TypeScript 检查通过，Vitest 42/42，Vite 静态资源构建通过。
- 本地 bootJar 打包通过，复用本轮已经构建的前端资源（跳过重复 frontendInstall/frontendBuild）；未执行 Docker 镜像构建或生产部署。
- 全目录碰撞、未知/未提供工具、双向往返、历史与流式编码、错误脱敏通过。
- 真实 DeepSeek 隔离工具请求未执行：没有读取或使用生产 API Key；隔离结果来自严格本地 HTTP fixture。
- Gradle 未来主版本弃用提示与 Vite >500 KiB chunk 警告仍存在，没有通过隐藏警告改变验证结果。
