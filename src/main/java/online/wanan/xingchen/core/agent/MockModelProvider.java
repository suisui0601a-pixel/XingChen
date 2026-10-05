package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.context.ContextPackage;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic fixture provider. It never performs network I/O. */
public final class MockModelProvider implements ModelProvider {
    private final Queue<Fixture> fixtures=new ConcurrentLinkedQueue<>();
    private final Map<String,Queue<Fixture>> conversationFixtures=new ConcurrentHashMap<>();
    private final List<ContextPackage> received=new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<ModelRequest> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicInteger activeCalls=new AtomicInteger(),maxConcurrentCalls=new AtomicInteger();
    public MockModelProvider enqueue(ModelResponse response){fixtures.add(new Fixture(response,null,null,null));return this;}
    public BlockingFixture enqueueBlocked(ModelResponse response){var blocked=new BlockingFixture();fixtures.add(new Fixture(response,blocked.started,blocked.release,blocked.returned));return blocked;}
    public MockModelProvider enqueueFor(String conversationId,ModelResponse response){conversationFixtures.computeIfAbsent(conversationId,k->new ConcurrentLinkedQueue<>()).add(new Fixture(response,null,null,null));return this;}
    public BlockingFixture enqueueBlockedFor(String conversationId,ModelResponse response){var blocked=new BlockingFixture();conversationFixtures.computeIfAbsent(conversationId,k->new ConcurrentLinkedQueue<>()).add(new Fixture(response,blocked.started,blocked.release,blocked.returned));return blocked;}
    @Override public ModelResponse complete(ModelRequest request){int active=activeCalls.incrementAndGet();maxConcurrentCalls.accumulateAndGet(active,Math::max);received.add(request.context());requests.add(request);try{String key=request.context().conversationId();Queue<Fixture> routed=conversationFixtures.get(key);Fixture fixture=routed==null?null:routed.poll();if(fixture==null)fixture=fixtures.poll();if(fixture==null)throw new IllegalStateException("no mock model fixture queued for conversation "+key);if(fixture.started()!=null){fixture.started().countDown();try{if(!fixture.release().await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("mock model barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("mock model interrupted");}finally{fixture.returned().countDown();}}return fixture.response();}finally{activeCalls.decrementAndGet();}}
    public List<ContextPackage> receivedContexts(){return List.copyOf(received);}
    public List<ModelRequest> requests(){return List.copyOf(requests);}
    public int maxConcurrentCalls(){return maxConcurrentCalls.get();}
    private record Fixture(ModelResponse response,java.util.concurrent.CountDownLatch started,java.util.concurrent.CountDownLatch release,java.util.concurrent.CountDownLatch returned){}
    public static final class BlockingFixture {
        private final java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1),returned=new java.util.concurrent.CountDownLatch(1);
        public boolean awaitStarted(long timeout,java.util.concurrent.TimeUnit unit)throws InterruptedException{return started.await(timeout,unit);}
        public void release(){release.countDown();}
        public boolean awaitReturned(long timeout,java.util.concurrent.TimeUnit unit)throws InterruptedException{return returned.await(timeout,unit);}
    }
}
