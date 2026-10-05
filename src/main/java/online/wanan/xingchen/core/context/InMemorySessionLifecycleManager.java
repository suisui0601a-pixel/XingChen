package online.wanan.xingchen.core.context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionLifecycleManager implements SessionLifecycleManager {
    private final Map<String,SessionHandoff> handoffs=new ConcurrentHashMap<>();
    private final ContextBuilder builder;
    public InMemorySessionLifecycleManager(ContextBuilder builder){this.builder=builder;}
    @Override public SessionLifecycleStatus evaluate(int tokens){return builder.lifecycle(tokens);}
    @Override public SessionHandoff loadHandoff(String conversationId){return handoffs.get(conversationId);}
    @Override public void saveHandoff(String conversationId,SessionHandoff handoff){handoffs.put(conversationId,handoff);}
}
