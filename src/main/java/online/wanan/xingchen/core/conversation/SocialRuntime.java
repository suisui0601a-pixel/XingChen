package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.agent.ParticipationPolicy;
import online.wanan.xingchen.core.agent.ReservedModeMachine;
import online.wanan.xingchen.core.agent.SocialTrigger;
import online.wanan.xingchen.core.agent.SocialTriggerPolicy;
import online.wanan.xingchen.core.agent.SimulationStateService;
import online.wanan.xingchen.core.agent.SimulationConversationState;
import online.wanan.xingchen.core.model.PlatformEvent;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/** Lifecycle boundary from OneBot transport events to the social conversation use case. */
public final class SocialRuntime implements AutoCloseable,ConversationControlPort {
    private static final System.Logger LOG = System.getLogger(SocialRuntime.class.getName());
    private final OneBotGateway gateway;
    private final ConversationOrchestrator orchestrator;
    private final SocialTriggerPolicy triggers;
    private final ParticipationPolicy participation;
    private final ReservedModeMachine reserved;
    private final DoubleSupplier replyProbability;
    private final Predicate<PlatformEvent> humanInteractions;
    private final SimulationStateService simulation;
    private final online.wanan.xingchen.storage.DurableTurnExecutionStore turnJournal;
    private final ConversationSerialDispatcher queue;
    private volatile ShortContextResetService resetService;
    private volatile ConversationResetCoordinator resetCoordinator;
    private volatile String resetOwnerQq;
    private volatile online.wanan.xingchen.console.SocialSettingsService socialSettingsService;
    private volatile online.wanan.xingchen.console.AccessControlService accessControlService;
    private final ActiveTurnRegistry activeTurns=new ActiveTurnRegistry();
    private final AtomicBoolean started = new AtomicBoolean();

    public SocialRuntime(OneBotGateway gateway, ConversationOrchestrator orchestrator,
                         SocialTriggerPolicy triggers, ParticipationPolicy participation,
                         ReservedModeMachine reserved, DoubleSupplier replyProbability) {
        this(gateway,orchestrator,triggers,participation,reserved,replyProbability,e->false,null,null);
    }
    public SocialRuntime(OneBotGateway gateway, ConversationOrchestrator orchestrator,
                         SocialTriggerPolicy triggers, ParticipationPolicy participation,
                         ReservedModeMachine reserved, DoubleSupplier replyProbability,Predicate<PlatformEvent> humanInteractions) {
        this(gateway,orchestrator,triggers,participation,reserved,replyProbability,humanInteractions,null,null);
    }
    public SocialRuntime(OneBotGateway gateway, ConversationOrchestrator orchestrator,
                        SocialTriggerPolicy triggers, ParticipationPolicy participation,
                        ReservedModeMachine reserved, DoubleSupplier replyProbability,
                        Predicate<PlatformEvent> humanInteractions, SimulationStateService simulation) {
        this(gateway,orchestrator,triggers,participation,reserved,replyProbability,humanInteractions,simulation,null);
    }
    public SocialRuntime(OneBotGateway gateway,ConversationOrchestrator orchestrator,SocialTriggerPolicy triggers,ParticipationPolicy participation,
                        ReservedModeMachine reserved,DoubleSupplier replyProbability,Predicate<PlatformEvent> humanInteractions,
                        SimulationStateService simulation,online.wanan.xingchen.storage.DurableTurnExecutionStore turnJournal) {
        this.gateway = Objects.requireNonNull(gateway);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.triggers = Objects.requireNonNull(triggers);
        this.participation = Objects.requireNonNull(participation);
        this.reserved = Objects.requireNonNull(reserved);
        this.replyProbability = Objects.requireNonNull(replyProbability);
        this.humanInteractions = Objects.requireNonNull(humanInteractions);
        this.simulation = simulation;
        this.turnJournal=turnJournal;
        this.queue = new ConversationSerialDispatcher();
    }
    public void setResetService(ShortContextResetService resetService){this.resetService=resetService;}
    public void setResetControl(ConversationResetCoordinator coordinator,String ownerQq){this.resetCoordinator=coordinator;this.resetOwnerQq=ownerQq;}
    public void setSocialSettingsService(online.wanan.xingchen.console.SocialSettingsService service){this.socialSettingsService=Objects.requireNonNull(service);}
    public void setAccessControlService(online.wanan.xingchen.console.AccessControlService service){this.accessControlService=Objects.requireNonNull(service);}

