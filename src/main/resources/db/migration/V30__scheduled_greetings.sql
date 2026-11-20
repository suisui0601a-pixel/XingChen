ALTER TABLE proactive_attempts ADD COLUMN intent TEXT NOT NULL DEFAULT 'RANDOM' CHECK(intent IN ('RANDOM','MORNING','EVENING'));
ALTER TABLE proactive_attempts ADD COLUMN schedule_key TEXT;
CREATE UNIQUE INDEX proactive_greeting_once_idx ON proactive_attempts(conversation_id,schedule_key) WHERE schedule_key IS NOT NULL;
CREATE INDEX proactive_intent_time_idx ON proactive_attempts(intent,checked_at DESC);

-- Intentionally replace pre-upgrade manual policy once; later administrator edits survive.
CREATE TABLE social_policy_preset_backups (
  preset_id TEXT NOT NULL,
  scope TEXT NOT NULL CHECK(scope IN ('GLOBAL','CONVERSATION')),
  conversation_id TEXT NOT NULL,
  revision INTEGER NOT NULL,
  values_json TEXT NOT NULL,
  original_updated_at TEXT NOT NULL,
  original_updated_by TEXT NOT NULL,
  backed_up_at TEXT NOT NULL,
  PRIMARY KEY(preset_id,scope,conversation_id)
);
INSERT INTO social_policy_preset_backups
SELECT 'public-social-v1','GLOBAL','',revision,values_json,updated_at,updated_by,strftime('%Y-%m-%dT%H:%M:%fZ','now') FROM social_settings_global;
INSERT INTO social_policy_preset_backups
SELECT 'public-social-v1','CONVERSATION',conversation_id,revision,values_json,updated_at,updated_by,strftime('%Y-%m-%dT%H:%M:%fZ','now') FROM social_settings_override;
UPDATE social_settings_global SET values_json=json_set(values_json,
  '$.ordinaryMessageProbability',0.05,
  '$.proactivePrivateEnabled',json('true'),'$.proactiveGroupEnabled',json('true'),
  '$.proactiveIdleMinutes',120,'$.proactiveIntervalMinutes',120,'$.proactiveCooldownMinutes',120,
  '$.proactiveProbability',0.3,'$.proactiveMaxPerDay',8,
  '$.proactiveQuietStartHour',23,'$.proactiveQuietEndHour',8,'$.proactiveZoneId','Asia/Shanghai',
  '$.dailyGreetingsEnabled',json('true'),'$.morningGreetingTime','07:00','$.eveningGreetingTime','22:50',
  '$.nightReplyMuteEnabled',json('true'),'$.nightReplyStartHour',0,'$.nightReplyEndHour',6),
  revision=revision+1,updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now'),updated_by='migration:public-social-v1';
UPDATE social_settings_override SET values_json=json_remove(values_json,
  '$.ordinaryMessageProbability','$.proactivePrivateEnabled','$.proactiveGroupEnabled',
  '$.proactiveIdleMinutes','$.proactiveIntervalMinutes','$.proactiveCooldownMinutes',
  '$.proactiveProbability','$.proactiveMaxPerDay','$.proactiveQuietStartHour','$.proactiveQuietEndHour','$.proactiveZoneId',
  '$.dailyGreetingsEnabled','$.morningGreetingTime','$.eveningGreetingTime',
  '$.nightReplyMuteEnabled','$.nightReplyStartHour','$.nightReplyEndHour'),
  revision=revision+1,updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now'),updated_by='migration:public-social-v1'
WHERE EXISTS(SELECT 1 FROM json_each(values_json) WHERE key IN (
  'ordinaryMessageProbability','proactivePrivateEnabled','proactiveGroupEnabled',
  'proactiveIdleMinutes','proactiveIntervalMinutes','proactiveCooldownMinutes',
  'proactiveProbability','proactiveMaxPerDay','proactiveQuietStartHour','proactiveQuietEndHour','proactiveZoneId',
  'dailyGreetingsEnabled','morningGreetingTime','eveningGreetingTime',
  'nightReplyMuteEnabled','nightReplyStartHour','nightReplyEndHour'));
