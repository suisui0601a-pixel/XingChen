package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.memory.MemoryCandidate;
import java.util.List;

public record ModelResponse(AgentDecision decision,ModelUsage usage,MemoryCandidate memoryCandidate,
                            List<ModelToolCall> toolCalls,ModelFinishReason finishReason) {
    public ModelResponse(AgentDecision decision,ModelUsage usage,MemoryCandidate memoryCandidate){this(decision,usage,memoryCandidate,List.of(),ModelFinishReason.UNKNOWN);}
    public ModelResponse { toolCalls=toolCalls==null?List.of():List.copyOf(toolCalls);finishReason=finishReason==null?ModelFinishReason.UNKNOWN:finishReason; }
}
