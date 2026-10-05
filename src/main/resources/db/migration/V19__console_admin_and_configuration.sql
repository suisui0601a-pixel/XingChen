CREATE TABLE console_admin_credentials (
  username TEXT PRIMARY KEY,
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE console_configuration (
  category TEXT NOT NULL,
  config_key TEXT NOT NULL,
  value_json TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  updated_by TEXT NOT NULL,
  PRIMARY KEY(category, config_key)
);

CREATE TABLE console_configuration_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at TEXT NOT NULL,
  actor TEXT NOT NULL,
  category TEXT NOT NULL,
  config_key TEXT NOT NULL,
  action TEXT NOT NULL
);
CREATE INDEX console_configuration_audit_time_idx ON console_configuration_audit(occurred_at DESC);
