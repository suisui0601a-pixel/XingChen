package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.conversation.OutboundExecutionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SqliteOutboundExecutionRepository {
    private final JdbcTemplate jdbc;
    public SqliteOutboundExecutionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional public boolean reserve(OutboundExecutionRecord record){
        return jdbc.update("INSERT OR IGNORE INTO onebot_outbound_executions(execution_key,conversation_id,turn_id,generation,logical_message_id,status,provider_message_id,failure_reason,created_at,updated_at,dispatch_started_at) VALUES(?,?,?,?,?,'PENDING',NULL,NULL,?,?,NULL)",
                record.executionKey(),record.conversationId().toString(),record.turnId(),record.generation(),record.logicalMessageId(),str(record.createdAt()),str(record.updatedAt()))==1;
    }
    @Transactional public boolean startDispatch(String key,Instant at){
        return jdbc.update("UPDATE onebot_outbound_executions SET dispatch_started_at=?,updated_at=? WHERE execution_key=? AND status='PENDING' AND dispatch_started_at IS NULL",str(at),str(at),key)==1;
    }
    @Transactional public boolean complete(String key,OutboundExecutionRecord.Status status,String providerMessageId,String failureReason,Instant at){
        if(status==OutboundExecutionRecord.Status.PENDING)throw new IllegalArgumentException("terminal outbound status is required");
        return jdbc.update("UPDATE onebot_outbound_executions SET status=?,provider_message_id=?,failure_reason=?,updated_at=? WHERE execution_key=? AND status='PENDING'",status.name(),providerMessageId,failureReason,str(at),key)==1;
    }
    @Transactional public int recoverStartedPending(Instant at){
        return jdbc.update("UPDATE onebot_outbound_executions SET status='UNKNOWN',failure_reason='process-restarted-after-dispatch-start',updated_at=? WHERE status='PENDING' AND dispatch_started_at IS NOT NULL",str(at));
    }
    public Optional<OutboundExecutionRecord> find(String key){
        return jdbc.query("SELECT * FROM onebot_outbound_executions WHERE execution_key=?",(rs,n)->new OutboundExecutionRecord(
                rs.getString("execution_key"),UUID.fromString(rs.getString("conversation_id")),rs.getString("turn_id"),rs.getLong("generation"),
                rs.getString("logical_message_id"),OutboundExecutionRecord.Status.valueOf(rs.getString("status")),rs.getString("provider_message_id"),
                rs.getString("failure_reason"),Instant.parse(rs.getString("created_at")),Instant.parse(rs.getString("updated_at")),instant(rs.getString("dispatch_started_at"))),key).stream().findFirst();
    }
    private static String str(Instant value){return value.toString();}
    private static Instant instant(String value){return value==null?null:Instant.parse(value);}
}
