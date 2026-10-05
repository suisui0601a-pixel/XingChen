CREATE TABLE legacy_migration_ledger (
  source_fingerprint TEXT PRIMARY KEY,
  source_type TEXT NOT NULL,
  imported_at TEXT NOT NULL,
  imported_count INTEGER NOT NULL
);
