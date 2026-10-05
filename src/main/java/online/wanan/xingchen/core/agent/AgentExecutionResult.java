package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.context.ContextPackage;
import online.wanan.xingchen.core.memory.MemoryCandidate;
import java.util.List;

public record AgentExecutionResult(AgentDecision decision,ModelUsage totalUsage,int toolSteps,
                                  ContextPackage finalContext,List<ModelToolResult> toolResults,
                                  MemoryCandidate memoryCandidate,String stopReason,ModelTurnExecutionState turnState) {
    public AgentExecutionResult(AgentDecision decision,ModelUsage totalUsage,int toolSteps,ContextPackage finalContext,List<ModelToolResult> toolResults,MemoryCandidate memoryCandidate,String stopReason){this(decision,totalUsage,toolSteps,finalContext,toolResults,memoryCandidate,stopReason,null);}
    public AgentExecutionResult { toolResults=toolResults==null?List.of():List.copyOf(toolResults);stopReason=stopReason==null?"completed":stopReason; }
}
