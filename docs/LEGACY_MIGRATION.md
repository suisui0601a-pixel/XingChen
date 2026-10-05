# Legacy dry-run / transactional import

范围以 `LEGACY_MIGRATION_MAPPING.md` 为准：仅 Bridge 0.1.7 config 的 owner 与 allow/deny 稳定 ID。未证明的字段跳过，不迁移任何 secret。源 archive 永远只读；解出的 config 只能写到新的私有 staging，结束后删除。

```sh
# 自己的 Core 必须停止；target 必须已用当前 image 初始化 schema 28。
sudo -u '#10001' python3 tools/legacy_migrate.py \
  --source /srv/xingchen/migration/PLACEHOLDER-config.json --data /srv/xingchen/live
# 审核 report: discovered/importable/skips/conflicts/duplicates；不含 ID、昵称或值。
# 审核通过后填入上一条报告的 sourceFingerprint。
sudo -u '#10001' python3 tools/legacy_migrate.py \
  --source /srv/xingchen/migration/PLACEHOLDER-config.json --data /srv/xingchen/live \
  --apply --expected-fingerprint PLACEHOLDER-REVIEWED-SHA256
```

dry-run 使用 SQLite mode=ro，不修改目标 DB；只操作目标已有维护锁，不向 archive 写 marker。真实 apply 的人/访问规则/ledger 在一个 `BEGIN IMMEDIATE` transaction 中，失败全部 rollback。FK 开启，现有显式数据优先，冲突 skip。相同源 fingerprint 已导入则 ALREADY_IMPORTED/no-op；源发生变化后仍对 stable key 去重，不会覆盖先前或当前用户数据。

OWNER 并不是 QQ 自动登录。工具不连接 Gateway、不建新 QQ、不把数字 ID 作为密码或 token。不会迁移 group memberships/SELF：config 没有充分证据。重复列表 ID 去重；allow/deny 自相矛盾归 AMBIGUOUS；非数字昵称/partial/corrupt config 整个 fail-closed，不猜身份。

迁移前先备份。导入后启动自己的隔离 Core，核对访问策略和当前 prompt；生产切换、Gateway 登录及其生命周期不属于本轮。旧 archive 真 dry-run 只能作为附加证据，不能代替 sanitized fixture 的 empty/conflict/duplicate/invalid/rollback 测试。
