# Phase 4B-5 — FINAL CLOSURE

## 2026-10-04 最终验收：Phase 4B COMPLETE

本节是当前结论；下方 checkpoint、NOT COMPLETE、失败与旧计数均保留为历史，不是当前状态。所有原有 closure gates 已通过，未开始 Phase 4C，未进行生产部署。最终提交为 `feat: complete admin console`，其完整 hash 以 Git 日志为准，避免在提交内容中循环引用自身 hash。

### 提交与失败记录

- 午夜修复保留并随 hardening 提交：`ddbc52b283539a8e55526267f687500a471515a6`，`fix: harden admin console final audit`。原始 dirty tree 混有其他修正，没有强行拆成错误的独立午夜提交。
- 浏览器闭环修正：`b2860a17bbc6ae686fcb4b80e1f2a830f616ab30`，`fix: close final browser audit regressions`。
- 此前完整浏览器尝试分别为 13/17、15/17，不计作正式通过轮次。问题包括旧 partial selector 命中新关联字段、未登记明确 unsupported reconnect 501、合法俚语表格内部滚动被误判，以及真实 Chrome console.error：HTML pattern 的未转义连字符不符合 `v` 模式。最后一项通过原生 pattern 转义修正源码，不加入忽略名单。随后完整预检 17/17，再从头执行以下正式双轮。

### 正式双轮与最终 smoke

| 验收 | 命令 / 环境 | 结果 |
| --- | --- | --- |
| Round 1 Gradle | `clean test bootJar frontendTypecheck frontendTest --offline` | PASS，3m57s；44 backend suites，331 tests，failures/errors/skipped=0；frontend 38/38、10 files；typecheck、bootJar PASS |
| Round 1 Browser | 新隔离 fake DB/secret/assets/state；完整 Playwright，workers=1、retries=0 | 17/17，2.8m；非预期 console.error/pageerror/unhandledrejection/request failure=0 |
| Round 1 清理 | runner finally + fixture assets 清理 | 3200 released；临时 DB/secret/assets/logs/state 不残留 |
| Round 2 Gradle | `test bootJar frontendTypecheck frontendTest --rerun-tasks --offline` | PASS，5m16s；10 actionable tasks 全执行；backend 331/331、44 suites，failures/errors/skipped=0；frontend 38/38；typecheck、bootJar PASS |
| Round 2 Browser | 再建全新隔离 fake 环境；完整 Playwright，workers=1、retries=0 | 17/17，2.9m；非预期 console.error/pageerror/unhandledrejection/request failure=0 |
| 最终最新 jar smoke | `FinalSmoke.e2e.spec.ts`，第三个新 fake 环境 | 1/1，retries=0；Login+19 protected routes=20 页，页面加载/标题/刷新正常；health200、匿名401、CSRF403、unknown404、conflict409、fake500，错误为 safe JSON、无 HTML/异常/私有正文 |
| 最终清理 | 停隔离 JVM、清临时目录与3个测试 Sticker fixture目录、`gradlew --stop` | 3200 released；不留测试运行态；保留构建报告与 trace 证据；diff --check PASS |

最终 smoke 的 jar 来自第二轮，代码基线为 `b2860a1`；最终封版提交只更新验收文档，不改变已双轮验证的实现。普通测试不运行付费 DeepSeekLiveTest，不声称真实外部集成通过。

### 全部 gate 的结论与边界

