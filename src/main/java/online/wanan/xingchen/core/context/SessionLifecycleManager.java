package online.wanan.xingchen.core.context;

public interface SessionLifecycleManager {
    SessionLifecycleStatus evaluate(int estimatedTokens);
    SessionHandoff loadHandoff(String conversationId);
    void saveHandoff(String conversationId, SessionHandoff handoff);
    default void recordContextSize(String conversationId, int estimatedTokens) { }
}
