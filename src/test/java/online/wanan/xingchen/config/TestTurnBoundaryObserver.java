package online.wanan.xingchen.config;

import online.wanan.xingchen.core.agent.ModelToolCall;
import online.wanan.xingchen.core.agent.TurnBoundaryObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** One-shot latch gates at real runtime boundaries; inert unless a test arms one. */
public final class TestTurnBoundaryObserver implements TurnBoundaryObserver {
    public enum Phase { OUTPUT_PERSISTED, TOOL_PROPOSED, TOOL_EXECUTED }
    public static final class Gate {
        private final CountDownLatch reached=new CountDownLatch(1),release=new CountDownLatch(1);
        public boolean awaitReached(long timeout,TimeUnit unit)throws InterruptedException{return reached.await(timeout,unit);}
        public void release(){release.countDown();}
        private void block(){reached.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test boundary was not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("test boundary interrupted");}}
    }
    private final AtomicReference<Armed> armed=new AtomicReference<>();
    private final AtomicInteger toolProposals=new AtomicInteger(),toolExecutions=new AtomicInteger(),dispatches=new AtomicInteger();
    public int toolProposalCount(){return toolProposals.get();}public int toolExecutionCount(){return toolExecutions.get();}public int dispatchCount(){return dispatches.get();}
    public Gate arm(Phase phase){Gate gate=new Gate();if(!armed.compareAndSet(null,new Armed(phase,gate)))throw new IllegalStateException("a test boundary is already armed");return gate;}
    @Override public void outputPersistedBeforeDispatch(String turnId){block(Phase.OUTPUT_PERSISTED);}
    @Override public void toolProposed(String turnId,ModelToolCall call){toolProposals.incrementAndGet();block(Phase.TOOL_PROPOSED);}
    @Override public void toolExecuted(String turnId,ModelToolCall call){toolExecutions.incrementAndGet();block(Phase.TOOL_EXECUTED);}
    @Override public void outboundDispatchInvoked(String turnId){dispatches.incrementAndGet();}
    private void block(Phase phase){Armed candidate=armed.get();if(candidate!=null&&candidate.phase()==phase&&armed.compareAndSet(candidate,null))candidate.gate().block();}
    private record Armed(Phase phase,Gate gate){}
}
