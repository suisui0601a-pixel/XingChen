DROP TRIGGER memories_fts_insert;
DROP TRIGGER memories_fts_update;

CREATE TRIGGER memories_fts_insert AFTER INSERT ON memories
WHEN new.status = 'ACTIVE'
BEGIN
  INSERT INTO memories_fts(memory_id, content) VALUES(new.id, new.content);
END;

CREATE TRIGGER memories_fts_update AFTER UPDATE OF content, status ON memories
BEGIN
  DELETE FROM memories_fts WHERE memory_id = old.id;
  INSERT INTO memories_fts(memory_id, content)
  SELECT new.id, new.content WHERE new.status = 'ACTIVE';
END;

DELETE FROM memories_fts WHERE memory_id IN (SELECT id FROM memories WHERE status <> 'ACTIVE');
