package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.memory.MemoryCandidate;
import java.util.*;

/** Provider-neutral tool loop. Context is rebuilt before every model round and tool output is budgeted separately. */
public final class AgentExecutor {
    private final ModelProvider provider;private final ToolExecutor tools;private final ContextBuilder contexts;private final AgentToolCatalog catalog;
    private final SecurityEventSink security;private final int maxToolSteps,maxContextTokens,maxToolResultChars;private final TokenEstimator estimator;
    private final UsageLedger usageLedger;private final AgentTraceSink traceSink;private final ToolExecutionLedger executionLedger;private final TurnBoundaryObserver boundaryObserver;
    public AgentExecutor(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,AgentToolCatalog catalog,SecurityEventSink security,int maxToolSteps,int maxContextTokens,int maxToolResultChars,TokenEstimator estimator){
        this(provider,tools,contexts,catalog,security,maxToolSteps,maxContextTokens,maxToolResultChars,estimator,null,null,new InMemoryToolExecutionLedger());
    }
    public AgentExecutor(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,AgentToolCatalog catalog,SecurityEventSink security,int maxToolSteps,int maxContextTokens,int maxToolResultChars,TokenEstimator estimator,UsageLedger usageLedger,AgentTraceSink traceSink){
        this(provider,tools,contexts,catalog,security,maxToolSteps,maxContextTokens,maxToolResultChars,estimator,usageLedger,traceSink,new InMemoryToolExecutionLedger());
    }
    public AgentExecutor(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,AgentToolCatalog catalog,SecurityEventSink security,int maxToolSteps,int maxContextTokens,int maxToolResultChars,TokenEstimator estimator,UsageLedger usageLedger,AgentTraceSink traceSink,ToolExecutionLedger executionLedger){
        this(provider,tools,contexts,catalog,security,maxToolSteps,maxContextTokens,maxToolResultChars,estimator,usageLedger,traceSink,executionLedger,TurnBoundaryObserver.NOOP);
    }
    public AgentExecutor(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,AgentToolCatalog catalog,SecurityEventSink security,int maxToolSteps,int maxContextTokens,int maxToolResultChars,TokenEstimator estimator,UsageLedger usageLedger,AgentTraceSink traceSink,ToolExecutionLedger executionLedger,TurnBoundaryObserver boundaryObserver){
        this.provider=Objects.requireNonNull(provider);this.tools=Objects.requireNonNull(tools);this.contexts=Objects.requireNonNull(contexts);this.catalog=Objects.requireNonNull(catalog);this.security=Objects.requireNonNull(security);this.estimator=Objects.requireNonNull(estimator);
        this.usageLedger=usageLedger;this.traceSink=traceSink;this.executionLedger=Objects.requireNonNull(executionLedger);this.boundaryObserver=Objects.requireNonNull(boundaryObserver);if(maxToolSteps<0||maxToolSteps>32||maxContextTokens<1||maxToolResultChars<64)throw new IllegalArgumentException("invalid agent execution limits");this.maxToolSteps=maxToolSteps;this.maxContextTokens=maxContextTokens;this.maxToolResultChars=maxToolResultChars;
    }
    public static AgentExecutor defaults(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,SecurityEventSink security){return new AgentExecutor(provider,tools,contexts,new AgentToolCatalog(),security,8,contexts.budget().hardLimitTokens(),8000,new ApproximateTokenEstimator());}
    /** Production composition must pass the durable SQLite ledger; the test-friendly constructors remain process-local. */
    public static AgentExecutor production(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,AgentToolCatalog catalog,
            SecurityEventSink security,int maxToolSteps,int maxContextTokens,int maxToolResultChars,TokenEstimator estimator,
            UsageLedger usageLedger,AgentTraceSink traceSink,ToolExecutionLedger durableLedger){
        return production(provider,tools,contexts,catalog,security,maxToolSteps,maxContextTokens,maxToolResultChars,estimator,usageLedger,traceSink,durableLedger,TurnBoundaryObserver.NOOP);
    }
    public static AgentExecutor production(ModelProvider provider,ToolExecutor tools,ContextBuilder contexts,AgentToolCatalog catalog,
            SecurityEventSink security,int maxToolSteps,int maxContextTokens,int maxToolResultChars,TokenEstimator estimator,
            UsageLedger usageLedger,AgentTraceSink traceSink,ToolExecutionLedger durableLedger,TurnBoundaryObserver boundaryObserver){
        Objects.requireNonNull(durableLedger,"production execution ledger is required");
        if(!durableLedger.requiresStableEventId())throw new IllegalArgumentException("production execution ledger must be durable");
        return new AgentExecutor(provider,tools,contexts,catalog,security,maxToolSteps,maxContextTokens,maxToolResultChars,estimator,usageLedger,traceSink,durableLedger,boundaryObserver);
    }
    public AgentExecutionResult execute(AgentRequest initial){
        List<String> toolContext=new ArrayList<>();List<ModelToolResult> results=new ArrayList<>();List<ModelMessage> transcript=new ArrayList<>();List<String> toolNames=new ArrayList<>();ModelUsage total=new ModelUsage(0,0,0,0,0,"unknown","unknown",0);MemoryCandidate candidate=null;int steps=0,callsMade=0;String turnId=initial.turnId()==null||initial.turnId().isBlank()?(initial.eventId()==null||initial.eventId().isBlank()?UUID.randomUUID().toString():initial.eventId()):initial.turnId();ModelTurnExecutionState turnState=new ModelTurnExecutionState(initial.turnStateObserver());
        ModelProvider turnProvider=provider.snapshot();if(!turnProvider.available())return stopped(initial,initial.context(),total,0,results,null,toolNames,0,turnProvider.unavailableReason());
        ContextPackage current=initial.context();transcript.addAll(new ModelRequest(current,catalog.available(initial.availableCapabilities())).messages());
        for(;;){
            current=rebuild(initial.context(),toolContext);
            if(current.estimatedTokens()>maxContextTokens)return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"context-token-limit");
            int fullToolTokens=toolContext.stream().mapToInt(estimator::estimate).sum();if(fullToolTokens>current.diagnostics().toolResultTokens())return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"tool-result-token-limit");
            transcript.set(0,new ModelRequest(current,catalog.available(initial.availableCapabilities())).messages().getFirst());
            initial.cancellationToken().throwIfCancelled();if(turnState.state()==ModelTurnExecutionState.State.NOT_STARTED)turnState.begin();ModelRequest request=new ModelRequest(current,catalog.available(initial.availableCapabilities()),List.copyOf(transcript),1024,initial.cancellationToken(),turnState);
            ModelResponse response;
            try{response=turnProvider.stream(request,event->{});}catch(java.util.concurrent.CancellationException ex){return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"cancelled");}
            if(initial.cancellationToken().isCancelled())return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"cancelled");
            if(response!=null&&(response.decision()!=null||!response.toolCalls().isEmpty()))turnState.modelOutputReceived();
            callsMade++;if(response.usage()!=null){total=plus(total,response.usage());if(estimator instanceof ProviderTokenEstimator calibrated&&response.usage().inputTokens()>0)calibrated.recordUsage(response.usage().provider(),response.usage().model(),String.join("\n",request.messages().stream().map(ModelMessage::content).toList()),response.usage().inputTokens());if(usageLedger!=null)usageLedger.record(initial.actor().conversation().id(),turnId,response.usage());if(response.usage().inputTokens()+response.usage().outputTokens()>maxContextTokens)return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"provider-call-token-limit");}
            if(response.memoryCandidate()!=null)candidate=response.memoryCandidate();
            List<ModelToolCall> calls=response.toolCalls();if(calls.isEmpty()&&response.decision()!=null&&response.decision().type()==DecisionType.TOOL_CALL&&response.decision().toolCall()!=null)calls=List.of(new ModelToolCall("tool-"+(steps+1),response.decision().toolCall().name(),response.decision().toolCall().arguments()));
            if(calls.isEmpty()){AgentDecision decision=response.decision()==null?AgentDecision.noReply():response.decision();if(candidate!=null)decision=decision.withMemory(candidate);return complete(initial,decision,total,steps,current,results,candidate,toolNames,callsMade,"completed",turnState);}
            if(steps+calls.size()>maxToolSteps){security.record(new SecurityEvent("TOOL_STEP_LIMIT","","tool step limit reached",null));return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"tool-step-limit");}
            transcript.add(ModelMessage.assistantTools("",calls));
            for(ModelToolCall call:calls){
                if(initial.cancellationToken().isCancelled())return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"cancelled");
                turnState.toolProposed();
                boundaryObserver.toolProposed(turnId,call);
                if(call==null||!catalog.allows(call.name(),initial.availableCapabilities())){String name=call==null?"<null>":call.name();security.record(new SecurityEvent("TOOL_DENIED",name,"unknown or unauthorized tool",null));return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"tool-denied");}
                if(executionLedger.requiresStableEventId()&&(initial.eventId()==null||initial.eventId().isBlank())){security.record(new SecurityEvent("TOOL_DENIED",call.name(),"stable event id required for durable side-effect claim",null));return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"stable-event-id-required");}
                String eventId=initial.eventId()==null||initial.eventId().isBlank()?turnId:initial.eventId();
                boolean claimed=initial.cancellationToken().withCurrentGeneration(()->executionLedger.claim(eventId,call.id()));if(!claimed){security.record(new SecurityEvent("TOOL_REPLAY_BLOCKED",call.name(),"tool call was already claimed; refusing possible duplicate side effect",null));return stopped(initial,current,total,steps,results,candidate,toolNames,callsMade,"tool-replay-blocked");}
                ToolResult output=initial.cancellationToken().withCurrentGeneration(()->tools.execute(call.toToolCall(),initial,initial.availableCapabilities()));turnState.toolExecuted();boundaryObserver.toolExecuted(turnId,call);steps++;
                toolNames.add(call.name());ModelToolResult bounded=bound(ModelToolResult.from(call,output));results.add(bounded);transcript.add(ModelMessage.tool(bounded));toolContext.add(bounded.promptContent());
            }
        }
    }
    private ContextPackage rebuild(ContextPackage base,List<String> toolResults){
        ContextBuildInput input=new ContextBuildInput(base.simulationPrompt(),base.persona(),base.actorId(),base.displayName(),base.relationshipAddress(),base.conversationId(),base.memories(),base.conversationSummary(),base.recentMessages(),base.taskState(),base.currentMessage(),base.toolDescription(),null,toolResults);
        return contexts.build(input);
    }
    private ModelToolResult bound(ModelToolResult result){String text=result.content();if(text.length()>maxToolResultChars)text=text.substring(0,maxToolResultChars);Map<String,Object> data=result.data();if(data.toString().length()>maxToolResultChars)data=Map.of("truncated",true,"summary",data.toString().substring(0,maxToolResultChars));return new ModelToolResult(result.toolCallId(),result.name(),result.success(),text,data);}
    private AgentExecutionResult stopped(AgentRequest request,ContextPackage context,ModelUsage usage,int steps,List<ModelToolResult> results,MemoryCandidate candidate,List<String> toolNames,int calls,String reason){security.record(new SecurityEvent("AGENT_STOP",reason,"execution stopped safely",null));return complete(request,AgentDecision.noReply(),usage,steps,context,results,candidate,toolNames,calls,reason);}
    private AgentExecutionResult complete(AgentRequest request,AgentDecision decision,ModelUsage usage,int steps,ContextPackage context,List<ModelToolResult> results,MemoryCandidate candidate,List<String> toolNames,int calls,String reason){return complete(request,decision,usage,steps,context,results,candidate,toolNames,calls,reason,null);}
    private AgentExecutionResult complete(AgentRequest request,AgentDecision decision,ModelUsage usage,int steps,ContextPackage context,List<ModelToolResult> results,MemoryCandidate candidate,List<String> toolNames,int calls,String reason,ModelTurnExecutionState turnState){if(traceSink!=null)traceSink.record(new AgentTrace(request.actor().conversation().id(),request.eventId(),request.actor().actorId(),context.diagnostics()==null?List.of():context.diagnostics().includedMemoryIds(),context.diagnostics(),calls,usage.provider(),usage.model(),toolNames,decision.type(),decision.messages().size(),null));return new AgentExecutionResult(decision,usage,steps,context,results,candidate,reason,turnState);}
    private static ModelUsage plus(ModelUsage a,ModelUsage b){boolean empty=a.durationMillis()==0&&a.inputTokens()==0&&a.outputTokens()==0&&a.reasoningTokens()==0;return new ModelUsage(a.inputTokens()+b.inputTokens(),a.cacheHitTokens()+b.cacheHitTokens(),a.cacheMissTokens()+b.cacheMissTokens(),a.outputTokens()+b.outputTokens(),a.reasoningTokens()+b.reasoningTokens(),b.provider(),b.model(),a.durationMillis()+b.durationMillis(),empty?b.reasoningAvailable():a.reasoningAvailable()&&b.reasoningAvailable());}
}
