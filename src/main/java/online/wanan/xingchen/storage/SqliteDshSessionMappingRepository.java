package online.wanan.xingchen.storage;

import online.wanan.xingchen.adapter.dsh.DshSessionMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Repository
public class SqliteDshSessionMappingRepository {
    private final JdbcTemplate jdbc;
    public SqliteDshSessionMappingRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public DshSessionMapping current(UUID conversation){return jdbc.query("SELECT * FROM dsh_session_mappings WHERE conversation_id=? AND status='CURRENT'",(rs,n)->map(rs),conversation.toString()).stream().findFirst().orElse(null);}
    public DshSessionMapping currentBySession(String sessionId){return jdbc.query("SELECT * FROM dsh_session_mappings WHERE session_id=? AND status='CURRENT'",(rs,n)->map(rs),sessionId).stream().findFirst().orElse(null);}
    public long nextGeneration(UUID conversation){return jdbc.queryForObject("SELECT COALESCE(MAX(generation),0)+1 FROM dsh_session_mappings WHERE conversation_id=?",Long.class,conversation.toString());}
    @Transactional public void save(DshSessionMapping mapping){jdbc.update("UPDATE dsh_session_mappings SET status='RETIRED',updated_at=? WHERE conversation_id=? AND status='CURRENT'",mapping.updatedAt().toString(),mapping.conversationId().toString());jdbc.update("INSERT INTO dsh_session_mappings(id,conversation_id,workspace_id,session_id,mode,preset,model,policy_hash,generation,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'CURRENT',?,?)",mapping.id().toString(),mapping.conversationId().toString(),mapping.workspaceId(),mapping.sessionId(),mapping.mode(),mapping.preset(),mapping.model(),mapping.policyHash(),mapping.generation(),mapping.createdAt().toString(),mapping.updatedAt().toString());}
    @Transactional public void retire(UUID conversation,Instant at){jdbc.update("UPDATE dsh_session_mappings SET status='RETIRED',updated_at=? WHERE conversation_id=? AND status='CURRENT'",at.toString(),conversation.toString());}
    private static DshSessionMapping map(java.sql.ResultSet rs)throws java.sql.SQLException{return new DshSessionMapping(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("conversation_id")),rs.getString("workspace_id"),rs.getString("session_id"),rs.getString("mode"),rs.getString("preset"),rs.getString("model"),rs.getString("policy_hash"),rs.getLong("generation"),DshSessionMapping.Status.valueOf(rs.getString("status")),Instant.parse(rs.getString("created_at")),Instant.parse(rs.getString("updated_at")));}
}
