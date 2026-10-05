package online.wanan.xingchen.core.agent;

import java.time.Duration;

public interface Scheduler {
    Cancellable schedule(Duration delay,Runnable task);
    interface Cancellable { boolean cancel();boolean isCancelled(); }
}
