package online.wanan.xingchen.core.agent;

/** Atomically claims a provider call before execution; a duplicate claim must never execute the tool again. */
public interface ToolExecutionLedger {
    boolean claim(String eventId, String callId);
    default boolean requiresStableEventId() { return false; }
}