    public synchronized void start() {
        if (!started.compareAndSet(false, true)) return;
        var coordinator=resetCoordinator;if(coordinator!=null)for(var pending:coordinator.pendingResets())try{queue.execute(pending.key(),()->processResetCleanup(pending));}catch(RejectedExecutionException ignored){LOG.log(System.Logger.Level.ERROR,"pending reset cleanup could not be queued; durable RESETTING marker retained");}
        gateway.setEventListener(this::enqueue);
        var status = gateway.connect();
        if (!status.connected()) {
            started.set(false);
            gateway.setEventListener(null);
            throw new IllegalStateException("OneBot gateway failed to connect");
        }
        // Inputs persisted before turn claim have no Agent side effects and are safe to resume.
        if(simulation!=null)for(var pending:orchestrator.pendingEvents()){
            var identity=pending.conversation();String key=identity.platform()+":"+identity.type()+":"+identity.platformConversationId();
            try{queue.execute(key,()->process(pending,true));}catch(RejectedExecutionException ignored){LOG.log(System.Logger.Level.WARNING,"persisted social event recovery deferred because the bounded conversation queue is full");}
        }
        if(simulation!=null&&turnJournal!=null){turnJournal.releaseExpiredRecoveryClaims();for(var turn:turnJournal.recoverable()){
            var event=orchestrator.eventForExecutionKey(turn.inboundExecutionKey());if(event==null){turnJournal.interrupt(turn.turnId());continue;}
            var current=simulation.get(turn.conversationId());if(current.generation()!=turn.generation()){turnJournal.markStale(turn.turnId());continue;}
            // The tool ledger is authoritative across the gap between a tool effect and journal update;
            // the outbound ledger is authoritative across send/ack loss. Either blocks whole-turn replay.
            if(turnJournal.hasToolClaim(turn.eventId())){turnJournal.interrupt(turn.turnId());continue;}
            if(turnJournal.hasOutbound(turn.turnId())){turnJournal.interrupt(turn.turnId());continue;}
            if(turn.modelTurnState().equals("OUTPUT_EMITTED")&&(turn.outputType()==null||((turn.outputType().equals("TEXT_REPLY")||turn.outputType().equals("MULTI_REPLY"))&&turn.outputMessages().isEmpty()))){turnJournal.interrupt(turn.turnId());continue;}
            if(!java.util.Set.of("NOT_STARTED","STREAMING_NO_EFFECT","MODEL_OUTPUT_RECEIVED","TOOL_PROPOSED","OUTPUT_EMITTED").contains(turn.modelTurnState())){turnJournal.interrupt(turn.turnId());continue;}
            if(!turnJournal.claimRecovery(turn.turnId(),current.generation()))continue;
            String key=event.conversation().platform()+":"+event.conversation().type()+":"+event.conversation().platformConversationId();
            try{queue.execute(key,()->recoverClaimed(event,current,turn));}catch(RejectedExecutionException ignored){turnJournal.fail(turn.turnId());LOG.log(System.Logger.Level.WARNING,"durable turn recovery deferred because the bounded conversation queue is full");}
        }}
    }

    private void enqueue(PlatformEvent event) {
        if (!started.get()) return;
        var conversation=event.conversation();
        String key=conversation.platform()+":"+conversation.type()+":"+conversation.platformConversationId();
        var coordinator=resetCoordinator;
        if(coordinator==null){try{queue.execute(key,()->process(event,false));}catch(RejectedExecutionException ignored){LOG.log(System.Logger.Level.WARNING,"social event dropped because the bounded conversation queue is full or closed");}return;}
        // Serialize control-intent registration with lane enqueue so newer input cannot slip ahead of reset cleanup.
        synchronized(coordinator){var ticket=coordinator.interrupt(event,resetOwnerQq,activeTurns);long epoch=ticket==null?coordinator.epoch(event):ticket.epoch();
            try { queue.execute(key, () -> {if(ticket!=null){processReset(event,ticket);return;}if(!coordinator.current(event,epoch))return;process(event,false);}); }
            catch (RejectedExecutionException ignored) { LOG.log(System.Logger.Level.WARNING, "social event dropped because the bounded conversation queue is full or closed"); }
        }
    }

