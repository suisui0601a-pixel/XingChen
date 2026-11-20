# QQ 图片按需识别

普通纯图片（包含仅 @ 加图）、以及明确“只保存/只记录”的图片消息：通过 Access 与入站去重后持久化消息、图片标识和轻量占位说明，不下载图片，不调用模型，不消耗图像 token。WAIT 状态不会因这类纯记录事件被消耗。图文里的正常聊天继续遵守既有唤醒规则；没有看图要求时不附加图像。

用户明确要求识图后，在已获准的 Runtime 回合中读取当前图、引用图或本会话最近有效记录。仅查当前 conversation、当前事件之前、未被上下文 reset 隔离的最多40条记录；引用找不到时不替换为别的图。重启后记录仍可用于查询，原图没有在 moss 持久化，QQ 地址过期时需要重发。

OneBot 使用独立 HTTP 凭据调用 get_image。只获取 URL，不读取网关返回的服务器文件路径。QQ CDN 地址升级为 HTTPS，限制主机后缀、验证全部 DNS 结果并将 TLS 连接绑定到通过检查的 IP；启用 SNI 与证书主机校验，拒绝私网、重定向、超长响应及不明确的 HTTP framing。

每次最多2张，每张4 MiB、合计6 MiB；单边最多8192、总像素最多1600万；每张下载总预算12秒。内容检查支持PNG/JPEG/GIF/WebP，解析元数据使用内存缓存。图片仅留当前回合内存，不写入数据库、历史、日志、提示词配置或文件。

DeepSeek Chat Completions 的 USER content 使用 text + image_url（内联Base64，detail=low）块。图片不放入 assistant 历史。官方 api.deepseek.com 的旧 deepseek-v4-flash / deepseek-chat 文本配置遇到本轮图像输入时使用当前图像模型 deepseek-flash；不修改保存的文本模型或API Key，自定义 Provider/模型不自动改名。用量记录保留实际模型。仅图像请求保留每图1024 tokens预算，不用于校准纯文本估算。

参考：[DeepSeek 官方图像理解](https://api-docs.deepseek.com/zh-cn/guides/vision/)。不将“收到图片标识”或“模型给了回复”当作图像理解成功；测试检查真实图像块、私聊/群聊隔离、零调用记录路径和基于图像的回答。

不新增数据库 migration，不改 Persona/Simulation、Access/Owner 或 UNKNOWN 出站规则。不创建第二个 OneBot 消费者，不修改 SnowLuma 登录态或其他项目。
