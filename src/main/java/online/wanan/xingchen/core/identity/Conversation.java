package online.wanan.xingchen.core.identity;

import online.wanan.xingchen.core.model.ConversationIdentity;
import java.util.UUID;

public record Conversation(UUID id, ConversationIdentity identity) {}
