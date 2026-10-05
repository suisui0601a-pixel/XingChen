package online.wanan.xingchen.storage;

/** Whether a newly archived event may advance conversational processing. */
public record EventPersistResult(Status status) {
    public enum Status { NEW_IN_ORDER, LATE_ARCHIVED, UNORDERABLE_ARCHIVED, DUPLICATE }
    public boolean shouldProcess() { return status == Status.NEW_IN_ORDER; }
    public boolean isDuplicate() { return status == Status.DUPLICATE; }
}
