CREATE TABLE simulation_conversation_state (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id),
  mode TEXT NOT NULL DEFAULT 'SOCIAL',
  wake_state TEXT NOT NULL DEFAULT 'AWAKE' CHECK(wake_state IN ('AWAKE','SLEEPING','IDLE')),
  last_read_cursor TEXT,
  last_action_at TEXT,
  last_incoming_at TEXT,
  sleep_until TEXT,
  wake_config_json TEXT NOT NULL DEFAULT '{}',
  consecutive_no_action INTEGER NOT NULL DEFAULT 0 CHECK(consecutive_no_action >= 0),
  generation INTEGER NOT NULL DEFAULT 0 CHECK(generation >= 0),
  updated_at TEXT NOT NULL
);
