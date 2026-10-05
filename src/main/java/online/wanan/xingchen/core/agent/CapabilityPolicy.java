package online.wanan.xingchen.core.agent;

import java.util.*;

public final class CapabilityPolicy {
    private static final Set<AgentCapability> SOCIAL=Set.of(AgentCapability.QQ_READ,AgentCapability.QQ_SEND,AgentCapability.QQ_REPLY,AgentCapability.QQ_WAIT,AgentCapability.QQ_POKE,AgentCapability.MEMORY_READ,AgentCapability.MEMORY_WRITE,AgentCapability.MEMORY_ADDRESS,AgentCapability.STICKER_READ,AgentCapability.STICKER_SEND,AgentCapability.STICKER_WRITE,AgentCapability.SLANG_READ,AgentCapability.SLANG_WRITE);
    private CapabilityPolicy(){}
    public static Set<AgentCapability> capabilities(AgentRole role,boolean ownerAuthorized){if(role==AgentRole.SOCIAL)return SOCIAL;if(role==AgentRole.CLOSED_AGENT&&ownerAuthorized)return SOCIAL;return Set.of();}
    public static boolean allows(AgentRole role,boolean ownerAuthorized,AgentCapability c){return capabilities(role,ownerAuthorized).contains(c);}
}
