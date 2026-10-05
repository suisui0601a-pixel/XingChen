package online.wanan.xingchen.core.agent;

import java.util.Set;

public interface ToolExecutor {
    ToolResult execute(ToolCall call,AgentRequest request,Set<AgentCapability> capabilities);
}
