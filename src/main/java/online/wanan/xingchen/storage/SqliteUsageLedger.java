package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.agent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;

@Repository public class SqliteUsageLedger implements UsageLedger {
    private final JdbcTemplate jdbc;public SqliteUsageLedger(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public void record(UUID conversationId,String turnId,ModelUsage u){if(u==null)return;jdbc.update("INSERT INTO token_usage(id,provider,conversation_id,turn_id,model,usage_time,input_tokens,cache_hit_tokens,cache_miss_tokens,output_tokens,reasoning_tokens,cost,duration_ms,reasoning_available) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),u.provider(),conversationId==null?null:conversationId.toString(),turnId,u.model(),Instant.now().toString(),u.inputTokens(),u.cacheHitTokens(),u.cacheMissTokens(),u.outputTokens(),u.reasoningTokens(),0.0,u.durationMillis(),u.reasoningAvailable()?1:0);}
}
