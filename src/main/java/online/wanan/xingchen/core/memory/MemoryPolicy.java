package online.wanan.xingchen.core.memory;

import online.wanan.xingchen.core.identity.ResolvedActorContext;
import java.time.Instant;
import java.util.UUID;

public final class MemoryPolicy {
    public enum Outcome { ACCEPT, REJECT, DOWNGRADE }
    public record Evaluation(Outcome outcome,String reason,Memory memory) {}
    /** Validation for explicit console-authored records; intentionally separate from agent candidate trust/downgrade rules. */
    public void validateAdministrative(Memory memory,Memory previous,boolean confirmedScopeExpansion){
        if(memory.scopeType()==MemoryScope.PRIVATE&&memory.subjectPersonId()==null)throw new IllegalArgumentException("PRIVATE memory requires an owner person");
        if(memory.scopeType()==MemoryScope.OWNER_GLOBAL&&memory.subjectPersonId()==null)throw new IllegalArgumentException("OWNER_GLOBAL memory requires its owner person");
        if(previous!=null&&expands(previous.scopeType(),memory.scopeType())&&!confirmedScopeExpansion)throw new IllegalArgumentException("expanding memory visibility requires explicit confirmation");
    }
    private static boolean expands(MemoryScope oldScope,MemoryScope next){
        if(oldScope==next||next==MemoryScope.PRIVATE)return false;
        return switch(oldScope){
            case PRIVATE,CONVERSATION,PROJECT->next==MemoryScope.PERSON_GLOBAL||next==MemoryScope.OWNER_GLOBAL||next==MemoryScope.GLOBAL;
            case PERSON_GLOBAL,OWNER_GLOBAL->next==MemoryScope.GLOBAL;
            case GLOBAL->false;
        };
    }
    public Evaluation evaluate(MemoryCandidate candidate,ResolvedActorContext actor){
        if(candidate.subjectPersonId()!=null&&!candidate.subjectPersonId().equals(actor.person().id()))return new Evaluation(Outcome.REJECT,"candidate subject must be the current speaker",null);
        if((candidate.requestedScope()==MemoryScope.PROJECT||candidate.requestedScope()==MemoryScope.GLOBAL)&&!actor.flags().owner()){
            var downgraded=build(candidate,actor,MemoryType.EPISODIC,MemoryScope.CONVERSATION,null);
            return new Evaluation(Outcome.DOWNGRADE,"non-owner broad-scope candidate downgraded to conversation scope",downgraded);
        }
        if(candidate.requestedScope()==MemoryScope.OWNER_GLOBAL&&!actor.flags().owner())return new Evaluation(Outcome.REJECT,"only owner may create owner-global memory",null);
        return new Evaluation(Outcome.ACCEPT, "policy accepted candidate",build(candidate,actor,candidate.type(),candidate.requestedScope(),candidate.projectKey()));
    }
    private static Memory build(MemoryCandidate c,ResolvedActorContext a,MemoryType type,MemoryScope scope,String project){
        Instant now=Instant.now(); UUID conversation=scope==MemoryScope.CONVERSATION?a.conversation().id():c.conversationId();
        UUID subject=a.person().id();
        String scopeId=switch(scope){case CONVERSATION->conversation.toString();case PERSON_GLOBAL,PRIVATE->subject.toString();case PROJECT->project;default->null;};
        return new Memory(UUID.randomUUID(),type,subject,conversation,project,c.content(),scope,scopeId,c.confidence(),c.importance(),c.explicit(),now,now,null,null,MemoryStatus.ACTIVE);
    }
}
