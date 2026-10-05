package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.context.ContextDiagnostics;
import java.time.Instant;
import java.util.*;

/** Redacted development trace: identifiers/counts only; never prompt text, tool args, credentials or cookies. */
public record AgentTrace(UUID conversationId,String eventId,String actorId,List<UUID> memoryIds,
                         ContextDiagnostics diagnostics,int modelCallCount,String provider,String model,
                         List<String> toolNames,DecisionType decision,int outgoingActionCount,Instant createdAt) {
    public AgentTrace {memoryIds=memoryIds==null?List.of():List.copyOf(memoryIds);toolNames=toolNames==null?List.of():List.copyOf(toolNames);createdAt=createdAt==null?Instant.now():createdAt;}
}
