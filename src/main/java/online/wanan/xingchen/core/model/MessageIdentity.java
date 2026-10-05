package online.wanan.xingchen.core.model;

public record MessageIdentity(String platformMessageId, String replyToMessageId) {
    public MessageIdentity { if (platformMessageId == null || platformMessageId.isBlank()) throw new IllegalArgumentException("message id is required"); }
}
