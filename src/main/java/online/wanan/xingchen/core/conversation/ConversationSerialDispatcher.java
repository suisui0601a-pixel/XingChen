package online.wanan.xingchen.core.conversation;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** FIFO within a conversation, bounded globally, and parallel across conversations. */
final class ConversationSerialDispatcher implements AutoCloseable {
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "xingchen-social-runtime"); t.setDaemon(true); return t;
    });
    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();
    private final Semaphore capacity = new Semaphore(1024);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger pending = new AtomicInteger();

    void execute(String key, Runnable task) {
        if (closed.get() || !capacity.tryAcquire()) throw new RejectedExecutionException("social event queue is full or closed");
        pending.incrementAndGet();
        Lane[] selected = new Lane[1]; boolean[] schedule = new boolean[1];
        lanes.compute(key, (ignored, current) -> {
            Lane lane = current == null ? new Lane() : current; selected[0] = lane;
            synchronized (lane) { lane.tasks.add(task); schedule[0] = !lane.running; if (schedule[0]) lane.running = true; }
            return lane;
        });
        if (schedule[0]) submit(key, selected[0]);
    }

    private void submit(String key, Lane lane) {
        try { workers.execute(() -> runNext(key, lane)); }
        catch (RejectedExecutionException ex) { synchronized (lane) { lane.running = false; } throw ex; }
    }

    private void runNext(String key, Lane lane) {
        Runnable task;
        synchronized (lane) { task = lane.tasks.poll(); }
        if(task!=null)pending.decrementAndGet();
        try { if (task != null) task.run(); }
        finally {
            capacity.release();
            boolean[] more = new boolean[1];
            lanes.compute(key, (ignored, current) -> {
                synchronized (lane) { more[0] = !lane.tasks.isEmpty(); if (more[0]) return lane; lane.running = false; return current == lane ? null : current; }
            });
            if (more[0]) submit(key, lane);
        }
    }

    int queuedTaskCount(){return pending.get();}
    int activeConversationCount(){return (int)lanes.values().stream().filter(lane->{synchronized(lane){return lane.running;}}).count();}
    int queuedConversationCount(){return (int)lanes.values().stream().filter(lane->{synchronized(lane){return !lane.tasks.isEmpty();}}).count();}
    int queuedFor(String key){Lane lane=lanes.get(key);if(lane==null)return 0;synchronized(lane){return lane.tasks.size();}}
    boolean active(String key){Lane lane=lanes.get(key);if(lane==null)return false;synchronized(lane){return lane.running;}}

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        workers.shutdownNow();
        lanes.values().forEach(lane -> { synchronized (lane) { int removed=lane.tasks.size();capacity.release(removed);pending.addAndGet(-removed);lane.tasks.clear(); } });
        lanes.clear();
    }
    private static final class Lane { final ArrayDeque<Runnable> tasks = new ArrayDeque<>(); boolean running; }
}
