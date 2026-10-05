CREATE TABLE persons (
  id TEXT PRIMARY KEY, platform TEXT NOT NULL, platform_user_id TEXT NOT NULL,
  is_self INTEGER NOT NULL DEFAULT 0 CHECK (is_self IN (0,1)),
  is_owner INTEGER NOT NULL DEFAULT 0 CHECK (is_owner IN (0,1)),
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  UNIQUE(platform, platform_user_id)
);
CREATE TABLE conversations (
  id TEXT PRIMARY KEY, platform TEXT NOT NULL, type TEXT NOT NULL CHECK(type IN ('PRIVATE','GROUP')),
  platform_conversation_id TEXT NOT NULL, created_at TEXT NOT NULL,
  UNIQUE(platform,type,platform_conversation_id)
);
CREATE TABLE person_aliases (
  id TEXT PRIMARY KEY, person_id TEXT NOT NULL REFERENCES persons(id), alias TEXT NOT NULL,
  source_conversation_id TEXT REFERENCES conversations(id), first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL
);
CREATE INDEX idx_alias_person ON person_aliases(person_id, last_seen_at DESC);
CREATE TABLE memberships (
  id TEXT PRIMARY KEY, person_id TEXT NOT NULL REFERENCES persons(id), conversation_id TEXT NOT NULL REFERENCES conversations(id),
  current_display_name TEXT, card TEXT, platform_role TEXT NOT NULL DEFAULT 'MEMBER', first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL, UNIQUE(person_id,conversation_id)
);
CREATE INDEX idx_memberships_conversation ON memberships(conversation_id,last_seen_at DESC);
CREATE TABLE messages (
  id TEXT PRIMARY KEY, platform TEXT NOT NULL, conversation_id TEXT NOT NULL REFERENCES conversations(id),
  actor_person_id TEXT NOT NULL REFERENCES persons(id), platform_message_id TEXT NOT NULL,
  reply_to_message_id TEXT, text TEXT NOT NULL DEFAULT '', is_self INTEGER NOT NULL DEFAULT 0,
  is_owner INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, raw_metadata_json TEXT NOT NULL DEFAULT '{}',
  UNIQUE(platform,platform_message_id)
);
CREATE INDEX idx_messages_conversation_time ON messages(conversation_id,created_at DESC);
CREATE TABLE relationship_terms (
  id TEXT PRIMARY KEY, subject_person_id TEXT NOT NULL REFERENCES persons(id), target_person_id TEXT NOT NULL REFERENCES persons(id),
  type TEXT NOT NULL, value TEXT NOT NULL, scope_type TEXT NOT NULL CHECK(scope_type IN ('GLOBAL','CONVERSATION','PRIVATE')),
  scope_id TEXT, explicit INTEGER NOT NULL, confidence REAL NOT NULL CHECK(confidence BETWEEN 0 AND 1),
  source_message_id TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE INDEX idx_relationship_lookup ON relationship_terms(subject_person_id,target_person_id,type,scope_type,scope_id);
CREATE TABLE memories (
  id TEXT PRIMARY KEY, type TEXT NOT NULL, subject_person_id TEXT REFERENCES persons(id), conversation_id TEXT REFERENCES conversations(id),
  project_key TEXT, content TEXT NOT NULL, scope_type TEXT NOT NULL, scope_id TEXT,
  confidence REAL NOT NULL CHECK(confidence BETWEEN 0 AND 1), importance REAL NOT NULL CHECK(importance BETWEEN 0 AND 1),
  explicit INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, last_accessed_at TEXT,
  expires_at TEXT, status TEXT NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX idx_memories_subject ON memories(subject_person_id,status,updated_at DESC);
CREATE INDEX idx_memories_conversation ON memories(conversation_id,status,updated_at DESC);
CREATE INDEX idx_memories_scope ON memories(scope_type,scope_id,status);
CREATE TABLE memory_sources (
  id INTEGER PRIMARY KEY AUTOINCREMENT, memory_id TEXT NOT NULL REFERENCES memories(id) ON DELETE CASCADE,
  platform TEXT NOT NULL, conversation_id TEXT REFERENCES conversations(id), message_id TEXT NOT NULL,
  actor_person_id TEXT NOT NULL REFERENCES persons(id), timestamp TEXT NOT NULL,
  UNIQUE(memory_id,platform,conversation_id,message_id,actor_person_id)
);
CREATE INDEX idx_memory_sources_actor ON memory_sources(actor_person_id,timestamp DESC);
CREATE VIRTUAL TABLE memories_fts USING fts5(memory_id UNINDEXED,content, tokenize='unicode61');
CREATE TRIGGER memories_fts_insert AFTER INSERT ON memories BEGIN INSERT INTO memories_fts(memory_id,content) VALUES(new.id,new.content); END;
CREATE TRIGGER memories_fts_update AFTER UPDATE OF content ON memories BEGIN DELETE FROM memories_fts WHERE memory_id=old.id; INSERT INTO memories_fts(memory_id,content) VALUES(new.id,new.content); END;
CREATE TRIGGER memories_fts_delete AFTER DELETE ON memories BEGIN DELETE FROM memories_fts WHERE memory_id=old.id; END;
CREATE TABLE conversation_summaries (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id), conversation_summary TEXT NOT NULL DEFAULT '',
  active_topics TEXT NOT NULL DEFAULT '[]', people TEXT NOT NULL DEFAULT '[]', relationships TEXT NOT NULL DEFAULT '[]',
  open_tasks TEXT NOT NULL DEFAULT '[]', decisions TEXT NOT NULL DEFAULT '[]', important_facts TEXT NOT NULL DEFAULT '[]',
  recent_context TEXT NOT NULL DEFAULT '[]', updated_at TEXT NOT NULL
);
CREATE TABLE prompt_profiles (id TEXT PRIMARY KEY,name TEXT NOT NULL,simulation_prompt TEXT NOT NULL DEFAULT '',persona_prompt TEXT NOT NULL DEFAULT '',updated_at TEXT NOT NULL);
CREATE TABLE prompt_versions (
  id TEXT PRIMARY KEY,profile_id TEXT NOT NULL REFERENCES prompt_profiles(id),layer TEXT NOT NULL CHECK(layer IN ('SIMULATION','PERSONA')),
  content TEXT NOT NULL,version INTEGER NOT NULL,created_at TEXT NOT NULL,created_by TEXT NOT NULL,checksum TEXT NOT NULL,
  UNIQUE(profile_id,layer,version)
);
CREATE INDEX idx_prompt_versions_history ON prompt_versions(profile_id,layer,version DESC);
CREATE TABLE session_state (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id), session_id TEXT NOT NULL, status TEXT NOT NULL,
  token_estimate INTEGER NOT NULL DEFAULT 0, updated_at TEXT NOT NULL
);
CREATE TABLE token_usage (
  id TEXT PRIMARY KEY, conversation_id TEXT REFERENCES conversations(id), turn_id TEXT, model TEXT NOT NULL,
  usage_time TEXT NOT NULL, input_tokens INTEGER NOT NULL DEFAULT 0, cache_hit_tokens INTEGER NOT NULL DEFAULT 0,
  cache_miss_tokens INTEGER NOT NULL DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0, reasoning_tokens INTEGER NOT NULL DEFAULT 0,
  cost REAL NOT NULL DEFAULT 0, duration_ms INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_token_usage_conversation_time ON token_usage(conversation_id,usage_time DESC);
