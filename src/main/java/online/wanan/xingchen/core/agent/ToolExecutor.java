package online.wanan.xingchen.core.agent;

import java.util.Set;

public interface ToolExecutor {
    interface TurnScope extends AutoCloseable { @Override void close(); }
    /** Owned by the synchronous AgentExecutor invocation, including exceptional exits. */
    default TurnScope beginTurn(AgentRequest request) { return () -> {}; }
    ToolResult execute(ToolCall call,AgentRequest request,Set<AgentCapability> capabilities);
}
