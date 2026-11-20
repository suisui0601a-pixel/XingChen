package online.wanan.xingchen.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.context.ContextMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** A bounded view of existing durable evidence, never a source of replay work. */
@Repository
public class ConversationHistoryStore {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public ConversationHistoryStore(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @Transactional(readOnly=true)
    public List<ContextMessage> recent(UUID conversation,String currentEventId,int requested){
        int limit=Math.min(100,Math.max(0,requested));if(limit==0)return List.of();String cid=conversation.toString();
        long generation=jdbc.queryForObject("SELECT MAX(g) FROM (SELECT generation g FROM conversation_reset_controls WHERE conversation_id=? UNION ALL SELECT history_reset_generation g FROM session_state WHERE conversation_id=? UNION ALL SELECT 0)",Long.class,cid,cid);
        var starts=jdbc.query("SELECT history_start_message_id FROM session_state WHERE conversation_id=?",(rs,n)->rs.getString(1),cid);
        String start=starts.isEmpty()?null:starts.getFirst();long floor=0;
        if(start!=null){var ids=jdbc.query("SELECT rowid FROM messages WHERE conversation_id=? AND id=?",(rs,n)->rs.getLong(1),cid,start);if(ids.isEmpty())return List.of();floor=ids.getFirst();}
        long ceiling=Long.MAX_VALUE;
        if(currentEventId!=null){var ids=jdbc.query("SELECT rowid FROM messages WHERE conversation_id=? AND platform_message_id=?",(rs,n)->rs.getLong(1),cid,currentEventId);if(ids.isEmpty())throw new SecurityException("history current event is not in this conversation");ceiling=ids.getFirst();}
        var rows=jdbc.queryForList("""
            SELECT * FROM (
              SELECT 'USER' AS role,p.platform_user_id AS speaker,COALESCE(m.context_speaker_name,p.platform_user_id) AS name,
                m.created_at AS at,m.platform_message_id AS message_id,substr(m.text,1,4000) AS content,
                NULL AS legacy_json,NULL AS logical_id,NULL AS turn_id,m.rowid AS anchor,0 AS role_order,0 AS ordinal
              FROM messages m JOIN persons p ON p.id=m.actor_person_id
              JOIN inbound_turn_executions i ON i.conversation_id=m.conversation_id AND i.incoming_event_id=m.platform_message_id AND i.platform=m.platform
              WHERE m.conversation_id=? AND m.is_self=0 AND m.message_type='message' AND m.context_generation>=?
                AND m.rowid>=? AND m.rowid<? AND length(trim(m.text))>0 AND trim(m.text)<>'/reset'
              UNION ALL
              SELECT 'ASSISTANT','bot','机器人',o.updated_at,o.provider_message_id,substr(o.context_text,1,4000),
                COALESCE(t.output_json,p.output_json),o.logical_message_id,o.turn_id,COALESCE(m.rowid,0),1,o.rowid
              FROM onebot_outbound_executions o
              LEFT JOIN inbound_turn_executions i ON i.turn_id=o.turn_id AND i.conversation_id=o.conversation_id
              LEFT JOIN proactive_attempts p ON p.turn_id=o.turn_id AND p.conversation_id=o.conversation_id AND p.status='SENT'
              LEFT JOIN messages m ON m.conversation_id=o.conversation_id AND m.platform_message_id=COALESCE(i.incoming_event_id,p.source_message_id)
              LEFT JOIN durable_turn_executions t ON t.turn_id=o.turn_id AND t.conversation_id=o.conversation_id
              WHERE o.conversation_id=? AND o.status='SUCCESS' AND o.generation>=?
                AND COALESCE(m.rowid,0)>=? AND COALESCE(m.rowid,0)<?
                AND (o.context_text IS NOT NULL OR (COALESCE(t.output_json,p.output_json) IS NOT NULL AND o.logical_message_id LIKE o.turn_id||':%'))
            ) ORDER BY anchor DESC,role_order DESC,ordinal DESC LIMIT ?
            """,cid,generation,floor,ceiling,cid,generation,floor,ceiling,limit);
        var result=new ArrayList<ContextMessage>();
        for(var row:rows){
            String content=(String)row.get("content");
            if(content==null){try{
                String logical=(String)row.get("logical_id"),turn=(String)row.get("turn_id"),json=(String)row.get("legacy_json");
                if(json.length()>65536)continue;int part=Integer.parseInt(logical.substring(turn.length()+1));
                var values=mapper.readTree(json);if(!values.isArray()||part<0||part>=values.size()||!values.get(part).isTextual())continue;
                content=values.get(part).asText();
            }catch(Exception invalid){continue;}}
            if(content.isBlank())continue;if(content.length()>4000)content=content.substring(0,4000);
            try{result.add(new ContextMessage(ContextMessage.Role.valueOf((String)row.get("role")),(String)row.get("speaker"),(String)row.get("name"),Instant.parse((String)row.get("at")),(String)row.get("message_id"),content));}catch(IllegalArgumentException invalid){/* malformed evidence is not promoted into history */}
        }
        Collections.reverse(result);return List.copyOf(result);
    }
    public int recentUnknownCount(UUID conversation){
        String cid=conversation.toString();
        return jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT status FROM onebot_outbound_executions WHERE conversation_id=? AND generation>=(SELECT MAX(g) FROM (SELECT generation g FROM conversation_reset_controls WHERE conversation_id=? UNION ALL SELECT history_reset_generation g FROM session_state WHERE conversation_id=? UNION ALL SELECT 0)) ORDER BY rowid DESC LIMIT 16) WHERE status='UNKNOWN'",Integer.class,cid,cid,cid);
    }
    public void startAt(UUID conversation,String eventId){
        var ids=jdbc.query("SELECT id FROM messages WHERE conversation_id=? AND platform_message_id=?",(rs,n)->rs.getString(1),conversation.toString(),eventId);
        if(ids.isEmpty())throw new SecurityException("history boundary is not in this conversation");
        jdbc.update("INSERT INTO session_state(conversation_id,session_id,status,updated_at,history_start_message_id) VALUES(?,?,'ACTIVE',?,?) ON CONFLICT(conversation_id) DO UPDATE SET history_start_message_id=excluded.history_start_message_id",conversation.toString(),conversation+"-context",Instant.now().toString(),ids.getFirst());
    }
}
