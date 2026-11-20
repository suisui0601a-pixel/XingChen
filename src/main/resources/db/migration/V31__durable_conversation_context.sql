-- Context is evidence, not another message consumer or a replay queue.
ALTER TABLE messages ADD COLUMN context_generation INTEGER NOT NULL DEFAULT 0;
UPDATE messages SET context_generation=COALESCE((SELECT i.generation FROM inbound_turn_executions i WHERE i.conversation_id=messages.conversation_id AND i.incoming_event_id=messages.platform_message_id AND i.platform=messages.platform),0);
ALTER TABLE messages ADD COLUMN context_speaker_name TEXT;
ALTER TABLE session_state ADD COLUMN history_start_message_id TEXT;
ALTER TABLE session_state ADD COLUMN history_reset_generation INTEGER NOT NULL DEFAULT 0;
ALTER TABLE onebot_outbound_executions ADD COLUMN context_text TEXT;
ALTER TABLE onebot_outbound_executions ADD COLUMN context_kind TEXT CHECK(context_kind IN ('TEXT','STICKER'));
CREATE INDEX messages_context_idx ON messages(conversation_id,context_generation,message_type);
