# DSH 0.1.7-rc.2 与 qq-bridge 0.1.7 协议核验

## Revision 固定

| 组件 | Revision | 固定方式 | 证据等级 |
|---|---|---|---|
| qq-bridge | `61b7e2e6905ec60489ec3985307b2251e37087bf` | GitHub commit API；提交仅修改 README，父提交 `bbe0bc807b1510b49c33cdf9f1d65291eca288e2`，其父为 release commit `07ad86abd5cfa528a59d58db1dd3d18de8293a70` | A |
| DSH | `477b4f420553e8a52c2fbccc464d7561b239c443` | 官方 tag `dsh-v0.1.7-rc.2` 解析结果 | B |

该报告不把当前 master 文档当作旧版线协议依据。下文所列 Bridge 请求来自精确 commit 的运行代码；DSH 结构由精确 RC tag 的 Host/Client 实现交叉核对。

## 已确认的协议

### HTTP unary RPC

| 项目 | 契约 | 证据 |
|---|---|---|
| 鉴权交换 | `GET /?token=<launch token>`；手动处理重定向，读取 `Set-Cookie` 的首个 `name=value`，后续通过 `Cookie` 请求头发送。错误或缺失 Cookie 不得降级匿名成功。 | A：`src/dsh-client.js` `ensureAuth()`；B：`packages/client/connection/src/browser-auth.ts` `authorizeIndex()` |
| HTTP 方法与路由 | `POST /api/<endpoint>`，如 `/api/session/create`、`/api/workspace/create`。 | A：`src/dsh-client.js` `callUnary()`；B：`packages/client/connection/src/rpc-host.ts` Fetch handler |
| 请求信封 | `{type:"client-request",rpcId,method,payload}`；`method` 必须与路由 endpoint 相同。 | A：`src/dsh-client.js` `callUnary()`；B：`rpc-host.ts` 检查 `message.method` 与 endpoint |
| 参数包装 | 大多数 Session/Workspace 方法为 `payload:{args:{request:<业务请求>}}`；`session/list` 为 `_request`；`settings/describe` 与 `agentPresets/list` 为 `args:{}`。 | A：`src/dsh-client.js` `METHOD_ARG_WRAPPERS` / `wrapArgs()` |
| 响应信封 | `{type:"server-response",rpcId,result}`；`result` 为 `{ok:true,value}` 或 `{ok:false,error:{code,message,details}}`。响应 `rpcId` 不匹配、缺失或 envelope 畸形视为协议错误；业务错误不能吞掉。 | A：`src/dsh-client.js` `callUnary()` / `unwrap()`；B：tag `dsh-v0.1.7-rc.2` 的 `packages/client/connection/src/rpc.ts` `ConnectionRpcResult` 与 `rpc-host.ts` `fullResponse()` |
| 错误语义 | Host 的失败结果带稳定 `error.code` / `error.message` / `error.details`；认证失败发生在 RPC 前，HTTP 401。 | B：`rpc-host.ts`；`browser-auth.ts` |

### Cookie/session auth

- RC2 有 launch token 到浏览器 Cookie 的交换：仅根路径 `GET /?token=...` 可接受单个有效 launch token，并返回绑定 authority 的 HttpOnly Cookie；普通 API 请求携带 Cookie。缺失、过期、错误 authority 的 Cookie 被拒绝。
- 精确 Bridge commit 还实现了离线铸造同种签名 Cookie：从 DSH home `.credentials.yaml` 读取 `client-connection/browser-session` 的 32-byte secret，以 HMAC-SHA256 签名 `{version, authority, issuedAt, expiresAt}`；Cookie 名按 authority 的 SHA-256 派生。铸造路径仅限 loopback；secret 不可用时才回退 launch-token 交换。
- Adapter 应将 Cookie/launch token 置于专用鉴权对象；不得记入日志、trace 或领域事件。401、超时、错误 envelope 必须区分；mux 断线本身不是签名 Cookie 无效的证据。

证据：A `src/dsh-client.js` `readBrowserSessionSecret()`, `mintBrowserSessionCookie()`, `ensureAuth()`, `_doFetchWithAuth()`；B `packages/client/connection/src/browser-auth.ts` `initializeSecret()`, `authorizeIndex()`, `isAuthenticated()`。

