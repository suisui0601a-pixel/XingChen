package online.wanan.xingchen.core.agent;

import java.util.Set;

public interface ToolExecutor {
    interface TurnScope extends AutoCloseable { @Override void close(); }
    /** Owned by the synchronous AgentExecutor invocation, including exceptional exits. */
    default TurnScope beginTurn(AgentRequest request) { return () -> {}; }
    /** Optional, permission-scoped media input; invoked once per admitted turn, not once per tool round. */
    default ModelInputAttachments modelInputs(AgentRequest request){return ModelInputAttachments.NONE;}
    ToolResult execute(ToolCall call,AgentRequest request,Set<AgentCapability> capabilities);
}
