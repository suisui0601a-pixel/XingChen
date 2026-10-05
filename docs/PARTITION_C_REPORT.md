# Partition C — Persistence Semantics

**Status: PASS — APPLICATION PERSISTENCE TESTED.** All context-reopen suites also pass in the final 339-test Linux run on runtime source `fb8e70860ab457fa09e334ed69ba4f9a1f50b8f3`. Partition E separately proves Docker restart and same-volume remove/recreate for every requested category, including CLEAR, administrator/bootstrap precedence and explicit-empty public URL. Application and Docker evidence remain distinguished.

## Historical application-only record (before E; later Docker closure is in PARTITION_E_REPORT)

Ran on audited commit `e0ca8493151a2efa7cff4bccb2f97e830ba9d579`:

```text
./gradlew.bat test --offline --tests '*EffectiveConfigurationRestartTest' --tests '*SocialRuntimeWaitRestartE2ETest' --tests '*SocialRuntimeFinalEvidenceE2ETest' --tests '*UsageCostAccountingTest' --tests '*StickerSlangVoiceConsoleContractTest' --tests '*ClosedAgentAccessPolicyTest'
BUILD SUCCESSFUL
```

Selected results: 44 tests, 0 failures/errors/skips. (3 EffectiveConfigurationRestart, 15 SocialRuntimeWaitRestart, 3 SocialRuntimeFinalEvidence, 15 UsageCostAccounting, 7 StickerSlangVoiceConsole, 1 ClosedAgentAccessPolicy.) Tests use isolated temporary SQLite/filesystem fixtures and fake OneBot/model integrations; no production endpoint or secret was used.

| Requested persistence item | Evidence | Assessment |
|---|---|---|
| Environment bootstrap / persisted config wins | Effective configuration test starts, persists public URL, closes and reopens against same DB; persisted value beats changed bootstrap input | Context-reopen tested |
| Secret CLEAR persists | OneBot secret is cleared, context closes, reopen confirms no token is configured; explicit tombstone/bootstrap behavior also tested | Context-reopen tested |
| Administrator credential | `ApplicationPersistenceReopenTest` bootstraps an administrator, rotates its password through the API, closes and reopens the application with a different bootstrap password, then verifies the persisted hash is unchanged, both bootstrap passwords fail and the rotated password authenticates | Context-reopen tested |
| `publicBaseUrl` | Same-DB context close/reopen; persisted value and explicit empty clear are verified, including cookie posture | Context-reopen tested |
| Memory | Runtime E2E writes and retrieves memory across application restart | Context-reopen tested |
| Prompt versions | Prompt/runtime restart test closes and reopens on same database; active prompt/version history survives | Context-reopen tested |
| Access policy | New reopen test saves private ALLOW/group DENY through the API; reopened policy keeps revision and rules, allows the expected private identity, denies outsiders and preserves group deny priority | Context-reopen tested |
| Pricing | New reopen test saves rates through the API; reopened API retains revision/model and SQLite retains the exact 18-decimal input rate | Context-reopen tested |
| Sticker metadata and filesystem root | New reopen test uploads a PNG and edits its tags/note/enabled flag through the API; after reopening the same data tree it verifies metadata, root/database paths and byte-identical preview from the persistent library | Context-reopen tested |

Closure test added: `ApplicationPersistenceReopenTest.adminAccessPricingAndStickerSurviveClosedContextWithChangedBootstrap`. Initial focused run passed (1/1). A combined reopen regression run passed 22/22 with 0 failures/errors/skips: this test plus EffectiveConfigurationRestart, SocialRuntimeWaitRestart and SocialRuntimeFinalEvidence. All fixtures are isolated and external integrations are disabled or fake.

This partition establishes application persistence semantics. It does not prove Docker named-volume initialization, survival after `docker restart`, or remove/recreate with the same named volume. Those runtime gates remain in Partition E.

Final Linux regression also passed all 336 backend tests on clean commit `7ad879afedfef4204f4def246d8d18ce222e9a29`, including all application reopen and packaging tests. No application implementation change was needed for the C gaps.
