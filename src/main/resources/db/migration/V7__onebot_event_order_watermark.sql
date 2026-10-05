CREATE TABLE event_order_state (
  platform TEXT NOT NULL,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  last_timestamp TEXT NOT NULL,
  last_message_id TEXT NOT NULL,
  last_sequence INTEGER,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(platform, conversation_id)
);
