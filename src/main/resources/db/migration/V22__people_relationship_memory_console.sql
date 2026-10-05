ALTER TABLE relationship_terms ADD COLUMN active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1));
CREATE TABLE relationship_console_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT, occurred_at TEXT NOT NULL, actor TEXT NOT NULL,
  person_id TEXT NOT NULL, scope_type TEXT NOT NULL, scope_id TEXT, operation TEXT NOT NULL,
  old_term TEXT, new_term TEXT, result TEXT NOT NULL
);
CREATE INDEX relationship_console_audit_time_idx ON relationship_console_audit(occurred_at DESC);
CREATE TABLE memory_console_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT, occurred_at TEXT NOT NULL, actor TEXT NOT NULL,
  memory_id TEXT NOT NULL, operation TEXT NOT NULL, type TEXT NOT NULL,
  old_scope TEXT, new_scope TEXT, result TEXT NOT NULL
);
CREATE INDEX memory_console_audit_time_idx ON memory_console_audit(occurred_at DESC);
CREATE TABLE memory_admin_provenance (
  memory_id TEXT PRIMARY KEY REFERENCES memories(id), actor TEXT NOT NULL, created_at TEXT NOT NULL
);
