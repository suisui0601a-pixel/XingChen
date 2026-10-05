ALTER TABLE prompt_profiles ADD COLUMN active_simulation_version_id TEXT;
ALTER TABLE prompt_profiles ADD COLUMN active_persona_version_id TEXT;
ALTER TABLE prompt_profiles ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE prompt_versions ADD COLUMN note TEXT NOT NULL DEFAULT '';
CREATE TABLE prompt_console_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT, occurred_at TEXT NOT NULL, actor TEXT NOT NULL,
  layer TEXT NOT NULL CHECK(layer IN ('SIMULATION','PERSONA')), operation TEXT NOT NULL,
  from_version_id TEXT, to_version_id TEXT, source_version_id TEXT, note TEXT NOT NULL DEFAULT ''
);
CREATE INDEX prompt_console_audit_time_idx ON prompt_console_audit(occurred_at DESC);
