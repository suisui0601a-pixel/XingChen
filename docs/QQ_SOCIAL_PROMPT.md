# QQ 底层聊天规则

QQ 模型请求在硬安全策略、当前 Persona / Simulation、可信会话状态之外，使用
`src/main/resources/prompts/qq-social-protocol.txt` 提供共用聊天规则。
不替换管理端当前人格、模拟版本，不修改数据库或唤醒策略。

内容基于 Derpyu520/qq-bridge 0.1.7 的 `qq-chat-v2`（二代仿真）预设，
可核验源码版本 `b21a8ccce9b516455ee454245b1553f0630c895b`，
文件 `dsh/agent-presets/qq-chat-v2/agent.cordis.yml`。保留群聊选择性参与、
主体性、自然短句、记忆卫生、不汇报发送过程等原则；不是完整原文恢复。
许可见 `third-party/qq-bridge/LICENSE`。

协议适配：

- 原预设规定所有回复走 MCP 工具、最终文字不发送；本 Runtime 普通最终文字自动发送。
  读工具后仍可正常回复；已有发送成功时不复述，结果未知时不重试。
- 仅使用当前 AgentToolCatalog 提供的名称、参数和能力；不引入旧会话令牌、
  MCP 名称、五分钟沉睡观察或无实现的联网、语音、看图等功能。
- 当前会话目标与类型来自后端；群聊不读取 PRIVATE 记忆，不能换 ID 绕过范围。
- 当前 Persona / Simulation 保持身份与个性配置来源，不使用 coding-agent 默认身份、
  旧角色名称或历史 assistant 身份代替它们。
- 不将示例里的偏好、生活经历、昵称、成员印象或旧私人角色卡放入公共规则。

该规则只用于有可信 QQ 会话元数据的请求，固定开销在选取历史前计入上下文预算。
安全与权限仍由服务器执行，提示词本身不构成鉴权。
