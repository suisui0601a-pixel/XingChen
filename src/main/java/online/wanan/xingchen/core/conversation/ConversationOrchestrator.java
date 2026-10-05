package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.model.PlatformEvent;
import online.wanan.xingchen.core.prompt.PromptSections;
import online.wanan.xingchen.core.prompt.PromptSnapshot;
import online.wanan.xingchen.core.prompt.PromptSnapshotProvider;
import online.wanan.xingchen.storage.*;
import java.time.Instant;
import java.util.*;

/** Coordinates domain use cases only; identity, memory policy, context, agent and transport remain separate services. */
public final class ConversationOrchestrator {
    private final IdentityResolver identities;private final IncomingMessageStore messages;private final MemoryRetriever retriever;
    private final MemoryRepository memories;private final MemoryPolicy memoryPolicy;private final ContextBuilder contexts;
    private final PromptSections prompts;private final SocialAgent agent;private final Set<AgentCapability> capabilities;
    private final MockToolExecutor tools;private final OneBotGateway gateway;private final ConversationWindow window;
    private final MockSummaryProvider summaryProvider;private final SessionLifecycleManager lifecycle;private final ContextDiagnosticsStore diagnostics;
    private final AgentExecutor executor;
    private final DurableTurnExecutionStore turnJournal;
    private final ReplyDispatcher replies;
    private final TurnBoundaryObserver boundaryObserver;
    private PromptSnapshotProvider promptSnapshots;
    public ConversationOrchestrator(IdentityResolver identities,IncomingMessageStore messages,MemoryRetriever retriever,
            MemoryRepository memories,MemoryPolicy memoryPolicy,ContextBuilder contexts,PromptSections prompts,
            SocialAgent agent,Set<AgentCapability> capabilities,MockToolExecutor tools,OneBotGateway gateway,
            ConversationWindow window,MockSummaryProvider summaryProvider,SessionLifecycleManager lifecycle,ContextDiagnosticsStore diagnostics){
        this.identities=identities;this.messages=messages;this.retriever=retriever;this.memories=memories;this.memoryPolicy=memoryPolicy;
        this.contexts=contexts;this.prompts=prompts;this.agent=agent;this.capabilities=Set.copyOf(capabilities);this.tools=tools;
        this.gateway=gateway;this.window=window;this.summaryProvider=summaryProvider;this.lifecycle=lifecycle;this.diagnostics=diagnostics;
        this.executor=null;this.turnJournal=null;
        this.boundaryObserver=TurnBoundaryObserver.NOOP;
        this.replies=ReplyDispatcher.fromEnvironment(gateway);
    }
    public ConversationOrchestrator(IdentityResolver identities,IncomingMessageStore messages,MemoryRetriever retriever,
            MemoryRepository memories,MemoryPolicy memoryPolicy,ContextBuilder contexts,PromptSections prompts,
            AgentExecutor executor,Set<AgentCapability> capabilities,OneBotGateway gateway,ConversationWindow window,
            MockSummaryProvider summaryProvider,SessionLifecycleManager lifecycle,ContextDiagnosticsStore diagnostics){
        this(identities,messages,retriever,memories,memoryPolicy,contexts,prompts,executor,capabilities,gateway,window,summaryProvider,lifecycle,diagnostics,null);
    }
    public ConversationOrchestrator(IdentityResolver identities,IncomingMessageStore messages,MemoryRetriever retriever,
            MemoryRepository memories,MemoryPolicy memoryPolicy,ContextBuilder contexts,PromptSections prompts,
            AgentExecutor executor,Set<AgentCapability> capabilities,OneBotGateway gateway,ConversationWindow window,
            MockSummaryProvider summaryProvider,SessionLifecycleManager lifecycle,ContextDiagnosticsStore diagnostics,
            OutboundExecutionService outbound){
        this(identities,messages,retriever,memories,memoryPolicy,contexts,prompts,executor,capabilities,gateway,window,summaryProvider,lifecycle,diagnostics,outbound,null);
    }
    public ConversationOrchestrator(IdentityResolver identities,IncomingMessageStore messages,MemoryRetriever retriever,
            MemoryRepository memories,MemoryPolicy memoryPolicy,ContextBuilder contexts,PromptSections prompts,
            AgentExecutor executor,Set<AgentCapability> capabilities,OneBotGateway gateway,ConversationWindow window,
            MockSummaryProvider summaryProvider,SessionLifecycleManager lifecycle,ContextDiagnosticsStore diagnostics,
            OutboundExecutionService outbound,DurableTurnExecutionStore turnJournal){
        this(identities,messages,retriever,memories,memoryPolicy,contexts,prompts,executor,capabilities,gateway,window,summaryProvider,lifecycle,diagnostics,outbound,turnJournal,TurnBoundaryObserver.NOOP);
    }
    public ConversationOrchestrator(IdentityResolver identities,IncomingMessageStore messages,MemoryRetriever retriever,
            MemoryRepository memories,MemoryPolicy memoryPolicy,ContextBuilder contexts,PromptSections prompts,
            AgentExecutor executor,Set<AgentCapability> capabilities,OneBotGateway gateway,ConversationWindow window,
            MockSummaryProvider summaryProvider,SessionLifecycleManager lifecycle,ContextDiagnosticsStore diagnostics,
            OutboundExecutionService outbound,DurableTurnExecutionStore turnJournal,TurnBoundaryObserver boundaryObserver){
        this.identities=identities;this.messages=messages;this.retriever=retriever;this.memories=memories;this.memoryPolicy=memoryPolicy;
        this.contexts=contexts;this.prompts=prompts;this.agent=null;this.capabilities=Set.copyOf(capabilities);this.tools=null;
        this.gateway=gateway;this.window=window;this.summaryProvider=summaryProvider;this.lifecycle=lifecycle;this.diagnostics=diagnostics;
        this.executor=Objects.requireNonNull(executor);this.turnJournal=turnJournal;
        this.boundaryObserver=Objects.requireNonNull(boundaryObserver);
        this.replies=ReplyDispatcher.fromEnvironment(gateway,outbound);
    }
    public OrchestrationResult handle(PlatformEvent event){
        return handle(event,true);
    }
    public OrchestrationResult handle(PlatformEvent event,boolean allowResponse){
        return handle(prepare(event),allowResponse,null,Set.of());
    }
    public PreparedConversationEvent prepare(PlatformEvent event){
        ResolvedActorContext actor=identities.resolve(event);
        return new PreparedConversationEvent(event,actor,messages.persistOrdered(event,actor));
    }
    public PreparedConversationEvent preparePending(PlatformEvent event){
        ResolvedActorContext actor=identities.resolve(event);
        if(!messages.isPending(event,actor.conversation().id()))return new PreparedConversationEvent(event,actor,new EventPersistResult(EventPersistResult.Status.DUPLICATE));
        return new PreparedConversationEvent(event,actor,new EventPersistResult(EventPersistResult.Status.NEW_IN_ORDER));
    }
    public PreparedConversationEvent prepareRecovered(PlatformEvent event){return new PreparedConversationEvent(event,identities.resolve(event),new EventPersistResult(EventPersistResult.Status.NEW_IN_ORDER));}
    public List<PlatformEvent> pendingEvents(){return messages.pendingEvents();}
    public PlatformEvent eventForExecutionKey(String key){return messages.eventForExecutionKey(key);}
    public boolean claimInbound(PlatformEvent event,UUID conversationId,long generation){return messages.claim(event,conversationId,generation);}
    public void completeInbound(PlatformEvent event,UUID conversationId){messages.complete(event,conversationId);}
    public void failInbound(PlatformEvent event,UUID conversationId){messages.fail(event,conversationId);}
    public void persistWakeReasons(PlatformEvent event,UUID conversationId,Set<String> reasons){if(turnJournal!=null)turnJournal.wakeReasons(IncomingMessageStore.stableTurnId(IncomingMessageStore.executionKey(event,conversationId.toString())),reasons);}
    public OrchestrationResult handle(PreparedConversationEvent prepared,boolean allowResponse,
                                     online.wanan.xingchen.core.agent.SimulationConversationState simulation,
                                     Set<String> wakeReasons){
        return handle(prepared,allowResponse,simulation,wakeReasons,new CancellationToken());
    }
    public OrchestrationResult handle(PreparedConversationEvent prepared,boolean allowResponse,
                                     online.wanan.xingchen.core.agent.SimulationConversationState simulation,
                                     Set<String> wakeReasons,CancellationToken cancellation){
        return handle(prepared,allowResponse,simulation,wakeReasons,cancellation,null);
    }
    public OrchestrationResult handle(PreparedConversationEvent prepared,boolean allowResponse,
                                     online.wanan.xingchen.core.agent.SimulationConversationState simulation,
                                     Set<String> wakeReasons,CancellationToken cancellation,
                                     online.wanan.xingchen.core.agent.SocialSettingsSnapshot socialSettings){
        PlatformEvent event=prepared.event();ResolvedActorContext actor=prepared.actor();var persisted=prepared.persistence();
        if(!persisted.shouldProcess())return new OrchestrationResult(persisted.isDuplicate(),!persisted.isDuplicate(),false,actor,null,null,null,null);
        if(actor.flags().self()||event.eventType().startsWith("notice:"))return new OrchestrationResult(false,true,false,actor,null,null,null,null);
        if(!allowResponse)return new OrchestrationResult(false,true,false,actor,null,null,null,null);
        PromptSnapshot promptSnapshot=promptSnapshots==null?new PromptSnapshot(prompts.profileId(),null,prompts.simulationVersion(),prompts.simulationPrompt(),null,prompts.personaVersion(),prompts.personaPrompt()):promptSnapshots.capture();
        String key=actor.conversation().id().toString(); List<String> recent=window.appendAndGet(key,event.actor().displayName()+": "+event.text());
        RequestContext request=new RequestContext(actor.person().id(),actor.conversation().id(),actor.flags().owner(),null);
        RelevantMemorySet relevant=retriever.retrieve(request,event.text(),actor.conversation().id(),actor.person().id(),contexts.budget().memoryTokenBudget());
        String address=actor.address();SessionHandoff prior=lifecycle.loadHandoff(key);
        ContextPackage context=build(actor,event,request,relevant,recent,prior==null?"":prior.conversationSummary(),simulation,wakeReasons,promptSnapshot);
        boolean rollover=context.lifecycleStatus()==SessionLifecycleStatus.ROLLOVER_REQUIRED||context.lifecycleStatus()==SessionLifecycleStatus.HARD_LIMIT;
        if(rollover){SessionHandoff handoff=summaryProvider.summarize(actor.displayIdentity(),address,recent);lifecycle.saveHandoff(key,handoff);window.clear(key);context=build(actor,event,request,relevant,List.of(),handoff.conversationSummary(),simulation,wakeReasons,promptSnapshot);}
        lifecycle.recordContextSize(key,context.estimatedTokens());
        diagnostics.save(actor.conversation().id(),context.diagnostics());
        String logicalTurnId=IncomingMessageStore.stableTurnId(IncomingMessageStore.executionKey(event,actor.conversation().id().toString()));
        AgentExecutionResult execution=null;AgentDecision decision;if(executor==null)decision=agent.decide(new AgentRequest(actor,context,capabilities,event.message().platformMessageId(),logicalTurnId,null,cancellation));else{TurnStateObserver observer=turnJournal==null?null:state->turnJournal.state(logicalTurnId,state.name());execution=executor.execute(new AgentRequest(actor,context,capabilities,event.message().platformMessageId(),logicalTurnId,observer,cancellation));decision=execution.decision();context=execution.finalContext();diagnostics.save(actor.conversation().id(),context.diagnostics());}
        if(cancellation.isCancelled())return new OrchestrationResult(false,true,rollover,actor,context,AgentDecision.noReply(),null,null);
        boolean journalable=execution==null||execution.turnState()==null||execution.turnState().state()!=ModelTurnExecutionState.State.UNCERTAIN;
        if(turnJournal!=null&&journalable)cancellation.runWithCurrentGeneration(()->turnJournal.output(logicalTurnId,decision.type().name(),decision.messages()));
        if(execution!=null&&execution.turnState()!=null&&journalable)execution.turnState().outputEmitted();
        if(journalable&&(decision.type()==DecisionType.TEXT_REPLY||decision.type()==DecisionType.MULTI_REPLY))boundaryObserver.outputPersistedBeforeDispatch(logicalTurnId);
        ToolResult toolResult=null;
        MemoryPolicy.Evaluation evaluation=null;
        if(decision.memoryCandidate()!=null&&!cancellation.isCancelled()){evaluation=memoryPolicy.evaluate(decision.memoryCandidate(),actor);if(evaluation.memory()!=null&&!cancellation.isCancelled()){Memory m=evaluation.memory();cancellation.runWithCurrentGeneration(()->memories.save(m,List.of(new MemorySource(m.id(),event.platform().name(),actor.conversation().id(),event.message().platformMessageId(),actor.person().id(),Instant.now()))));}}
        if(execution!=null&&!execution.toolResults().isEmpty()){var last=execution.toolResults().getLast();toolResult=new ToolResult(last.success(),last.content(),last.data());}
        else if(execution==null&&decision.type()==DecisionType.TOOL_CALL&&!cancellation.isCancelled())toolResult=cancellation.withCurrentGeneration(()->tools.execute(decision.toolCall(),capabilities));
        else if((decision.type()==DecisionType.TEXT_REPLY||decision.type()==DecisionType.MULTI_REPLY)&&!cancellation.isCancelled()){var receipts=cancellation.withCurrentGeneration(()->{boundaryObserver.outboundDispatchInvoked(logicalTurnId);return replies.dispatch(actor,decision.messages(),logicalTurnId,simulation==null?0:simulation.generation(),socialSettings);});if(execution!=null&&execution.turnState()!=null){if(receipts.stream().anyMatch(r->r.status()==DeliveryStatus.UNKNOWN))execution.turnState().outboundOutputUnknown();else if(receipts.stream().anyMatch(SendReceipt::accepted))execution.turnState().outboundOutputSent();}}
        return new OrchestrationResult(false,false,rollover,actor,context,decision,evaluation,toolResult);
    }
    public DecisionType resumePersistedDecision(PlatformEvent event,DurableTurnExecutionStore.RecoveryTurn turn){
        DecisionType type;try{type=DecisionType.valueOf(turn.outputType());}catch(Exception e){throw new IllegalStateException("durable emitted decision is missing or invalid",e);}
        if(type==DecisionType.TEXT_REPLY||type==DecisionType.MULTI_REPLY){if(turn.outputMessages().isEmpty())throw new IllegalStateException("durable emitted text output is missing");var actor=identities.resolve(event);replies.dispatch(actor,turn.outputMessages(),turn.turnId(),turn.generation());}
        else if(type!=DecisionType.NO_REPLY&&type!=DecisionType.WAIT)throw new IllegalStateException("durable decision is not resumable");
        return type;
    }
    private ContextPackage build(ResolvedActorContext actor,PlatformEvent event,RequestContext request,RelevantMemorySet memory,List<String> recent,String summary,
                                 online.wanan.xingchen.core.agent.SimulationConversationState simulation,Set<String> wakeReasons,PromptSnapshot promptSnapshot){
        Map<String,String> taskState=new LinkedHashMap<>();if(event.details().reply()!=null){var ref=event.details().reply();taskState.put("reply.messageId",Objects.toString(ref.messageId(),""));taskState.put("reply.quotedUserId",Objects.toString(ref.quotedUserId(),"unknown"));taskState.put("reply.quotedDisplayName",Objects.toString(ref.quotedDisplayName(),""));taskState.put("reply.quotedBot",Boolean.toString(ref.quotedBot()));taskState.put("reply.quotedText",ref.quotedText());}
        taskState.put("prompt.simulationVersionId",Objects.toString(promptSnapshot.simulationVersionId(),"configuration-default"));taskState.put("prompt.personaVersionId",Objects.toString(promptSnapshot.personaVersionId(),"configuration-default"));
        if(simulation!=null){taskState.put("simulation.mode",simulation.mode());taskState.put("simulation.wakeState",simulation.wakeState().name());taskState.put("simulation.generation",Long.toString(simulation.generation()));taskState.put("simulation.lastReadCursor",Objects.toString(simulation.lastReadCursor(),""));taskState.put("simulation.wakeReasons",String.join(",",new TreeSet<>(wakeReasons==null?Set.of():wakeReasons)));taskState.put("simulation.consecutiveNoAction",Integer.toString(simulation.consecutiveNoAction()));}
        var input=new ContextBuildInput(promptSnapshot.simulationPrompt(),promptSnapshot.personaPrompt(),actor.actorId(),actor.displayIdentity(),actor.address(),
                actor.conversation().identity().platformConversationId(),memory.memories(),summary,recent,taskState,event.text(),"",request);
        return contexts.build(input);
    }
    public void setPromptSnapshotProvider(PromptSnapshotProvider provider){this.promptSnapshots=Objects.requireNonNull(provider);}
}