### Workspace 与 Session 操作

| 行为 | endpoint / 参数 | 响应与语义 | 证据等级 |
|---|---|---|---|
| create/find workspace | `workspace/create`, `args.request={path}` | `path` 指向已存在目录。Host 按规范化 path 解析已有 workspace；已有则返回 `created:false`，新建返回 `created:true` 和 workspace view。结果为 `{workspace:{id,...},created:boolean}`。 | A Bridge `src/bridge.js` workspace 初始化；B tag `dsh-v0.1.7-rc.2` `packages/api/workspace-controller/src/commands.ts` 的 `create()` 与 `types.ts` 的 `WorkspaceCreateValue` |
| rename workspace | `workspace/rename`, `args.request={workspaceId,title}` | 返回更新后的 workspace view；空 title / 冲突为显式错误。 | A Bridge；B workspace `commands.ts` |
| create session | `session/create`, `args.request={workspaceId,agentPreset?}`；`cwd` 与 `workspaceId` 不能同时提供。 | 返回 `{sessionId,agentPreset?}`。预设通过创建请求选择；Bridge 在模式/权限策略变化时安全创建新会话，不假定存在独立 `selectPreset` RPC。 | A Bridge `src/bridge.js`；B tag `dsh-v0.1.7-rc.2` `packages/api/session-controller/src/commands.ts` 与 `types.ts` `SessionCreateRequest` / `SessionCreateValue` |
| prompt | `session/prompt`, `args.request={requestId,sessionId,mode:"queue"|"steer",content:[{type:"text",text}]}` | 成功 `{accepted:true}`。`requestId` 必填且由客户端生成，用于幂等/事件关联。 | A `src/dsh-client.js` `wrapArgs()` + `src/bridge.js` prompt；B `session-controller/src/types.ts`、`commands.ts` |
| select model | `session/selectModel`, `args.request={sessionId,provider,model,...}` | 返回已选择的 model selection；模型不可用是业务错误。 | A Bridge `ensureChatModel()`；B `session-controller/src/commands.ts` |
| stop | `session/control` 读取当前 queue baseline；对 queue 项逐一 `session/updateQueue` remove；最后 `session/cancel`。仅 cancel 会保留 inbox，不等同于清空排队消息。 | 不应把 cancel 成功误报成队列已清空。所有 remove/cancel 都限定目标 session。 | A `src/dsh-client.js` `stopSessionWork()`；B `session-controller/src/commands.ts` `cancel()` |
| archive | `workspace/archiveSession`, `args.request={sessionId}` | 归档会话是隐藏/退役操作，不替代 stop/cancel。 | A Bridge reset/session-retire；B workspace `commands.ts` |

### Remote mux、follow 与事件

| 项目 | 契约 | 证据 |
|---|---|---|
| WebSocket 路由 | `ws(s)://<authority>/api/remote.mux`，请求使用已认证 Cookie。 | A `src/dsh-client.js` `_remoteMuxGenerator()`；B `packages/api/gateway/src/stream-protocol.ts` |
| open 帧 | `{type:"open",streamId,endpoint,payload}`。 | B `stream-protocol.ts` `RemoteStreamClientMessage` |
| follow | `endpoint:"session/follow"`, `payload:{args:{request:{address:{kind:"session",sessionId},maxMessages,assistantStream?}}}`。每个 streamId 映射唯一 sessionId；未知/错配 stream 不得投递给别的会话。 | A `src/dsh-client.js` `sendOpen()`；B `session-controller/src/types.ts` `SessionFollowRequest` |
| mux server frame | `{type:"item",streamId,value}`、`{type:"end",streamId}` 或 `{type:"error",streamId,error:{code,message,details}}`。Client 还定义 `item`、`end`、`cancel` uplink 帧。 | B `stream-protocol.ts` |
| session follow item | `value.type:"snapshot"` 是历史基线；`value.type:"event"` 是后续持久事件；另有可选 `assistant-stream` 展示帧。快照不能伪装成实时 event，否则旧 `turn/end` 会被重放成新 QQ 回复。 | A `src/dsh-client.js` `handleMessage()`；B `session-controller/src/types.ts` `SessionFollowFrame` |
| forwarded events | 单独 open `$events`，`payload:{args:{}}`。首 item `ready` 提供 `clientId`；waterfall `approval/request` 和 `user-questions/request` 有 `eventId`、`agentId`、request。 | A `src/dsh-client.js` `sendOpenEvents()` / event demux；B `stream-protocol.ts` |
| event result | `POST /api/$events/result`，`args:{clientId,eventId,outcome}`；outcome 为 `next`、`result` 或 `rejected`。 | A `src/dsh-client.js` `respond()`；B `stream-protocol.ts` `RemoteEventResult` |
| 关闭/重连 | Stream 正常结束与错误是不同终态；Socket 断线关闭该 generation 的所有流。Bridge 将期望 follow 集合跨重连保留并重新打开；未知 malformed frame 不被转成业务事件。 | A `_remoteMuxGenerator()`；B `stream-server.ts` / `stream-protocol.ts` |

