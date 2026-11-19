package online.wanan.xingchen.core.memory;

import java.util.UUID;

/** Read-only integration boundary. The built-in retriever remains the default.
 * A future external adapter must preserve caller/conversation visibility and the
 * context budget. This port does not enable a remote service or grant write access. */
@FunctionalInterface
public interface MemoryRecallPort {
    RelevantMemorySet retrieve(RequestContext request,String currentMessage,UUID conversation,UUID actor,int tokenBudget);
}
