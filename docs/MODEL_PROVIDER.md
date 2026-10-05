# Model provider boundary

`ModelProvider` is the provider-neutral contract. The DeepSeek adapter owns HTTP, auth header construction, response/SSE parsing, retry policy, cancellation and provider usage mapping. The API key is resolved only from the configured environment-variable name (`XINGCHEN_DEEPSEEK_API_KEY` by default); it is not held in Spring properties, trace records or logs.

Requests carry system/user/assistant/tool messages and JSON-schema tool definitions. Streaming emits bounded content deltas and a terminal usage event. Retry is limited to configured transport timeout, 429 and 5xx responses; malformed responses and auth failures are sanitized and not retried. Model usage is per-call and does not represent context length.

Ordinary `test` uses local HTTP fixtures and excludes `DeepSeekLiveTest`. The opt-in `liveTest` task requires `XINGCHEN_LIVE_TEST=1` and a configured key, has exactly five requests capped at 64 output tokens each, and is not run by Phase 3 verification unless explicitly requested.

# Local opt-in only

```powershell
$env:XINGCHEN_LIVE_TEST = '1'
# Set XINGCHEN_DEEPSEEK_API_KEY through a secret manager or protected process environment.
./gradlew.bat liveTest
```

Do not put the key in Gradle arguments, files committed to Git, logs, or test reports.
