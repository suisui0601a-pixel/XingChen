package online.wanan.xingchen.adapter.dsh;

import online.wanan.xingchen.storage.SqliteDshSessionMappingRepository;
import java.time.Clock;
import java.util.*;
import online.wanan.xingchen.core.agent.ClosedAgentAccessPolicy;
import online.wanan.xingchen.core.identity.ResolvedActorContext;
import online.wanan.xingchen.core.model.ConversationType;

/** Conversation-to-DSH mapping. A permission/mode/preset change never reuses the prior session. */
public final class DshSessionService {
    private final SqliteDshSessionMappingRepository mappings;private final DshGateway dsh;private final Clock clock;private final java.nio.file.Path workspaceDirectory;
    public DshSessionService(SqliteDshSessionMappingRepository mappings,DshGateway dsh,Clock clock){this(mappings,dsh,clock,java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath());}
    public DshSessionService(SqliteDshSessionMappingRepository mappings,DshGateway dsh,Clock clock,java.nio.file.Path workspaceDirectory){this.mappings=mappings;this.dsh=dsh;this.clock=clock;this.workspaceDirectory=workspaceDirectory.toAbsolutePath().normalize();}
    private synchronized DshSessionMapping ensure(UUID conversation,String mode,String preset,String model,String policyHash){
        if(mode==null||preset==null||model==null||policyHash==null||mode.isBlank()||preset.isBlank()||model.isBlank()||policyHash.isBlank())throw new IllegalArgumentException("DSH session policy is incomplete");
        var prior=mappings.current(conversation);if(prior!=null&&prior.mode().equals(mode)&&prior.preset().equals(preset)&&prior.model().equals(model)&&prior.policyHash().equals(policyHash))return prior;
        if(prior!=null){dsh.stop(prior.sessionId());dsh.archive(prior.sessionId());mappings.retire(conversation,clock.instant());}
        String workspace=require(dsh.findOrCreateWorkspace(workspaceDirectory.toString()),"workspace");String session=require(dsh.createSession(workspace,mode,preset,model),"session");var now=clock.instant();var created=new DshSessionMapping(UUID.randomUUID(),conversation,workspace,session,mode,preset,model,policyHash,mappings.nextGeneration(conversation),DshSessionMapping.Status.CURRENT,now,now);mappings.save(created);return created;
    }
    /** The closed-agent boundary is fail-closed and is the only actor-facing path to DSH sessions. */
    public synchronized DshSessionMapping ensureClosedAgent(ResolvedActorContext actor,String mode,String preset,String model,String policyHash,boolean identityCertain,ClosedAgentAccessPolicy policy){
        Objects.requireNonNull(actor);Objects.requireNonNull(policy);
        if(!policy.allows(actor.conversation().identity().type(),actor.flags().owner(),mode,identityCertain))
            throw new SecurityException("closed-agent DSH access denied");
        return ensure(actor.conversation().id(),mode,preset,model,policyHash);
    }
    public synchronized void reset(UUID conversation){var prior=mappings.current(conversation);if(prior==null)return;dsh.stop(prior.sessionId());dsh.archive(prior.sessionId());mappings.retire(conversation,clock.instant());}
    public void promptClosedAgent(ResolvedActorContext actor,DshSessionMapping mapping,String text,boolean identityCertain,ClosedAgentAccessPolicy policy){
        Objects.requireNonNull(actor);Objects.requireNonNull(mapping);Objects.requireNonNull(policy);
        if(!policy.allows(actor.conversation().identity().type(),actor.flags().owner(),mapping.mode(),identityCertain)||!mapping.conversationId().equals(actor.conversation().id()))
            throw new SecurityException("closed-agent DSH access denied");
        if(text==null||text.isBlank())throw new IllegalArgumentException("prompt cannot be blank");dsh.prompt(mapping.sessionId(),text);
    }
    private static String require(String value,String what){if(value==null||value.isBlank())throw new IllegalStateException("DSH returned an empty "+what+" id");return value;}
}