| 类别 | 最终结果 |
| --- | --- |
| Navigation | /agent、/groups route/nav/服务端转发已移除；没有可点击死占位页。20 页：Login、Overview、Gateway、Conversations、People、Relationships、Memory、Persona、Simulation、Social Settings、Models、Stickers、Slang、Voice、Usage、Access、Security、Logs、Operations、Settings。真实 unsupported 能力保持明确说明。 |
| i18n | zh-CN/en-US 主字典176/176，实际用户文案与 aria 已审计，四个原硬编码页面已本地化，raw key/意外 fallback=0；剩余品牌、语言自称、技术 ID/协议/格式/UTC 与不可改写的 HardSecurityPolicy 原文不冒充待翻译产品文案。 |
| Errors | Operations error/retry、Logs ERROR≠EMPTY、Access stale preview 清理/响应序号保护闭环；Model probe/其他预览不保留冒充新结果的旧成功。CSRF discovery 统一 helper 去重/timeout/cancel/cleanup/失效，不盲目 replay mutation；401与403区分。400/401/403/404/409/429/500 safe code/message/traceId 合约通过。 |
| Accessibility | Login/Memory/Relationship/Prompt/Social/Model/Pricing/Access/Password 核心错误关联字段。自定义 Prompt/Reset/Logout dialog 有名称/描述、初始焦点、Tab/反向Tab、Esc、取消/确认/回焦；原生 confirm 的危险操作保留浏览器语义，不声称自制 modal。纯键盘核心全流程通过，四主题 focus 可见；状态有文字、Usage 图有表格替代、Sticker 有 alt。 |
| Responsive | 所有20页分别375/430/1280/1440通过；无 body 失控溢出。宽表格仅允许在边界受约束的自身 scroll 容器内滚动，不按所有 offscreen 表格子元素误判失败。 |
| Themes | Light/Dark/晚宁/System(light+dark) 六关键页与 reduced-motion 通过，焦点/状态/差异文本可辨识，截图已复核。颜色 ratio>=3 是此次可辨识检查，不声称完整 WCAG 认证。 |
| Security | auth、session invalidation、password rotation、CSRF、SecretStore CLEAR、Access fail-closed、Memory isolation、HardSecurityPolicy precedence、Provider SSRF、Sticker traversal、普通 profile 无 fake 服务、UNKNOWN 不重试均有自动化证据。storage仅theme/language；输入 HTML/script/img-onerror 在编辑/历史/diff作为文本，未执行。 |
| Headers | nosniff、DENY frame、no-referrer；Cookie HttpOnly、SameSite Strict、Secure 随有效 HTTPS 配置。CSP deferred：部署资源/连接/动态样式策略未充分验证，不在 closure 临时引入破坏性策略，不据此授权裸露公网。 |
| Bundle | 137 modules；JS543.42kB/gzip164.46kB，CSS28.37kB/gzip6.38kB。生产依赖无嵌套重复/第二份React或明显不当重型库；接受现有500kB warning，阈值未改，无高风险 lazy refactor；既有Java/Gradle弃用提示记录但不扩展依赖升级。 |
| Matrix | 123 rows=123 unique IDs；74 TESTED+31 FOUNDATION+18 PLANNED+0 NEEDS_REVIEW。105a/109a/110a消除冲突而保留历史ID；stale描述已修正。59 qualified method、21 inherited、12 class-suite、84 index-method引用存在；33 range/alias按真实case映射且审阅行为边界，不把引用存在当测试充分。49非TESTED逐项scope审查，无隐藏大型4B blocker；FOUNDATION/PLANNED不虚假升格。 |
| Docs/隔离 | PHASE4B_REPORT保存验收记录；CONSOLE_ARCHITECTURE仅稳定设计，Matrix及只读audit工具保存证据。无SSH、无Docker/迁移、无真实QQ/SnowLuma/DSH/DeepSeek连接、无生产secret访问、Zetu未修改。 |

### Browser 预期错误的窄名单

仅实际 HTTP response 的精确 path/status 配对可豁免资源错误：`/api/auth/session` 401（匿名 session 探测）、`/api/auth/login` 401（错密码测试）、`/api/prompts/PERSONA/versions` 409（显式乐观冲突）、`/api/social-settings/conversations/<UUID>` 409（显式乐观冲突）。逐测试登记 `/api/gateway/reconnect` 501（真实 unsupported）、`/api/operations/status`、`/api/logs/operational`、`/api/access/preview` 的注入500，以及 `/api/prompts/PERSONA/versions` 注入403。仅明确导航/卸载清理的 GET ERR_ABORTED 可视为取消；不忽略整类 status、所有 console.error 或任意 JS 错误。负向 API smoke 使用 request 客户端核验响应，不发生浏览器资源错误。最终两轮所有其他异常均为0。

## 2026-10-04 FINAL CLOSURE hardening checkpoint

延续 `b034b4db4f1bfef6f934bbc0e5e533911ac32f09` 的预期 dirty tree，保留已验证的午夜相对窗口/UTC/[from,to) 修复，没有 reset/restore/stash/clean 工作树。下方 NOT COMPLETE 与失败记录属于历史尝试；最终双轮尚待本检查点后执行。