### `$events` 交互流（本轮核验）

证据限定：B 来自 DSH 固定 tag Host 源码。A 来自本任务提供的精确 pinned Bridge 源码核验记录（`src/dsh-client.js`、`src/bridge.js`）；本轮没有再次读取本机缺失的 Git blob。Host 对 `outcome` 只执行无损 JSON 校验，并将 `result.value` 原样交给上层 waterfall；以下字段与通用 Host JSON schema 没有冲突。业务含义由 Bridge 定义而非 Host 定义。

| 子项 | RC.2 契约 | A 客户端证据 | B Host 证据 |
|---|---|---|---|
| Open | 与 `session/follow` 共用 `/api/remote.mux`，但使用独立 `streamId`；`endpoint:"$events"`、`payload:{args:{}}`。不应把该 stream 的 item 分派到 session/follow。 | 用户提供的 pinned Bridge 审计说明：commit `61b7e2e6905ec60489ec3985307b2251e37087bf`，`src/dsh-client.js`；本轮未独立复读 blob。 | tag `dsh-v0.1.7-rc.2`，`packages/api/gateway/src/stream-protocol.ts` 的 endpoint/payload 常量；`packages/api/gateway/src/index.ts` `openWireStream()` / `openRemoteEvents()`。 |
| Ready | Host 第一个 item 是 `{type:"ready",clientId,host:{home}}`；`clientId` 对应一次活动 client generation。必须先收到 ready 才接受 waterfall。 | 用户提供的 pinned Bridge 审计说明；本轮未独立复读 blob。 | `stream-protocol.ts` `RemoteEventReadyFrame`；`index.ts` `openRemoteEvents()` 创建、登记 client 并先 yield ready。 |
| Waterfall | mux item 的 `value` 为 `{type:"waterfall",event,eventId,agentId,request}`。ID 都是不透明、非空字符串；`request` 必须是无损 JSON object。 | 用户提供的 pinned Bridge 审计说明；本轮未独立复读 blob。 | `stream-protocol.ts` `RemoteEventInvocationFrame`；`index.ts` `startRemoteEvent()` 投递 eventId、agentId 和投影 request。Host 删除 `agent` 与 `signal`，不负责解释业务 request 字段。 |
| Agent/session | 对此 tag，Host Agent lookup 将 wire `agentId` 声明为 `SessionId`，且 Agent 注册时强制 `agent.id == agent.session.id`；因此可安全以该值查 XingChen 当前 session 映射，不能从 question/request 内猜 session。 | Bridge pinned event handler 使用 agentId 关联当前 session。 | tag `packages/core/agent/src/index.ts` Agent lookup/context lookup 注册处及 `enter()` 一致性检查；`packages/api/gateway/src/index.ts` 要求非空 agentId。 |
| Approval request | event 为 `approval/request`；当前 parser 只读取 `toolName`、可选 `callId`、可选 `reason`。不含 DSH Agent 或 AbortSignal（Host 投影时已剔除）。 | 用户提供的 pinned Bridge 审计说明；本轮未独立复读 blob。 | Host B 仅验证对象可无损 JSON；这些业务字段不由 Host wire parser 校验。其 Agent/AbortSignal 剔除和通用投影见 `stream-protocol.ts` `projectRemoteEventRequest()`。 |
| User question | event 为 `user-questions/request`；request 中保留 `questions` 数组。当前 parser 要求每个问题有 `id` 与 `question`，将选项投影为 label。 | 用户提供的 pinned Bridge 审计说明；本轮未独立复读 blob。 | Host B 将该 event 当通用 projected request JSON 透传；不校验 question schema。 |
| Result endpoint | `POST /api/$events/result`，HTTP body 为 Connection RPC envelope；`payload:{args:{clientId,eventId,outcome}}`，这里 `args` 直接装结果字段，**不是** `args:{request:...}`。成功 RPC 允许 void result 没有 `value`。 | 结果 endpoint/wrapping 由 Host B 本轮直接核实；Bridge pinned `respond()` / unary call 的 A blob 未能本轮重读。 | `stream-protocol.ts` `REMOTE_EVENT_RESULT_ENDPOINT` 与 `RemoteEventResult`；`gateway/index.ts` `dispatchRpc()` / `parseRemoteEventResultPayload()`；`client/connection/src/rpc-host.ts` 构造 `server-response`。 |
| Outcome | Wire outcome 联合类型为 `{kind:"next"}`、`{kind:"result",value?}`、`{kind:"rejected",error:{name,message,code?,details?}}`。Host 仅检查 lossless JSON，并将 `result.value` 交给上层 waterfall；Host 不判断某种业务字符串是否是“批准”。 | Bridge pinned `respond()` 的具体 answer mapping；调用方必须保持 DSH event 返回值语义。 | `stream-protocol.ts` `parseRemoteEventResult()` 精确验证这三个分支；`index.ts` `receiveRemoteEventResult()` 将 result value 交回 pending waterfall、rejected 则 reject、next 由最后一个 client 触发 waterfall continuation。 |