    private void processReset(PlatformEvent event,ConversationResetCoordinator.ResetTicket ticket){
        try{var prepared=orchestrator.prepare(event);if(prepared.mayStartTurn())orchestrator.claimInbound(event,ticket.conversationId(),ticket.generation());
            processResetCleanup(ticket);if(prepared.mayStartTurn())orchestrator.completeInbound(event,ticket.conversationId());
        }catch(RuntimeException|Error failure){LOG.log(System.Logger.Level.ERROR,"serialized reset cleanup failed; durable RESETTING marker retained");throw failure;}
    }
    private void processResetCleanup(ConversationResetCoordinator.ResetTicket ticket){if(resetService==null||!resetService.cleanupReset(ticket.conversationId(),ticket.generation()))throw new IllegalStateException("conversation reset cleanup lost its generation claim");var coordinator=resetCoordinator;if(coordinator!=null)coordinator.complete(ticket);}

    private void process(PlatformEvent event,boolean recovery) {
        var access=accessControlService;
        if(access!=null){String scope=event.conversation().type().name();var decision=access.evaluate(event.platform().name(),event.actor().platformUserId(),scope,event.conversation().platformConversationId(),event.actor().owner());if(!decision.allowed()){LOG.log(System.Logger.Level.INFO,"incoming event denied by configured access policy ({0})",decision.reason());return;}}
        if(humanInteractions.test(event))return;
        if(simulation==null) {
            SocialTrigger legacyTrigger=triggers.evaluate(event);
            reserved.onTrigger(legacyTrigger);
            orchestrator.handle(event,participation.participates(legacyTrigger,replyProbability.getAsDouble()));
            return;
        }
        var prepared=recovery?orchestrator.preparePending(event):orchestrator.prepare(event);
        if(!prepared.mayStartTurn()) {
            orchestrator.handle(prepared,false,null,java.util.Set.of());
            return;
        }
        var actor=prepared.actor();
        var command=new SlashCommandRouter(java.util.Set.of()).parse(event.text());
        if(command.kind()==SlashCommandRouter.Kind.RESET){
            if(!orchestrator.claimInbound(event,actor.conversation().id(),simulation==null?0:simulation.get(actor.conversation().id()).generation()))return;
            if(actor.flags().owner()&&event.platform()==online.wanan.xingchen.core.model.Platform.QQ
                    &&actor.conversation().identity().type()==online.wanan.xingchen.core.model.ConversationType.PRIVATE){
                if(resetService==null)throw new IllegalStateException("conversation reset is not configured");
                resetService.reset(actor.conversation().id());
            }
            orchestrator.completeInbound(event,actor.conversation().id());
            return;
        }
        if(!orchestrator.claimInbound(event,actor.conversation().id(),simulation.get(actor.conversation().id()).generation()))return;
        try { if(processClaimed(event,prepared))orchestrator.completeInbound(event,actor.conversation().id()); }
        catch(RuntimeException|Error failure){orchestrator.failInbound(event,actor.conversation().id());throw failure;}
    }