| 检查点 | 当前证据 / 有意保留的边界 |
| --- | --- |
| Navigation | 删除 /agent、/groups 死入口和服务端转发；Login +19 个受保护顶层页，已有能力归属实际页面，不新增 Agent/Groups 产品。Voice/QR/Reasoning 的真实 unsupported 状态保留。 |
| i18n | 主字典176/176，无缺失/闲置候选；Access/Usage/Logs/Operations 实际文案、列名、状态与 aria 已本地化；Prompt composition/Voice 后端英文说明不直出。剩余词法候选为语言自称、品牌、版本、UTC/时间范围、格式/hash；枚举/ID/只读 HardSecurityPolicy 原文属于技术数据。 |
| 失败状态 | Operations catch/retry；Logs ERROR≠EMPTY；Access/Relationship/Memory preview 清旧结果并挡旧响应；Model probe 清旧成功；Usage 请求序号 fencing。字段错误关联 aria-invalid/describedby，保留原生 required/pattern/range。 |
| 请求 | 复用现有 api helper；CSRF discovery 去重、12秒 timeout、取消、timer/listener cleanup、401/403/logout 失效。写操作不自动 replay，不引入新 HTTP 框架。登录403不冒充密码错误。 |
| Dialog/键盘 | Prompt/Reset/Logout name/description、初始取消焦点、Tab/反向Tab、Esc、回焦；关系/Memory/Access 等继续原生 confirm。定向核心纯键盘登录→导航→编辑→保存→确认取消/执行→退出通过。 |
| Browser | Node 侧跨导航持续收集 pageerror/console.error/unhandledrejection/请求失败。只允许精确 auth/session401匿名探测、auth/login401错密码、Persona versions409及 UUID social-settings409冲突；失败注入逐例指定精确路径+状态并匹配实际response。GET 导航/卸载 ERR_ABORTED 是明确取消，不按整类status豁免。 |
| 安全 | 400/401/403/404/409/429/500 固定 code/message/traceId，不回显异常/reason；fake500仅隔离profile。普通profile fake controller/provider beans缺失、mutation404。保留auth/session/rotation、CLEAR、Memory隔离、HardPolicy、SSRF、traversal、UNKNOWN no-retry契约。 |
| Storage/XSS/Headers | browser storage仅theme/language，无HTML/Markdown执行sink；script/img-onerror在editor/history/diff作为文本，未执行。nosniff、DENY、no-referrer、HttpOnly/Strict/effective Secure。CSP明确deferred：动态样式/资源连接策略尚未完成部署审计，不在closure冒险破坏管理功能。 |
| Responsive/Themes | 20页四档375/430/1280/1440逐一遍历；无body/控件横向失控，table内部滚动允许。六关键页覆盖Light/Dark/晚宁/System双scheme、reduced-motion、focus；文字/muted/focus/error/warning/selected颜色ratio>=3的可辨识审计，不冒充完整WCAG认证；截图人工复核。Chart表格替代、Sticker alt、状态文字保留。 |
| Matrix | 123唯一ID：105a/109a/110a消除插入行冲突，历史ID不重排；74TESTED+31FOUNDATION+18PLANNED。59qualified、21inherited、12class-suite、84index-method引用无缺失；33alias/range复核实际case/index。行为范围与49非TESTED行逐条处置见Matrix，未发现隐藏大型4B功能blocker。 |
| Bundle | 137构建modules；React/ReactDOM、Router、RHF、Zod resolver/Zod与自有页面。lockfile13个非dev/optional生产包，嵌套重复0，主要依赖deduped；resolver未使用optional schema peers不属于缺失运行依赖。无重型图表/Markdown/编辑器、无第二份React。接受500kB警告，不调阈值或冒险拆包。检查点JS543.42kB/gzip164.46，CSS28.37/gzip6.38。 |

开发迭代失败如实记录：旧语言selector/mock Response复用、MockMvc csrf()污染真实cookie repository已修正；新浏览器检查发现favicon404、Prompt反向Tab、精确错误登记问题。扩展XSS/颜色测试又修正三位hex、合法空默认Prompt、异步history刷新和空文案禁用保存的错误测试前提；原文恢复使用原生rollback。这些失败不计作最终通过轮次。

定向后端16/16；完整前端37/37后补timeout用例，api定向7/7；新浏览器审计+FinalSmoke7/7，workers1/retries0。随后必须重新跑完整双轮，不以定向替代。所有运行均offline本地fake；无SSH、真实QQ/SnowLuma/DSH/DeepSeek、生产secret或Zetu修改；不开始Phase4C。

Date: 2026-10-03–04. Configuration-closure baseline: `e4a3c01a12721bb94a301f6d0cda024203723d8a`; cost-closure baseline: `762ffe340f9cc0a8149250b9c76c99d0191c41d6`.

**NOT COMPLETE / NOT SEALED.** Closing the configuration-source and cost-accounting defects does not establish Admin Console final product acceptance. No Phase 4C or production deployment is performed.

## Configuration source audit and closure

| Value | Bootstrap / default | Persistent state | Effective runtime consumer |
| --- | --- | --- | --- |
| Console public URL | application.yml / `XINGCHEN_PUBLIC_BASE_URL` | `console_configuration` Console/publicBaseUrl; an empty row is explicit absence | `EffectiveConsoleConfiguration` startup snapshot, servlet Cookie and Security Center |
| Gateway credential | primary `XINGCHEN_ONEBOT_ACCESS_TOKEN`, then `ONEBOT_ACCESS_TOKEN`, only while UNINITIALIZED | shared owner-restricted `FileSecretStore`; legacy Gateway file migrated before bootstrap | Gateway facade, ConfigService, actual OneBot startup/reconfigure credential snapshots and Security Center |
| DeepSeek credential | primary `XINGCHEN_DEEPSEEK_API_KEY`, then `DEEPSEEK_API_KEY`, only while UNINITIALIZED | existing shared provider secret file / Clear tombstone | model per-turn snapshot and Security Center |
| DSH launch credential | `DSH_LAUNCH_TOKEN`, only while UNINITIALIZED | shared `dsh-launch-token` secret | production DSH configuration and Security Center; historical `DSH_API_KEY` presence is not actual launch-auth status |

Other environment reads remain deployment defaults or standalone adapter constructors, not a fallback after an explicit persisted secret Clear. Production OneBot construction/reconfiguration now passes only its effective credential map, not a copy of all environment secrets. There is no new configuration framework, schema expansion, Vault, PropertySource rewrite, UI feature or secret echo.

