package online.wanan.xingchen.core.context;

import online.wanan.xingchen.core.memory.*;
import java.util.*;

public final class ContextBuilder {
    private final ContextBudget budget;
    private final TokenEstimator estimator;
    public ContextBuilder(ContextBudget budget) { this(budget,new ApproximateTokenEstimator()); }
    public ContextBuilder(ContextBudget budget,TokenEstimator estimator) { this.budget = Objects.requireNonNull(budget);this.estimator=Objects.requireNonNull(estimator); }
    public ContextPackage build(String persona, String simulationPrompt, String actorId, String conversationId,
                                String relationshipAddress, Collection<Memory> candidateMemories,
                                List<String> recentMessages, Map<String,String> taskState, String currentMessage,
                                int estimatedTokens, RequestContext request) {
        return build(new ContextBuildInput(simulationPrompt,persona,actorId,actorId,relationshipAddress,conversationId,
                candidateMemories,"",recentMessages,taskState,currentMessage,"",request));
    }
    public ContextPackage build(ContextBuildInput input){
        List<Memory> visible=input.memories().stream().filter(m->input.requestContext()==null||MemoryVisibility.canRead(m,input.requestContext()))
                .sorted(Comparator.comparingDouble(Memory::importance).reversed()).toList();
        String sim=n(input.simulationPrompt()),persona=n(input.personaPrompt()),summary=trim(n(input.conversationSummary()),budget.summaryTokenBudget());
        String identity="actorId="+n(input.actorId())+"\ndisplayName="+n(input.displayName());
        String relationship="displayName="+n(input.displayName())+"\nassistantAddress="+n(input.address());
        String taskText=input.taskState().entrySet().stream().map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+"\n"+b).orElse("");
        String current=n(input.currentMessage()),tools=n(input.toolDescription());
        int fixed=estimate(sim)+estimate(persona)+estimate(identity)+estimate(relationship)+estimate(current)+estimate(tools)+estimate(taskText);
        int summaryTokens=estimate(summary); int remaining=Math.max(0,budget.hardLimitTokens()-fixed-summaryTokens);
        List<Memory> selected=new ArrayList<>();int memoryTokens=0;
        for(Memory m:visible){int cost=estimate(m.content());if(memoryTokens+cost>budget.memoryTokenBudget()||memoryTokens+cost>remaining)continue;selected.add(m);memoryTokens+=cost;}
        remaining=Math.max(0,remaining-memoryTokens);
        List<String> toolResults=new ArrayList<>();int toolTokens=0;
        for(int i=input.toolResults().size()-1;i>=0;i--){String item=input.toolResults().get(i);int cost=estimate(item);int cap=Math.min(budget.toolResultTokenBudget(),remaining);if(toolTokens+cost>cap){int allowed=Math.max(0,cap-toolTokens);item=trim(item,allowed);cost=estimate(item);}if(cost>0&&toolTokens+cost<=cap){toolResults.add(0,item);toolTokens+=cost;}}
        remaining=Math.max(0,remaining-toolTokens);
        List<String> recentCandidates=input.recentMessages().stream().skip(Math.max(0,input.recentMessages().size()-budget.recentMessageLimit())).toList();
        LinkedList<String> recent=new LinkedList<>();int recentTokens=0;
        for(int i=recentCandidates.size()-1;i>=0;i--){String item=recentCandidates.get(i);int cost=estimate(item);if(recentTokens+cost>remaining)continue;recent.addFirst(item);recentTokens+=cost;}
        int total=fixed+summaryTokens+memoryTokens+toolTokens+recentTokens;
        int calibratedTotal=calibratedEstimate(sim)+calibratedEstimate(persona)+calibratedEstimate(identity)+calibratedEstimate(relationship)+calibratedEstimate(current)+calibratedEstimate(tools)+calibratedEstimate(taskText)+calibratedEstimate(summary);
        for(Memory m:selected)calibratedTotal+=calibratedEstimate(m.content());
        for(String item:toolResults)calibratedTotal+=calibratedEstimate(item);
        for(String item:recent)calibratedTotal+=calibratedEstimate(item);
        SessionLifecycleStatus status=lifecycle(total);
        var diagnostics=new ContextDiagnostics(total,estimate(persona),estimate(sim),memoryTokens,recentTokens,summaryTokens,
                selected.stream().map(Memory::id).toList(),input.memories().size()-selected.size(),status,toolTokens,calibratedTotal,Math.max(0,total-calibratedTotal));
        return new ContextPackage(persona,sim,n(input.actorId()),n(input.conversationId()),n(input.address()),List.copyOf(selected),
                List.copyOf(recent),input.taskState(),current,total,status,n(input.displayName()),identity,relationship,summary,tools,diagnostics,List.copyOf(toolResults));
    }
    public SessionLifecycleStatus lifecycle(int tokens) {
        if (tokens > budget.hardLimitTokens()) return SessionLifecycleStatus.HARD_LIMIT;
        if (tokens >= budget.rolloverTokens()) return SessionLifecycleStatus.ROLLOVER_REQUIRED;
        if (tokens >= budget.softLimitTokens()) return SessionLifecycleStatus.SOFT_LIMIT;
        return SessionLifecycleStatus.NORMAL;
    }
    public ContextBudget budget() { return budget; }
    private int calibratedEstimate(String text){return estimator.estimate(text);}
    private int estimate(String text){int calibrated=calibratedEstimate(text);return calibrated==0?0:(int)Math.ceil(calibrated*(100.0+budget.safetyMarginPercent())/100.0);}
    private String trim(String value,int maxTokens){if(estimate(value)<=maxTokens)return value;int low=0,high=value.length();while(low<high){int mid=(low+high+1)/2;if(estimate(value.substring(0,mid))<=maxTokens)low=mid;else high=mid-1;}return value.substring(0,low);}
    private static String n(String s){return s==null?"":s;}
}
