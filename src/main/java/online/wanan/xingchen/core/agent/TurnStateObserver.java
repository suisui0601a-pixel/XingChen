package online.wanan.xingchen.core.agent;

@FunctionalInterface
public interface TurnStateObserver {
    void changed(ModelTurnExecutionState.State state);
}