Public URL saves remain RESTART_REQUIRED. Before restart the existing cookie/posture snapshot stays effective; after restart the saved value wins. Saving empty means explicitly no public URL, not inheritance. Secret Replace/Clear use the existing live reconfigure path; a new connection may still be rejected by the remote server. HOT_APPLY means a fresh effective credential snapshot, not guaranteed remote connectivity.

FileSecretStore states are represented by absent files (UNINITIALIZED), `.secret` (CONFIGURED), `.cleared` (CLEARED). Clear installs the owner-restricted atomic tombstone **before** deleting a secret, preventing an interrupted deletion from reviving a credential on restart. Reads give Clear precedence. Replace installs the new secret then retires Clear. Canonical files without metadata remain configured; legacy Gateway filenames migrate without environment overwrite. Unsupported secure filesystem operations fail closed. Bootstrap does not fabricate administrator audit actions; Console operations retain value-free action metadata.

## Local test evidence

`EffectiveConfigurationRestartTest` runs real Spring Boot contexts over the same isolated database/store, closes and restarts them, checks actual servlet Cookie settings and authenticated Security Center responses, uses Console mutations and real OneBot HTTP requests to a local fake, and checks old bootstrap/new/cleared/restarted/replaced credentials. It covers CONFIG-BOOT-001..004, SECRET-BOOT-001..005 and SEC-CRED-001..003. Provider credentials are configured through the protected Console API. All credentials in these tests are synthetic; no real QQ/DSH/DeepSeek connections are made.

The first targeted attempt had three JUnit temporary-directory cleanup failures (`AccessDeniedException` under Windows AppData). These failures are not erased or counted as passes. Moving the test TempDir factory to the disposable project build directory fixed cleanup without weakening owner-only ACLs. The failed test directories containing synthetic credentials were removed. Subsequent targeted tests passed.

The OneBot timeout audit found a real check-before-condition-lock lost-wakeup window. The unread check now occurs under the condition lock. `ob111` uses a dedicated thread and actual TIMED_WAITING readiness, preserving the two-second outer response bound. `ob114` deterministically delivers and signals an event before the waiter obtains the lock; it must return without waiting for the internal timeout. Test readiness failures now fail rather than silently falling through. Browser reset tests explicitly wait for the reset HTTP acknowledgement, without blanket sleep or retry. Browser fixtures collect unexpected pageerror/console.error and unhandled promise rejections; only documented negative-test HTTP resource notices are exempt.

After the fixes, two independent consecutive full validations passed:

| Validation | Result |
| --- | --- |
| Targeted config/restart/security/model/Gateway tests | Passed; new restart class 3/3, all targeted selections green |
| `clean test bootJar frontendTypecheck frontendTest --offline` | Passed; backend 311/311, frontend 17/17 across 7 files, typecheck and jar build green |
| `test bootJar frontendTypecheck frontendTest --rerun-tasks --offline` | Passed with tasks actually executed; backend 311/311, frontend 17/17, typecheck and jar build green |
| Playwright round 1 (`--workers=1 --retries=0`) | 10/10 passed, new isolated app/data, no retry |
| Playwright round 2 (`--workers=1 --retries=0`) | 10/10 passed, new isolated app/data, no retry |
| Browser diagnostics on both rounds | No unexpected pageerror/console.error or captured unhandled rejection; documented negative HTTP resource notices only are exempt |
| Latest jar route smoke on both rounds | /health 200; anonymous posture 401; authenticated unknown API 404/non-HTML; 19 Console routes load and refresh, login works, session HttpOnly and browser storage limited to theme/language |

The three configuration-source blockers are CLOSED by this local evidence, including compatibility with existing environment/defaults and secret files, persistent Clear, actual runtime transport/Cookie consumers and redacted posture. No real provider/QQ integration is claimed. A passing existing suite is regression evidence, not evidence for every final product-audit requirement. The initial failed attempts remain recorded below.

The first forced full rerun also exposed `reset006_activePromptHistorySurvivesAndRollbackBuildsV2Context` teardown racing the final social turn: model-entry count is not durable turn completion, and a writer recreated SQLite during directory cleanup. The fixture now waits for the exact post-reset event's COMPLETED journal row and an idle runtime queue before closing the context. The original eight-second readiness deadline is unchanged. This failed full attempt is retained as a failure; a later targeted/full pass is not presented as if that attempt had passed. The isolated failed database was removed.

## Resumed FINAL audit — outstanding gates

- Navigation: `/agent` and `/groups` still lead to the wildcard placeholder, with a misleading "later Phase 4B" message. No missing feature was implemented merely to turn the matrix green.
- i18n: Usage/Access/Security/Operations still expose English labels, raw field names and enum/status strings in Chinese mode (for example Provider, Scope, Stable ID, Effect, Loading, Unavailable and No records).
- Error states: Operations has an uncaught failed-fetch path and can remain Loading; Logs maps failed requests to empty records. API CSRF retrieval occurs outside the cleanup try/finally and is not covered by its request AbortSignal.
- Pricing blocker BLOCKER-USAGE-COST-001 is now locally closed; see the cost closure evidence below. This does not close the unrelated navigation, error-state or full-product audit gates.
- Audit coverage is not a final seal: 430/1440 viewports, exhaustive keyboard/reduced-motion/themes/loading/error/expiry checks across all pages, final security-header evaluation and all 123 meaningful evidence references still need completed evidence. Existing E2E tests do not establish all of these requirements.
- Usage fixture counters/models/prices are deterministic; usage timestamps now anchor 1–4 hours before fixture startup instead of expiring October 2026 dates. Other historical fixtures are unchanged. No timeout increase or retry masking was used.

