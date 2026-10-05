package online.wanan.xingchen.core.agent;

/** Narrow lifecycle observation points used to deterministically test reset fences. */
public interface TurnBoundaryObserver {
    TurnBoundaryObserver NOOP = new TurnBoundaryObserver() {};
    default void toolProposed(String turnId, ModelToolCall call) {}
    default void toolExecuted(String turnId, ModelToolCall call) {}
    default void outputPersistedBeforeDispatch(String turnId) {}
    default void outboundDispatchInvoked(String turnId) {}
}
