CREATE TABLE simulation_conversation_state_next (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id),
  mode TEXT NOT NULL DEFAULT 'SOCIAL',
  wake_state TEXT NOT NULL DEFAULT 'AWAKE' CHECK(wake_state IN ('AWAKE','SLEEPING','IDLE','WAITING')),
  last_read_cursor TEXT,
  last_action_at TEXT,
  last_incoming_at TEXT,
  sleep_until TEXT,
  wake_config_json TEXT NOT NULL DEFAULT '{}',
  consecutive_no_action INTEGER NOT NULL DEFAULT 0 CHECK(consecutive_no_action >= 0),
  generation INTEGER NOT NULL DEFAULT 0 CHECK(generation >= 0),
  updated_at TEXT NOT NULL,
  origin_turn_id TEXT,
  waiting_since TEXT,
  wait_reason TEXT
);

INSERT INTO simulation_conversation_state_next(
  conversation_id,mode,wake_state,last_read_cursor,last_action_at,last_incoming_at,sleep_until,
  wake_config_json,consecutive_no_action,generation,updated_at
)
SELECT conversation_id,mode,wake_state,last_read_cursor,last_action_at,last_incoming_at,sleep_until,
       wake_config_json,consecutive_no_action,generation,updated_at
FROM simulation_conversation_state;

DROP TABLE simulation_conversation_state;
ALTER TABLE simulation_conversation_state_next RENAME TO simulation_conversation_state;