These are observed remaining final gates, not newly added Phase 4C features. The final `feat: complete admin console` commit and a Phase 4B COMPLETE declaration are prohibited until the original acceptance gates are genuinely met.

## Inventory and build

Programmatic row counting after closing cost/time row 83 confirms 123 rows: 69 TESTED, 30 FOUNDATION, 24 PLANNED, 0 NEEDS_REVIEW (sum 123). Before closure: 68/30/24/1. No feature row was added or unrelated status promoted. Row 83 now has meaningful local automated monetary/coverage/browser evidence. The inventory count is not proof that all TESTED references are meaningful or all Phase 4B acceptance scope is complete.

A programmatic scan of the current numeric matrix rows found 73 explicitly class-qualified method references: 72 exact methods exist; `ConversationConsoleContractTest.conv001..014` is a range shorthand, resolved to eight actual methods whose names cover cases 001..014. No missing class was found in that limited scan. Unqualified aliases and whether each test meaningfully proves every row's claim still require the full evidence audit; reference existence alone is not enough.

Observed Vite production output: JS 527.99 kB / gzip 160.06 kB; CSS 28.32 kB / gzip 6.38 kB. The >500 kB warning is retained, not hidden with a larger warning threshold or dependency upgrade. Route-based splitting may be evaluated later; no high-risk bundle refactor was made in this closure.

All work in this phase was local to its development checkout. Production services, account sessions, secrets, networking, and external gateways were outside the phase scope.

## Verified Phase 4B baseline history

- 4B-1: `e76662b36baf16720922d48e774762d3c4fd5c75` — `feat: establish admin console foundation`
- 4B-2a: `6a167ae8ff0da5c6b18faa63c0140f88256167ee` — `feat: integrate qq gateway management`
- 4B-2b: `676f0dcc15fe464a09826fcd0758f0b6ce028c92` — `feat: add conversation runtime operations console`
- 4B-3a: `974ab8b95ea77b7e23b508632f11285f0c5b9589` — `feat: add people relationship and memory console`
- 4B-3b: `5c37e42b983bd85452a4a1acac43d6010f34653a` — `feat: add persona and simulation prompt console`
- 4B-4a: `fbd23e72e7f45e534835ebbba5ac58b6afade924` — `feat: add social settings and model console`
- 4B-4b: `ef3ec1e8d85385027fcf7e629aa09d70d68a5e93` — `feat: add sticker slang and voice console`
- 4B-4c: `e4a3c01a12721bb94a301f6d0cda024203723d8a` — `feat: add usage security and operations console`

This is the verified Git history, not an assertion that every milestone covers all final acceptance conditions. The final seal commit has not been made.

## Cost-accounting blocker closure (baseline 762ffe340f9cc0a8149250b9c76c99d0191c41d6)

The audited ledger stores provider/model, timestamp, conversation/turn IDs, input/cache-hit/cache-miss/output/reasoning tokens, reasoning availability and duration. The unused ledger cost is a zero placeholder, not a price snapshot. Pricing has an exact provider/model key, one currency, current revision and updated timestamp; no effective-date matching or generic fallback exists. The former double SQL SUM ignored currency, INNER JOIN excluded unpriced usage, and the UI labelled its scalar using the first price row.

The replacement streams grouped integer usage categories and computes only BigDecimal monetary amounts. Category-presence grouping preserves per-row coverage even when the same model has rows with and without a missing category. Rate storage/audit migrates REAL to nullable decimal TEXT (V27); stored legacy values, revisions and audit IDs survive, but previously lost REAL precision cannot be recovered. API rates accept bounded decimal values (0..1,000,000, up to 18 fractional places/25 precision); a blank/null rate means missing, never zero. Currency accepts exactly three uppercase ASCII letters, no ISO database or FX lookup.

The `cost` summary provides per-currency amounts/counts/tokens, global usage/priced/unpriced counts/tokens and COMPLETE/PARTIAL/NONE coverage. Amounts are unformatted decimal strings retaining exact digits, not locale-only display strings. Only one currency with COMPLETE coverage yields a total; mixed currencies and partial pricing never do. Empty/all-unpriced usage has NONE coverage and a null amount, not fake zero. Missing any nonzero disjoint input/cache-hit/cache-miss/output/reasoning rate makes that whole row UNPRICED with a coarse reason. Explicit zero rates remain priced zero. Amounts accumulate exactly before one scale-12 HALF_UP rounding per currency; record estimates use the same display rounding. Zero/small amounts serialize as plain fixed decimal, not scientific notation. Cache/reasoning subsets are not double counted.

