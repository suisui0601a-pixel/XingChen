-- SQLite REAL cannot preserve newly entered decimal rates. Legacy REAL values
-- retain their stored representation; this cannot recover already lost precision.
CREATE TABLE console_pricing_decimal (
 provider TEXT NOT NULL, model TEXT NOT NULL,
 input TEXT, cache_hit TEXT, cache_miss TEXT, output TEXT, reasoning TEXT,
 currency TEXT NOT NULL, revision INTEGER NOT NULL,
 updated_at TEXT NOT NULL, updated_by TEXT NOT NULL, PRIMARY KEY(provider,model)
);
INSERT INTO console_pricing_decimal SELECT provider,model,CAST(input AS TEXT),CAST(cache_hit AS TEXT),CAST(cache_miss AS TEXT),CAST(output AS TEXT),CAST(reasoning AS TEXT),currency,revision,updated_at,updated_by FROM console_pricing;
DROP TABLE console_pricing;
ALTER TABLE console_pricing_decimal RENAME TO console_pricing;
CREATE TABLE console_pricing_audit_decimal (
 id INTEGER PRIMARY KEY AUTOINCREMENT, occurred_at TEXT NOT NULL, actor TEXT NOT NULL,
 provider TEXT NOT NULL, model TEXT NOT NULL,
 old_input TEXT, new_input TEXT, old_cache_hit TEXT, new_cache_hit TEXT,
 old_cache_miss TEXT, new_cache_miss TEXT, old_output TEXT, new_output TEXT,
 old_reasoning TEXT, new_reasoning TEXT, currency TEXT NOT NULL, revision INTEGER NOT NULL
);
INSERT INTO console_pricing_audit_decimal SELECT id,occurred_at,actor,provider,model,CAST(old_input AS TEXT),CAST(new_input AS TEXT),CAST(old_cache_hit AS TEXT),CAST(new_cache_hit AS TEXT),CAST(old_cache_miss AS TEXT),CAST(new_cache_miss AS TEXT),CAST(old_output AS TEXT),CAST(new_output AS TEXT),CAST(old_reasoning AS TEXT),CAST(new_reasoning AS TEXT),currency,revision FROM console_pricing_audit;
DROP TABLE console_pricing_audit;
ALTER TABLE console_pricing_audit_decimal RENAME TO console_pricing_audit;
CREATE INDEX console_pricing_audit_time_idx ON console_pricing_audit(occurred_at DESC);
