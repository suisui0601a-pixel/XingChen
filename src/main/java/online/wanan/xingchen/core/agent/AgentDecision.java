package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.memory.MemoryCandidate;
import java.util.*;

public record AgentDecision(DecisionType type,List<String> messages,ToolCall toolCall,MemoryCandidate memoryCandidate) {
    public AgentDecision { Objects.requireNonNull(type);messages=messages==null?List.of():List.copyOf(messages); }
    public static AgentDecision noReply(){return new AgentDecision(DecisionType.NO_REPLY,List.of(),null,null);}
    public static AgentDecision waitForNextMessage(){return new AgentDecision(DecisionType.WAIT,List.of(),null,null);}
    public static AgentDecision text(String value){return new AgentDecision(DecisionType.TEXT_REPLY,List.of(value),null,null);}
    public static AgentDecision multi(List<String> values){return new AgentDecision(DecisionType.MULTI_REPLY,values,null,null);}
    public static AgentDecision tool(ToolCall call){return new AgentDecision(DecisionType.TOOL_CALL,List.of(),call,null);}
    public AgentDecision withMemory(MemoryCandidate candidate){return new AgentDecision(type,messages,toolCall,candidate);}
}