Historical estimates remain query-time current-rate estimates, not billing or immutable invoices. Records expose metadata/pricing status/reason only, no message/prompt/memory/hidden reasoning. Series and provider/conversation breakdowns remain token-only, with no cost chart or cross-currency cost sort. The Pricing editor remains revisioned and one currency per provider/model; decimal strings survive edit/save, null rates are explicit, and successful writes refresh summary/record pricing status. The orphan duplicate Usage implementation was replaced by an alias of the routed page, removing an incorrect scalar-cost consumer.

| Cost Accounting | Local evidence |
| --- | --- |
| Single currency complete / valid total | COST-001 |
| Mixed currencies / separate subtotals / no total | COST-002; UsageCost browser spec |
| Unpriced counts/tokens / partial / all-unpriced / empty | COST-003/004/006/014 |
| Explicit zero | COST-005 including actual HTTP decimal representation |
| Token-only breakdown/series; record currencies | COST-007/008 |
| Current-price historical revaluation | COST-009 via protected Pricing API |
| Decimal precision, stored digits, round-after-sum | COST-010 including actual HTTP tiny-amount representation |
| Nonzero missing rate / zero category missing rate | COST-011/012 |
| USD/CNY/JPY valid; lowercase/short/long/digits/empty invalid; exact provider/model | COST-013 |
| Legacy rates/revision/audit preserved on migration | DecimalPricingMigrationTest |
| Legacy `/api/usage` and `/api/usage/conversations` do not sum/expose placeholder monetary fields | COST-015 |
| Coverage UI, two languages, mixed+partial warnings, decimal display/edit/save | UsageCostSummary.test.tsx 8/8 |
| USD + CNY + unpriced -> pricing COMPLETE, still no combined total; metadata privacy; 375/430/1440 no horizontal overflow | UsageCost.e2e.spec.ts |

The targeted backend costs 14/14 plus migration 1/1 passed; frontend cost tests 8/8 passed; targeted Usage browser test 1/1 passed. The first development full regression passed 326/326 backend and 25/25 frontend, but the first full browser suite failed 1/11: AdminOps' post-save assertion still read the removed outer estimatedCost and received NaN. This is recorded as a failed attempt, not a pass. The test was corrected to compare the same USD subtotal before/after; no retries/timeouts were added. Plain decimal serialization was additionally covered by HTTP assertions. New validations below are on the revised code, not the prior 311/17 configuration-closure baseline.

- Revised full round 1: clean test/bootJar/typecheck/frontendTest offline passed; backend 326/326, frontend 25/25 (8 files).
- Revised Playwright round 1: 11/11 across four specs, 1 worker, 0 retries; browser error fixture passed.
- Between rounds: runner removed its isolated app/database/credentials/assets; port 3200 released; Gradle daemon stopped.
- Revised full round 2: `test bootJar frontendTypecheck frontendTest --rerun-tasks --offline` passed, all ten tasks actually executed; backend 326/326, frontend 25/25, typecheck/jar green.
- Revised Playwright round 2: **10/11 passed, 1 failed**, 1 worker, 0 retries. The unchanged People/Relationships/Memory test timed out clicking the new fern-memory link after filtering through the hard-coded local end time `2026-10-04T00:00`. The run crossed midnight: the new memory is outside that end bound, the transient link detaches when filtered results arrive. Source and failure trace establish this expired date-window blocker, not monetary aggregation failure. The Usage cost spec and 19-route latest-jar smoke both passed in this failed suite. No retry, timeout extension or unrelated Memory change was made in this cost-only closure. This does not satisfy the two-green-Playwright FINAL gate.
- Failure details are retained in this report. The trace/error-context copy under ignored `build/evidence/cost-closure/` was temporary and removed by the later `clean` regression; it is not claimed as a retained artifact. Isolated runner app/database/credentials/assets were removed and port 3200 released. Both full browser suites' latest-jar route smoke passed. Commit-aligned jar metadata, post-commit smoke and final cleanup are reported separately in the task handoff; none of these can convert the failed full browser round into a pass.

Resumed FINAL audit confirms `/agent` and `/groups` still have navigation links and server SPA forwards, but no corresponding React routes, falling into PlaceholderPage. Operations' rejected fetch has no catch; Logs turns rejection into empty data; CSRF discovery is outside the request cleanup and has no AbortSignal. Access/Security/Operations and existing Usage token controls still expose raw English labels/enums. Security source explicitly sets nosniff and DENY frame options but not CSP/Referrer-Policy/Permissions-Policy; final policy justification/verification remains pending. The new cost controls/reasons themselves have zh-CN/en-US text. Cost-page responsive/metadata checks and existing 19-route smoke are not exhaustive theme/keyboard/visual/a11y/expiry evidence for every page, nor a meaningful audit of all 123 rows. In accordance with original Final Audit section 48, these remaining product gates stop FINAL; no unrelated feature is implemented and no `feat: complete admin console` seal or Phase 4C begins.

