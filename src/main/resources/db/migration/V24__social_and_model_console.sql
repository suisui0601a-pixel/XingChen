CREATE TABLE social_settings_global (
  singleton_id INTEGER PRIMARY KEY CHECK(singleton_id = 1),
  revision INTEGER NOT NULL,
  values_json TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  updated_by TEXT NOT NULL
);

CREATE TABLE social_settings_override (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
  revision INTEGER NOT NULL,
  values_json TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  updated_by TEXT NOT NULL
);

CREATE TABLE social_settings_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at TEXT NOT NULL,
  actor TEXT NOT NULL,
  scope TEXT NOT NULL CHECK(scope IN ('GLOBAL','CONVERSATION')),
  conversation_id TEXT,
  setting_key TEXT NOT NULL,
  old_value_json TEXT,
  new_value_json TEXT
);
CREATE INDEX social_settings_audit_time_idx ON social_settings_audit(occurred_at DESC);

CREATE TABLE model_provider_config (
  provider_id TEXT PRIMARY KEY,
  revision INTEGER NOT NULL,
  values_json TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  updated_by TEXT NOT NULL
);

CREATE TABLE model_provider_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at TEXT NOT NULL,
  actor TEXT NOT NULL,
  provider_id TEXT NOT NULL,
  setting_key TEXT NOT NULL,
  old_value_json TEXT,
  new_value_json TEXT
);
CREATE INDEX model_provider_audit_time_idx ON model_provider_audit(occurred_at DESC);

CREATE TABLE model_provider_test_status (
  provider_id TEXT PRIMARY KEY,
  status TEXT NOT NULL CHECK(status IN ('SUCCESS','AUTH_FAILED','UNREACHABLE','TIMEOUT','INVALID_CONFIG','UNSUPPORTED')),
  safe_summary TEXT NOT NULL,
  tested_at TEXT NOT NULL
);
