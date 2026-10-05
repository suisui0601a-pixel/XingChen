package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.context.ContextPackage;
import online.wanan.xingchen.core.identity.ResolvedActorContext;
import java.util.Set;

public record AgentRequest(ResolvedActorContext actor,ContextPackage context,Set<AgentCapability> availableCapabilities,String eventId,String turnId,TurnStateObserver turnStateObserver,CancellationToken cancellationToken) {
    public AgentRequest(ResolvedActorContext actor,ContextPackage context,Set<AgentCapability> availableCapabilities,String eventId,String turnId,TurnStateObserver observer){this(actor,context,availableCapabilities,eventId,turnId,observer,new CancellationToken());}
    public AgentRequest(ResolvedActorContext actor,ContextPackage context,Set<AgentCapability> availableCapabilities,String eventId,String turnId){this(actor,context,availableCapabilities,eventId,turnId,null,new CancellationToken());}
    public AgentRequest(ResolvedActorContext actor,ContextPackage context,Set<AgentCapability> availableCapabilities,String eventId){this(actor,context,availableCapabilities,eventId,eventId,null,new CancellationToken());}
    public AgentRequest(ResolvedActorContext actor,ContextPackage context,Set<AgentCapability> availableCapabilities){this(actor,context,availableCapabilities,null,null,null,new CancellationToken());}
    public AgentRequest { availableCapabilities=Set.copyOf(availableCapabilities);cancellationToken=cancellationToken==null?new CancellationToken():cancellationToken; }
}
