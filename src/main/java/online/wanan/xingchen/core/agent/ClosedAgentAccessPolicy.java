package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.model.ConversationType;

/** Fails closed unless a confidently resolved owner is in a private closed-agent conversation. */
public final class ClosedAgentAccessPolicy {
    public boolean allows(ConversationType conversationType,boolean owner,String mode,boolean identityCertain){
        return conversationType==ConversationType.PRIVATE&&owner&&identityCertain&&"CLOSED_AGENT".equals(mode);
    }
}