### Residual legacy monetary projection scan

After the first cost commit (`2ca9cf371b23757ada4759e82e2783e763d9c2b1`), a repository-wide SUM(cost)/estimatedCost consumer scan found two legacy read APIs summing the unused ledger placeholder. These are not used by the new Usage page but could still present unpriced usage as zero to another caller. Both legacy projections now explicitly expose `metric: TOKENS_ONLY` and omit the misleading cost field. No provider API or ledger schema is changed by this supplementary fix. COST-015 checks both protected endpoints against a deliberately nonzero placeholder, proving it is never treated as a charge. Targeted Usage costs 15/15 and existing ConsoleRead API contracts passed. The 326-test full validations above predate this final two-query cleanup and are not labelled validations of that later source snapshot.

Final source snapshot regression: `clean test bootJar frontendTypecheck frontendTest --offline` passed backend **327/327**, frontend **25/25**, typecheck and jar. After isolated Usage + latest-jar smoke passed **2/2**, its app/data/port were cleaned and the Gradle daemon stopped. Then `test bootJar frontendTypecheck frontendTest --rerun-tasks --offline` actually executed all ten tasks and again passed **327/327** backend and **25/25** frontend. No more source changes followed. The final commit-aligned jar smoke is recorded in the task handoff. This is cost closure regression evidence, not two green full Playwright runs: the date-boundary failure and other FINAL gates above remain open. No production data/services or real credentials were accessed, no live API tests were enabled, and no Phase 4C was started.

## 2026-10-04 FINAL AUDIT CLOSURE：午夜阻断关闭，最终验收仍被阻断

开始时 HEAD 为 `b034b4db4f1bfef6f934bbc0e5e533911ac32f09`，工作树 clean。本节是该基线之后的未提交修复与审计结果，不把历史 327/25 或 11/11 记录当作本轮最终代码的两轮验证。

### Midnight Flaky Fix

- 根因：`AdminConsole.e2e.spec.ts` 中两个新建记忆筛选窗口把结束时间写死为本地 `2026-10-04T00:00`。新记录超过排他性上界后，列表刷新删除了短暂出现的链接，点击等待超时。
- 修复：测试起点捕获一次 `testNow`，两个筛选均使用相对窗口 `[testNow - 3d, testNow + 1h)`，保留原 120 秒测试期限，不增加重试、超时、skip，不把截止日期换成明天。
- 时区：Playwright context 明确 `timezoneId: UTC`；运行 Playwright 的 Node 进程可能仍为 Asia/Shanghai，因此测试窗口使用 UTC 格式化，不依赖 Node 本地时区。产品前端仍尊重管理员的浏览器本地 `datetime-local`，经 `memoryFilterInstant` 转为 UTC ISO 字符串；后端 `Instant.parse` 要求时区，SQLite 使用 `julianday(created_at) >= from AND < to`，未改变产品时间语义。
- 确定性回归：`memoryTime.test.ts` 在固定 UTC 23:59 / 00:00 / 00:01 验证相对记录和跨日后 2 分钟记录仍在窗口内，另验证浏览器本地输入的 UTC 往返与独立 UTC 测试窗口。`PeopleMemoryConsoleContractTest.memoryMidnightRangesAreDeterministicAndHalfOpen` 固定数据库 fixture 的 createdAt，不等真实午夜；三个起点均通过真实 `/api/memory` 查询，并验证 from 包含、to 排除。

本轮实测（不是 FINAL 双轮）：

| 验证 | 结果 |
| --- | --- |
| `test --tests '*PeopleMemoryConsoleContractTest' bootJar frontendTypecheck frontendTest --offline` | SUCCESS；Memory 后端 8/8，前端完整单元测试 28/28（9 文件），typecheck/bootJar 通过 |
| `npm test -- --run src/ui/memoryTime.test.ts` | 3/3，无真实午夜等待 |
| 新建 fake runtime 的 Memory Playwright 定向测试 | 1/1，1 worker，`--retries=0`；测试 22.1 秒，Playwright 25.5 秒 |
| 定向浏览器错误 fixture | 未记录非预期 console.error/pageerror；不能据此声称完整 suite 的跨页面 unhandledrejection Gate 已关闭 |

### 仍失败的产品 Gate（按本轮第 37 项停止，不封版）

