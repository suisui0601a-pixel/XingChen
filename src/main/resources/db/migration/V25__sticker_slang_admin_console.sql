ALTER TABLE stickers ADD COLUMN file_name TEXT NOT NULL DEFAULT '';
ALTER TABLE stickers ADD COLUMN mime_type TEXT NOT NULL DEFAULT 'application/octet-stream';
ALTER TABLE stickers ADD COLUMN file_size INTEGER NOT NULL DEFAULT 0;
ALTER TABLE stickers ADD COLUMN width INTEGER;
ALTER TABLE stickers ADD COLUMN height INTEGER;
ALTER TABLE stickers ADD COLUMN animated INTEGER;
ALTER TABLE stickers ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1;
ALTER TABLE stickers ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
CREATE INDEX idx_stickers_enabled_updated ON stickers(enabled,updated_at DESC);

CREATE TABLE slang_console_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at TEXT NOT NULL,
  actor TEXT NOT NULL,
  term_id TEXT NOT NULL,
  action TEXT NOT NULL,
  old_status TEXT,
  new_status TEXT
);
CREATE INDEX idx_slang_console_audit_time ON slang_console_audit(occurred_at DESC);
