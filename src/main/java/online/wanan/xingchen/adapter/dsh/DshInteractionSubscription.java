package online.wanan.xingchen.adapter.dsh;

import java.util.concurrent.CompletionStage;

/** Owns the independent $events logical stream and its current ready generation. */
public interface DshInteractionSubscription extends AutoCloseable {
    boolean isOpen();
    String currentClientId();
    CompletionStage<Void> completion();
    @Override void close();
}