#### Pinned Bridge 的业务结果值

以下值来自 `Derpyu520/qq-bridge@61b7e2e6905ec60489ec3985307b2251e37087bf` 的 `src/dsh-client.js` 与 `src/bridge.js`，按用户提供的精确源码核验记录整理。RC.2 Host 将 `result.value` 当作通用 JSON 交回 handler，不会解释其业务含义。

| 决策 | `outcome` |
|---|---|
| Approval 允许一次 | `{kind:"result",value:"allowed-once"}` |
| Approval 拒绝或挂起 Approval 被取消/覆盖 | `{kind:"result",value:"rejected"}` |
| Question 选择选项 | `{kind:"result",value:{answers:[{id,selected:[matchedLabel]}]}}` |
| Question 自由文本 | `{kind:"result",value:{answers:[{id,selected:[],custom:answerText}]}}` |
| Question 取消 | `{kind:"result",value:{answers:[]}}` |

这些业务值与 Host 通用 JSON schema 相容。不得将用户原文直接发送为 result；应用必须先完成身份、会话、事件、状态、expiry 和 generation 校验。
| Replay / stale generation | socket 断开移除该 client 的 delivery；仍 pending 的 waterfall 在新 `$events` stream ready 建立后重投，沿用同一 `eventId`，但新 stream 获得新 `clientId`。旧 generation 不得答复；pending domain row 必须按 eventId 更新 generation，不可把人类的授权状态重置。 | Bridge pinned 重连与 eventId 去重路径。 | `gateway/index.ts` `openRemoteEvents()` 在 ready 前重发 pending；`removeRemoteEventClient()` 退订旧 generation；result 只接受当前 client delivery，已完成/被取代 delivery 是幂等 no-op。 |

证据来源是固定 RC tag 的 Host 源码，不使用 master 替代。Host 对 approval/question 的业务结果值不做 schema 解释；因此 XingChen 只有在 pinned Bridge 的明确 event answer mapping 与 RC2 对应业务类型有双边证据后，才能构造 `outcome.kind="result"` 的 `value`。不得将 `APPROVE`、`YES`、`TRUE` 等自行发明的值发送给 DSH。

## 现有 Bridge 行为边界

- Bridge 0.1.7 的 `/reset`、`/new` 是 Bridge 侧管理命令，不是 DSH slash RPC；它会 retire 当前映射、停止排队工作、归档旧 Session，再下次创建新 Session。
- Agent preset 在 Session create 时提交为 `agentPreset`；模式或权限策略变更需要新 Session。源码中没有已证实的 `session/selectPreset`。
- `settings/describe`、`agentPresets/list`、`session.modelCatalog` 是额外调用；要纳入 Adapter 时须按同一 RPC envelope 继续取证和覆盖测试。本报告不把它们列为已实现的 DshGateway 方法。