    private boolean processClaimed(PlatformEvent event,PreparedConversationEvent prepared) {
        var actor=prepared.actor();
        var state=simulation.get(actor.conversation().id());
        var settingSource=socialSettingsService;var settings=settingSource==null?online.wanan.xingchen.core.agent.SocialSettingsSnapshot.defaults():settingSource.capture(actor.conversation().id(),state.wakeConfig());
        var policyEvent=new PlatformEvent(event.platform(),event.conversation(),
                new online.wanan.xingchen.core.model.ActorIdentity(event.actor().platform(),event.actor().platformUserId(),event.actor().displayName(),actor.flags().self(),actor.flags().owner()),
                event.message(),event.eventType(),event.actorPlatformRole(),event.text(),event.timestamp(),event.rawMetadata(),event.details());
        var wake=triggers.evaluate(policyEvent,simulation.configuredSpeakers(state),settings);
        var wakeReasons=new java.util.LinkedHashSet<>(wake.reasons());
        boolean continuation=false;
        if(state.wakeState()==SimulationConversationState.WakeState.WAITING) {
            var resumed=simulation.continueWait(actor.conversation().id(),state.generation());
            if(resumed.isEmpty()) {
                try { simulation.markRead(actor.conversation().id(),event.message().platformMessageId()); }
                catch(IllegalArgumentException ex) { LOG.log(System.Logger.Level.WARNING,"social read cursor was not advanced because ordering could not be proven"); }
                simulation.recordNoAction(actor.conversation().id());
                return true;
            }
            state=resumed.get();continuation=true;wakeReasons.add("WAIT_CONTINUATION");
        }
        boolean manualWake=wake.reasons().contains("manual-wake");
        if(manualWake)state=simulation.wake(actor.conversation().id(),actor.flags().owner());
        {
            var signal=new SimulationStateService.IncomingSignal(
                    wake.reasons().contains("mention"),
                    wake.reasons().contains("bot-name"),wake.reasons().contains("question"),
                    wake.reasons().contains("poke"),
                    wake.reasons().contains("reply-to-bot"),
                    actor.person().platformUserId(),event.message().platformMessageId());
            var transition=simulation.onIncoming(actor.conversation().id(),signal,settings);
            state=transition.state();
            if(wake.forced()&&state.wakeState()==SimulationConversationState.WakeState.SLEEPING)
                state=simulation.wakeFromTrigger(actor.conversation().id());
        }
        SocialTrigger trigger = continuation?SocialTrigger.MUST_REPLY:wake.trigger();
        reserved.onTrigger(trigger);
        boolean sleeping=state.wakeState()==SimulationConversationState.WakeState.SLEEPING;
        double ordinaryProbability=settingSource==null?replyProbability.getAsDouble():settings.ordinaryMessageProbability();
        boolean respond = !sleeping&&participation.participates(trigger, ordinaryProbability);
        try { simulation.markRead(actor.conversation().id(),event.message().platformMessageId()); }
        catch(IllegalArgumentException ex) { LOG.log(System.Logger.Level.WARNING,"social read cursor was not advanced because ordering could not be proven"); }
        boolean acted=false;
        String logicalTurnId=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId(online.wanan.xingchen.storage.IncomingMessageStore.executionKey(event,actor.conversation().id().toString()));
        var cancellation=new online.wanan.xingchen.core.agent.CancellationToken();
        // Incoming/read-cursor transitions may each advance generation; register against the persisted value after both.
        long activeGeneration=simulation.get(actor.conversation().id()).generation();
        var active=activeTurns.beginIfCurrent(actor.conversation().id(),logicalTurnId,activeGeneration,cancellation,()->simulation.get(actor.conversation().id()).generation());
        if(active==null)return false;
        try {
            orchestrator.persistWakeReasons(event,actor.conversation().id(),wakeReasons);
            var result=orchestrator.handle(prepared,respond,state,wakeReasons,cancellation,settings);
            if(cancellation.isCancelled())return false;
            acted=result.decision()!=null&&result.decision().type()!=online.wanan.xingchen.core.agent.DecisionType.NO_REPLY
                    &&result.decision().type()!=online.wanan.xingchen.core.agent.DecisionType.WAIT;
            if(result.decision()!=null&&result.decision().type()==online.wanan.xingchen.core.agent.DecisionType.WAIT)
                cancellation.runWithCurrentGeneration(()->simulation.waitForNextMessage(actor.conversation().id(),event.message().platformMessageId(),"next-message"));
        } finally {
            activeTurns.finish(actor.conversation().id(),active);
            boolean completedAction=acted;
            if(!cancellation.isCancelled())try{cancellation.runWithCurrentGeneration(()->{if(completedAction)simulation.recordAction(actor.conversation().id());else simulation.recordNoAction(actor.conversation().id());});}catch(java.util.concurrent.CancellationException stale){/* reset control interrupt won the generation race */}
        }
        return true;
    }

