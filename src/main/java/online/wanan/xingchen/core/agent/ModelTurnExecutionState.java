package online.wanan.xingchen.core.agent;

/** Safety state for deciding whether a model turn may be retried after transport interruption. */
public final class ModelTurnExecutionState {
    public enum State { NOT_STARTED, STREAMING_NO_EFFECT, MODEL_OUTPUT_RECEIVED, TOOL_PROPOSED, TOOL_EXECUTED, OUTPUT_EMITTED, OUTBOUND_OUTPUT_SENT, UNCERTAIN }
    private State state=State.NOT_STARTED;
    private final TurnStateObserver observer;
    public ModelTurnExecutionState(){this(null);}
    public ModelTurnExecutionState(TurnStateObserver observer){this.observer=observer;}
    public synchronized State state(){return state;}
    public synchronized void begin(){if(state!=State.NOT_STARTED)throw new IllegalStateException("turn already started");set(State.STREAMING_NO_EFFECT);}
    /** Model deltas are buffered internally; receiving text alone is not an externally visible effect. */
    public synchronized void modelOutputReceived(){if(state==State.UNCERTAIN||state==State.NOT_STARTED||state==State.OUTBOUND_OUTPUT_SENT)throw new IllegalStateException("model output in invalid turn state");if(state!=State.TOOL_PROPOSED&&state!=State.TOOL_EXECUTED)set(State.MODEL_OUTPUT_RECEIVED);}
    public synchronized void toolProposed(){if(state==State.TOOL_PROPOSED)return;if(state!=State.STREAMING_NO_EFFECT&&state!=State.MODEL_OUTPUT_RECEIVED&&state!=State.TOOL_EXECUTED)throw new IllegalStateException("tool proposal outside a valid stream");set(State.TOOL_PROPOSED);}
    public synchronized void toolExecuted(){if(state!=State.TOOL_PROPOSED)throw new IllegalStateException("tool execution without proposal");set(State.TOOL_EXECUTED);}
    public synchronized void outputEmitted(){if(state==State.UNCERTAIN||state==State.NOT_STARTED)throw new IllegalStateException("output in invalid turn state");set(State.OUTPUT_EMITTED);}
    /** Call only after the external transport confirms a user-visible send. */
    public synchronized void outboundOutputSent(){if(state==State.UNCERTAIN||state==State.NOT_STARTED)throw new IllegalStateException("outbound output in invalid turn state");set(State.OUTBOUND_OUTPUT_SENT);}
    /** An unconfirmed send may have happened remotely; fail closed. */
    public synchronized void outboundOutputUnknown(){if(state==State.NOT_STARTED)throw new IllegalStateException("unknown outbound result before turn start");set(State.UNCERTAIN);}
    public synchronized boolean interrupted(){if(mayAutoRetry())return true;set(State.UNCERTAIN);return false;}
    public synchronized boolean mayAutoRetry(){return state==State.NOT_STARTED||state==State.STREAMING_NO_EFFECT||state==State.MODEL_OUTPUT_RECEIVED||state==State.TOOL_PROPOSED;}
    public synchronized void resetForSafeRetry(){if(!mayAutoRetry())throw new IllegalStateException("unsafe model turn retry");set(State.NOT_STARTED);}
    private void set(State next){state=next;if(observer!=null)observer.changed(next);}
}
