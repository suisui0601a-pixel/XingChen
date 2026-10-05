package online.wanan.xingchen.core.agent;

import java.time.Duration;
import java.util.concurrent.*;

public final class ExecutorScheduler implements Scheduler,AutoCloseable {
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"xingchen-social-scheduler");t.setDaemon(true);return t;});
    @Override public Cancellable schedule(Duration delay,Runnable task){ScheduledFuture<?> f=executor.schedule(task,Math.max(0,delay.toMillis()),TimeUnit.MILLISECONDS);return new Cancellable(){public boolean cancel(){return f.cancel(false);}public boolean isCancelled(){return f.isCancelled();}};}
    @Override public void close(){executor.shutdownNow();}
}