    private void recoverClaimed(PlatformEvent event,SimulationConversationState snapshot,online.wanan.xingchen.storage.DurableTurnExecutionStore.RecoveryTurn turn){
        var prepared=orchestrator.prepareRecovered(event);var actor=prepared.actor();
        var current=simulation.get(actor.conversation().id());String turnId=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId(online.wanan.xingchen.storage.IncomingMessageStore.executionKey(event,actor.conversation().id().toString()));
        if(current.generation()!=snapshot.generation()){turnJournal.markStale(turnId);return;}
        try{if(turn.modelTurnState().equals("OUTPUT_EMITTED")){var decision=orchestrator.resumePersistedDecision(event,turn);if(decision==online.wanan.xingchen.core.agent.DecisionType.WAIT)simulation.waitForNextMessage(actor.conversation().id(),event.message().platformMessageId(),"next-message");}else orchestrator.handle(prepared,true,current,turn.wakeReasons());orchestrator.completeInbound(event,actor.conversation().id());}
        catch(RuntimeException|Error failure){orchestrator.failInbound(event,actor.conversation().id());throw failure;}
    }

    public boolean isRunning() { return started.get() && gateway.status().connected(); }
    public RuntimeQueueDiagnostics queueDiagnostics(){var coordinator=resetCoordinator;return new RuntimeQueueDiagnostics(queue.activeConversationCount(),queue.queuedConversationCount(),queue.queuedTaskCount(),coordinator==null?0:coordinator.resettingCount());}
    @Override public int queuedFor(String laneKey){return queue.queuedFor(laneKey);}
    @Override public boolean laneActive(String laneKey){return queue.active(laneKey);}
    @Override public SimulationSnapshot changeMode(String laneKey,java.util.UUID conversationId,String mode,long expectedGeneration,boolean ownerAllowed){return serialized(laneKey,()->snapshot(simulation.changeMode(conversationId,mode,expectedGeneration,ownerAllowed)));}
    @Override public SimulationSnapshot wake(String laneKey,java.util.UUID conversationId,long expectedGeneration,boolean ownerAllowed){return serialized(laneKey,()->snapshot(simulation.wake(conversationId,expectedGeneration,ownerAllowed)));}
    @Override public SimulationSnapshot reset(String laneKey,java.util.UUID conversationId,long expectedGeneration){
        var coordinator=resetCoordinator;if(coordinator==null||resetService==null)throw new IllegalStateException("conversation reset is unavailable");
        ConversationResetCoordinator.ResetTicket ticket; synchronized(coordinator){ticket=coordinator.consoleInterrupt(conversationId,expectedGeneration,laneKey,activeTurns);}
        var result=new java.util.concurrent.CompletableFuture<SimulationSnapshot>();
        try{queue.execute(laneKey,()->{try{processResetCleanup(ticket);result.complete(snapshot(simulation.get(conversationId)));}catch(Throwable failure){result.completeExceptionally(failure);}});}catch(java.util.concurrent.RejectedExecutionException failure){result.completeExceptionally(failure);}
        return await(result);
    }
    private <T> T serialized(String laneKey,java.util.function.Supplier<T> action){if(laneKey==null||laneKey.isBlank())throw new IllegalArgumentException("conversation lane is required");var result=new java.util.concurrent.CompletableFuture<T>();try{queue.execute(laneKey,()->{try{result.complete(action.get());}catch(Throwable failure){result.completeExceptionally(failure);}});}catch(java.util.concurrent.RejectedExecutionException failure){throw new IllegalStateException("conversation runtime queue is unavailable");}return await(result);}
    private static <T> T await(java.util.concurrent.CompletableFuture<T> result){try{return result.get(25,java.util.concurrent.TimeUnit.SECONDS);}catch(java.util.concurrent.ExecutionException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException re)throw re;if(cause instanceof Error error)throw error;throw new IllegalStateException("conversation operation failed");}catch(java.util.concurrent.TimeoutException e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"conversation operation remains queued");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"conversation operation interrupted");}}
    private static SimulationSnapshot snapshot(online.wanan.xingchen.core.agent.SimulationConversationState s){return new SimulationSnapshot(s.mode(),s.wakeState().name(),s.generation(),s.updatedAt()==null?"":s.updatedAt().toString());}
    public synchronized void stop() {
        if (!started.getAndSet(false)) return;
        gateway.setEventListener(null);
        gateway.disconnect();
        reserved.markIdle();
    }

    @Override public void close() {
        stop();
        reserved.close();
        queue.close();
    }
    public record RuntimeQueueDiagnostics(int activeConversationCount,int queuedConversationCount,int queuedEventCount,int resettingConversationCount){}
}
