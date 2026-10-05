CREATE TABLE dsh_pending_question_answers (
  interaction_id TEXT NOT NULL REFERENCES dsh_pending_interactions(id) ON DELETE CASCADE,
  question_id TEXT NOT NULL,
  answer_text TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(interaction_id,question_id)
);
