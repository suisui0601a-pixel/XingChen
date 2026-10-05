package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.core.identity.ResolvedActorContext;
import online.wanan.xingchen.core.model.PlatformEvent;
import online.wanan.xingchen.storage.EventPersistResult;

/** Identity-resolved, durably ordered input shared by lifecycle policy and the orchestrator. */
public record PreparedConversationEvent(PlatformEvent event, ResolvedActorContext actor, EventPersistResult persistence) {
    public boolean mayStartTurn(){return persistence.shouldProcess();}
}