| 阻断 | 本轮源码证据 / 分类 |
| --- | --- |
| Navigation / placeholder | `App.tsx` nav 含 `/agent`、`/groups`，React 无对应 Route，落入 `PlaceholderPage`；`SpaForwardController` 也仍转发；`future` 文案承诺后续 Phase 4B。分类 C：未实现导航壳残留，不应临时补大功能。正式范围为 Login + 19 个受保护顶层页面，另有详情路由，不把两个占位项计作已验收页面。 |
| i18n | 已实际运行只读 `node frontend/scripts/audit-console.mjs`。两个主 catalog 均为 180 keys，无对称缺失；仅代表主字典结构。`No records`、`Platform/Scope/Stable ID/Effect`、`ALL`、`Loading…/Unavailable`、Usage 的 Provider/Model/Currency/Requests 等是用户文案，不能作为协议枚举排除。语言自称 中文/English、品牌、UTC、MIME/hash、协议 ID 单独排除。动态后端表头/状态、本地化 fallback 和 raw key 浏览器检查尚未全量关闭。五个 unused 候选为 session/qqPlaceholder/qqHint/invalid/unknownError，需人工确认后删除，不能仅凭词法扫描判定全部 i18n 完成。 |
| Error states | `OperationsPage` 的 `.then(setData)` 无 catch，拒绝后仍显示 Loading；`LogsPage` catch 把错误变为 `[]`，与真正 empty 混淆；Access preview 的 async click 无 catch；`api.ts` 的 CSRF 获取在 try/finally 外，且该 fetch 未接入 AbortSignal。需要专门失败注入测试，不能用成功 API 200 掩盖。 |
| Accessibility | Login 与 Settings 的 field-error 无 aria-describedby 关联；Prompt 的原生 dialog 缺少明确 accessible name；已有 focus trap/Esc 源码及部分历史测试不能替代本轮纯键盘工作流、四主题和所有宽度实测。 |
| Browser audit | `auditFixtures.ts` 的 unhandledrejection counter 每次导航重新初始化；最终 document 的 0 不能证明之前 document 无异常。需要跨导航持久的测试收集器，不存敏感内容。 |
| Matrix evidence / scope | 重算 123 = 69 TESTED + 30 FOUNDATION + 24 PLANNED + 0 NEEDS_REVIEW。只读检查 TESTED 单元格 49 个 class-qualified method 引用，其中 48 个精确引用存在，1 个范围 shorthand 需展开语义复核；显式前端测试文件无缺失。不宣称全部别名、非限定方法和每行行为证明已审核。数字 ID 105/109/110 重复；使用 ordinal 区分，未擅自重编号。#25–30、#31/#47 等状态/描述与后来 AccessControlService/Social Settings 有陈旧之处，需逐行对照真实测试后更正，不直接把 PLANNED 批量升绿。尚不能确认“无隐藏 Phase 4B blocker”。 |

### 其他 Gate 的当前证据边界

- Responsive：本轮 Memory 定向流程含 375px 的读写/范围确认及 body overflow 检查；430/1280/1440 和所有正式页面没有本轮完整验收。
- Themes：Light/Dark/晚宁/System 的 CSS tokens 存在；本轮未完成跨 Memory/Prompt/Usage/Security/Logs/Operations 的对比度、focus、selected、warning/error/disabled 验收。
- Security/privacy：只读扫描生产前端，storage 使用只涉及 language/theme，未发现 dangerouslySetInnerHTML/innerHTML。React/diff 文本转义源码已查看，但没有把全部可编辑内容的 XSS 浏览器测试标绿。既有 auth/session/CSRF/password rotation/SecretStore/SSRF/path/UNKNOWN 契约保留，未在此轮全部重跑。`auth011` 当前只证明一个 fake conversation GET 在普通 profile 中缺失，不能替代所有 fake mutation/provider/bootstrap 的隔离检查。
- Headers：源码明确 nosniff、DENY frame，session HttpOnly/SameSite 与 effective Secure 机制已有；尚无显式 Referrer-Policy/CSP 最终验证或完整适用性决策，因此不宣称 header Gate 全绿。
- Errors：本轮 Memory 定向契约覆盖相关 400/401/409；403/404/429/500 的全 Console API/页面失败路径未完成本轮验收。500 handler 使用固定 message/code/traceId；400/ResponseStatusException reason 的安全性仍需逐调用源复核。
- TODO/FIXME/HACK/TEMP/XXX：只读扫描 `frontend/src` 非测试代码和 `src/main/java` 无匹配；这不代表不存在用其他措辞表达的未完成项。
- Bundle：本轮 bootJar 构建 main JS 528.00 kB（gzip 160.07），CSS 28.32 kB（gzip 6.38）；保留 Vite 500 kB 阈值警告，没有抬高阈值、下载依赖或进行高风险拆包。
- Docs：本轮仅补充本报告和 CONSOLE_ARCHITECTURE 的时间/验收证据边界。OBSERVABILITY.md 当前不存在；未为了凑文件新增，也未宣称其文档 Gate 完成。其他运行时/媒体/网关文档没有需要跟随此次测试修复的产品契约变化。
- 第 29–34 项两轮全量 FINAL 构建、完整零重试 Playwright 及最终 20 页面 smoke：产品 Gate 已失败，未开始，不把本轮定向测试或基线历史测试替代它们。
- 生产隔离：未 SSH、未真实 QQ/SnowLuma/DSH/DeepSeek，未访问生产凭据或修改 Zetu；仅本地 loopback fake runtime。
- 收尾：定向 runner 已结束并移除其隔离 DB/secret/assets/runtime；3200 无 LISTENING，Gradle daemon 已停止。工作树只保留本轮预期测试/工具/文档修复，没有最终提交。**Phase 4B NOT COMPLETE / NOT SEALED，未进入 Phase 4C。**