## XingChen Phase 4A-2 Adapter 覆盖

- 已实现并由协议级 loopback HTTP/WebSocket fake 覆盖：launch-token/Cookie 交换、401 重鉴权一次、unauthorized、RPC 相关 ID、业务错误码、畸形响应、限时与取消、workspace/create（要求路径已存在）、session/create、prompt、selectModel、queue baseline/remove/cancel、archive、session/follow、snapshot/session 绑定、mux stream 绑定、durable event 解码、assistant-stream 摘要、end/disconnect/malformed 终态及并发 follow。
- 所有鉴权错误对外保持固定脱敏消息；Cookie、launch token 不进入异常正文、AgentTrace 或领域事件。
- 预设仅在 `session/create` 指定；RC2 没有独立 `selectPreset`，该操作明确 unsupported。
- follow 对 snapshot 与 durable event 分型，snapshot 不应当作新消息回放。`completion()` 将正常 end 与断线/协议错误分开呈现。
- 已实现并在本地端到端验证：RC.2 独立 `$events` stream 的 ready/waterfall 解码、approval/question DTO 投影、独立 stream id、`$events/result` HTTP RPC 直接 args payload、持久化 PendingInteraction、OneBot 可读提示、正规 EventNormalizer/IdentityResolver 答复解析、actor/conversation/expiry/generation 校验、多问题逐项答案、approval allow/reject outcome、事务式 result ledger，以及 replay/restart rebind。
- `SqliteStartupContractTest` 覆盖从 Fake DSH 经 `$events`、SQLite、Mock OneBot 提示，再从 OneBot 规范化输入回到 `$events/result` 的完整链路；`DshPendingInteractionRestartTest` 使用两个独立 Spring ApplicationContext 与同一 SQLite 文件验证恢复、原 expiry 保留、pending replay 重绑定、答复继续、owner 限制和不重复提示。
- Delivery certainty 由 XingChen 自己掌握的 dispatch 边界决定，而不从 Fake DSH 是否收到 headers/body 反推。`DshHttpDispatcher.beforeDispatch` 在 `dispatch` 前运行；生产 dispatch 调用 `HttpClient.sendAsync`。该边界前的 timeout 为 PRE_DISPATCH，确认未调用 send，ledger 写 FAILED 与 `PRE_DISPATCH_TIMEOUT`；边界后任何无确认 timeout/reset/response loss 均为 UNKNOWN，不重发。
- 确定性测试 `DSHRES-001` 在 pre-dispatch barrier 超时，断言 dispatcher count=0、Fake `$events/result` count=0、ledger=FAILED 且不是 UNKNOWN。对照 `DSHRES-002` 在 dispatch 已调用且服务端已应用后丢失确认，断言 dispatch count=1、ledger=UNKNOWN、同 key 不再 POST。服务端读到 headers 但尚未确认 body 的情况属于 DISPATCH_STARTED，故只可判 UNKNOWN。
- Fake transport 同时覆盖成功、明确拒绝、dispatch 后读请求前/后断开及 server commit 后响应超时。Pre-dispatch timeout 不由 Fake TCP 时序模拟，而由 dispatch seam 在调用 Java HTTP client 前确定性触发。
- Bridge 的 approval/question 业务 outcome 值有 pinned Bridge 证据，Host 的通用 JSON schema 与之兼容；这不构成真实 DSH 对业务语义的在线验收。
- 还未实现：Bridge 所需 `settings/describe`、`agentPresets/list`、`session.modelCatalog`；本地 event coordinator 有 reconnect/replay 测试，但没有真实 DSH 服务的在线验收。
- transport 的 URI/证书边界使用 JDK HTTP client 默认 TLS 验证；此轮不连接远端或生产 DSH。

## 证据可信度与剩余边界

- 本文协议证据：A（Bridge 精确 commit 实际调用）或 B（DSH 精确 RC tag Host/Client 源码）。
- Release README、当前 master 文档仅可辅助解释，未用于猜测 wire shape。
- 无需 SSH、生产 DSH、真实 QQ、SnowLuma、DeepSeek API 或 Zetu 连接即可验证此契约。
