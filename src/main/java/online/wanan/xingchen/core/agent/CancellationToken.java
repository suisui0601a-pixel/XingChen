package online.wanan.xingchen.core.agent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CancellationToken {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private volatile BooleanSupplier generationCurrent=()->true;
    public synchronized boolean cancel(){if(!cancelled.compareAndSet(false,true))return false;for(Runnable listener:listeners)try{listener.run();}catch(RuntimeException ignored){}listeners.clear();return true;}
    public synchronized long invalidateGenerationAndCancel(LongSupplier invalidator){long next=invalidator.getAsLong();cancel();return next;}
    public synchronized void generationFence(BooleanSupplier current){generationCurrent=current==null?()->true:current;}
    public synchronized <T> T withCurrentGeneration(Supplier<T> action){throwIfCancelled();if(!generationCurrent.getAsBoolean())throw new java.util.concurrent.CancellationException("stale conversation generation");return action.get();}
    public synchronized void runWithCurrentGeneration(Runnable action){withCurrentGeneration(()->{action.run();return null;});}
    public boolean isCancelled(){return cancelled.get();}
    public void throwIfCancelled(){if(isCancelled())throw new java.util.concurrent.CancellationException("model request cancelled");}
    public AutoCloseable onCancel(Runnable listener){if(listener==null)throw new IllegalArgumentException("listener is required");if(cancelled.get()){listener.run();return ()->{};}listeners.add(listener);if(cancelled.get()&&listeners.remove(listener))listener.run();return ()->listeners.remove(listener);}
}
