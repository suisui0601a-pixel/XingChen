package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.core.agent.AgentDecision;
import online.wanan.xingchen.core.context.ContextPackage;
import online.wanan.xingchen.core.identity.ResolvedActorContext;
import online.wanan.xingchen.core.memory.MemoryPolicy;
import online.wanan.xingchen.core.agent.ToolResult;

public record OrchestrationResult(boolean duplicate,boolean ignored,boolean rolledOver,
                                  ResolvedActorContext actor,ContextPackage context,AgentDecision decision,
                                  MemoryPolicy.Evaluation memoryEvaluation,ToolResult toolResult) {}
