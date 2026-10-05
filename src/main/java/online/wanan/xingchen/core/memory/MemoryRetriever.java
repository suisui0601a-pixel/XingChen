package online.wanan.xingchen.core.memory;

import online.wanan.xingchen.core.context.TokenEstimator;
import java.time.Instant;
import java.util.*;

/** Scope-checks first, deduplicates repository search channels, applies a deterministic relevance order and hard token ceiling. */
public final class MemoryRetriever {
    private final MemoryRepository repository; private final TokenEstimator estimator;
    public MemoryRetriever(MemoryRepository repository,TokenEstimator estimator){this.repository=repository;this.estimator=estimator;}
    public RelevantMemorySet retrieve(RequestContext request,String currentMessage,UUID conversation,UUID actor,int tokenBudget){
        if(tokenBudget<=0)return new RelevantMemorySet(List.of(),0,repository.findRelevantCandidates(request,256).size());
        Map<UUID,Memory> candidates=new LinkedHashMap<>();
        repository.findRelevantCandidates(request,256).forEach(m->candidates.put(m.id(),m));
        if(actor!=null)repository.findBySubject(actor,request).forEach(m->candidates.put(m.id(),m));
        if(conversation!=null)repository.findByConversation(conversation,request).forEach(m->candidates.put(m.id(),m));
        Set<UUID> exact=new HashSet<>(); if(actor!=null)repository.findBySubject(actor,request).forEach(m->exact.add(m.id()));
        Set<UUID> local=new HashSet<>(); if(conversation!=null)repository.findByConversation(conversation,request).forEach(m->local.add(m.id()));
        Set<UUID> textMatches=new HashSet<>();
        if(currentMessage!=null&&!currentMessage.isBlank())try{repository.searchText(currentMessage,request,100).forEach(m->textMatches.add(m.id()));}catch(RuntimeException ignored){/* tokenizer-specific input is only a ranking hint */}
        Comparator<Memory> order=Comparator.<Memory>comparingInt(m->exact.contains(m.id())?0:1)
                .thenComparingInt(m->local.contains(m.id())?0:1)
                .thenComparingInt(m->typeRank(m.type()))
                .thenComparing(Comparator.comparingDouble(Memory::importance).reversed())
                .thenComparing((Memory m)->m.updatedAt()==null?Instant.EPOCH:m.updatedAt(),Comparator.reverseOrder())
                .thenComparingInt(m->textMatches.contains(m.id())?0:1);
        List<Memory> ordered=candidates.values().stream().filter(m->MemoryVisibility.canRead(m,request)).sorted(order).toList();
        List<Memory> selected=new ArrayList<>();int used=0;
        for(Memory m:ordered){int cost=estimator.estimate(m.content());if(used+cost>tokenBudget)continue;selected.add(m);used+=cost;}
        return new RelevantMemorySet(selected,used,ordered.size()-selected.size());
    }
    private static int typeRank(MemoryType t){return switch(t){case PERSON,RELATIONSHIP,PREFERENCE->0;case PROJECT,TASK->1;case CONVERSATION_SUMMARY->2;case SEMANTIC->3;case EPISODIC->4;};}
}
