package online.wanan.xingchen.adapter.dsh;

/** Cancellation handle for one DSH session-bound follow stream. */
public interface DshFollowSubscription extends AutoCloseable {
    boolean isOpen();
    java.util.concurrent.CompletionStage<Void> completion();
    @Override void close();
}
