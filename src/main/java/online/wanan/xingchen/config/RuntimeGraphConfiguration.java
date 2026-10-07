package online.wanan.xingchen.config;

import online.wanan.xingchen.adapter.dsh.DshGateway;
import online.wanan.xingchen.adapter.dsh.DshRc2InteractionOutcomeEncoder;
import online.wanan.xingchen.adapter.dsh.DshSessionService;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.conversation.*;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.model.Platform;
import online.wanan.xingchen.core.prompt.PromptSections;
import online.wanan.xingchen.core.prompt.PromptSnapshotProvider;
import online.wanan.xingchen.core.relationship.*;
import online.wanan.xingchen.core.slang.*;
import online.wanan.xingchen.core.sticker.*;
import online.wanan.xingchen.security.XingChenProperties;
import online.wanan.xingchen.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/** Shared application use-case graph. Adapters are supplied by one explicit runtime owner. */
@Configuration
public class RuntimeGraphConfiguration {
    @Bean @ConditionalOnMissingBean(TurnBoundaryObserver.class) public TurnBoundaryObserver turnBoundaryObserver(){return TurnBoundaryObserver.NOOP;}
    @Bean public ContextBudget contextBudget(XingChenProperties p) { var c=p.context();return new ContextBudget(c.softLimitTokens(),c.rolloverTokens(),c.hardLimitTokens(),c.recentMessageLimit(),c.memoryTokenBudget(),c.summaryTokenBudget()); }
    @Bean public ProviderTokenEstimator tokenEstimator(XingChenProperties p) { return new ProviderTokenEstimator(p.model().provider(),p.model().model().isBlank()?"unknown":p.model().model()); }
    @Bean public ContextBuilder contextBuilder(ContextBudget budget,ProviderTokenEstimator estimator) { return new ContextBuilder(budget,estimator); }
    @Bean public MemoryPolicy memoryPolicy() { return new MemoryPolicy(); }
    @Bean public MemoryRetriever memoryRetriever(MemoryRepository repo,ProviderTokenEstimator estimator) { return new MemoryRetriever(repo,estimator); }
    @Bean public AgentToolCatalog toolRegistry() { return new AgentToolCatalog(); }
    @Bean public SecurityEventSink securityEventSink() { return new InMemorySecurityEventSink(); }
    @Bean public ClosedAgentAccessPolicy closedAgentAccessPolicy() { return new ClosedAgentAccessPolicy(); }
    @Bean public IdentityRegistry identityRegistry(XingChenProperties p,IdentityPersistence persistence) {
        String self=p.onebot().loginUserId();String owner=p.owner().platformUserId();
        return new IdentityRegistry(self,List.of(owner==null?"":owner).stream().filter(s->!s.isBlank()).toList(),persistence);
    }
    @Bean public AddressResolver addressResolver() { return new AddressResolver(); }
    @Bean public RelationshipTermRegistry relationshipTermRegistry(RelationshipTermRepository repository) { return new RelationshipTermRegistry(repository); }
    @Bean public IdentityResolver identityResolver(IdentityRegistry identities,AddressResolver addresses,RelationshipTermRegistry terms,XingChenProperties p) { return new IdentityResolver(identities,addresses,terms,p.onebot().loginUserId()); }
    @Bean public PromptSections promptSections(@Value("${xingchen.prompt.profile-id:8f3e9d9f-77d9-5d99-9cb4-f1eea5678abc}") UUID profile,
            @Value("${xingchen.prompt.simulation-version:1}") int simulationVersion,@Value("${xingchen.prompt.persona-version:1}") int personaVersion,
            @Value("${xingchen.prompt.simulation:}") String simulation,@Value("${xingchen.prompt.persona:}") String persona) {
        return new PromptSections(profile,simulationVersion,personaVersion,simulation,persona);
    }
    @Bean public ConversationWindow conversationWindow() { return new ConversationWindow(); }
    @Bean public MockSummaryProvider sessionSummaryProvider() { return new MockSummaryProvider(); }
    @Bean public StickerPolicy stickerPolicy() { return new StickerPolicy(1,Duration.ofSeconds(30)); }
    @Bean public StickerService stickerService(DataPathResolver paths,StickerRepository repository,StickerPolicy policy,OneBotGateway gateway,Clock clock) { return new StickerService(paths.stickerRoot(),repository,policy,gateway,clock); }
    @Bean public StickerAssetService stickerAssetService(DataPathResolver paths,StickerService stickers,StickerRepository repository) { return new StickerAssetService(paths.stickerRoot(),stickers,repository); }
    @Bean public SlangService slangService(SlangRepository repository,Clock clock) { return new SlangService(repository,clock); }
    @Bean public PendingInteractionService pendingInteractionService(org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.fasterxml.jackson.databind.ObjectMapper mapper,Clock clock,XingChenProperties properties) {
        String owner=properties.owner().platformUserId();return new PendingInteractionService(jdbc,mapper,clock,owner==null||owner.isBlank()?Set.of():Set.of(owner));
    }
    @Bean public DshPendingInteractionStore dshPendingInteractionStore(org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.fasterxml.jackson.databind.ObjectMapper mapper,Clock clock) {
        return new DshPendingInteractionStore(jdbc,mapper,clock);
    }
    @Bean public QuestionAnswerParser questionAnswerParser(){return new QuestionAnswerParser();}
    @Bean public DshRc2InteractionOutcomeEncoder dshRc2InteractionOutcomeEncoder(com.fasterxml.jackson.databind.ObjectMapper mapper){return new DshRc2InteractionOutcomeEncoder(mapper);}
    @Bean public HumanInteractionRouter humanInteractionRouter(IdentityResolver identities,DshPendingInteractionStore pending,
            DshGateway dsh,OneBotGateway onebot,DshRc2InteractionOutcomeEncoder encoder,QuestionAnswerParser parser,
            com.fasterxml.jackson.databind.ObjectMapper mapper,Clock clock){return new HumanInteractionRouter(identities,pending,dsh,onebot,encoder,parser,mapper,clock);}
    @Bean(destroyMethod="close") public DshInteractionCoordinator dshInteractionCoordinator(DshGateway dsh,SqliteDshSessionMappingRepository mappings,
            org.springframework.jdbc.core.JdbcTemplate jdbc,DshPendingInteractionStore pending,OneBotGateway onebot,
            com.fasterxml.jackson.databind.ObjectMapper mapper,Clock clock,XingChenProperties properties){
        return new DshInteractionCoordinator(dsh,mappings,jdbc,pending,onebot,mapper,clock,properties.owner().platformUserId());
    }
    @Bean public SocialToolExecutor socialToolExecutor(OneBotGateway gateway,AgentToolCatalog catalog,MemoryRepository memories,
            MemoryRetriever retriever,MemoryPolicy policy,IdentityRegistry identities,RelationshipTermRegistry terms,
            XingChenProperties p,StickerService stickers,SlangService slang,Clock clock,SimulationStateService simulation,OutboundExecutionService outbound) {
        return new SocialToolExecutor(gateway,catalog,memories,retriever,policy,identities,terms,p.onebot().loginUserId(),stickers,slang,clock,simulation,outbound);
    }
    @Bean public AgentExecutor agentExecutor(online.wanan.xingchen.core.agent.ModelProvider provider,SocialToolExecutor tools,
            ContextBuilder contexts,AgentToolCatalog catalog,SecurityEventSink security,SqliteUsageLedger usage,
            SqliteAgentTraceStore traces,SqliteToolExecutionLedger ledger,TurnBoundaryObserver boundaryObserver) {
        return AgentExecutor.production(provider,tools,contexts,catalog,security,8,contexts.budget().hardLimitTokens(),8000,
                new ApproximateTokenEstimator(),usage,traces,ledger,boundaryObserver);
    }
    @Bean public ConversationOrchestrator conversationOrchestrator(IdentityResolver identities,IncomingMessageStore messages,
            MemoryRetriever retriever,MemoryRepository memories,MemoryPolicy policy,ContextBuilder contexts,PromptSections prompts,
            AgentExecutor executor,OneBotGateway gateway,ConversationWindow window,MockSummaryProvider summaries,
            SessionLifecycleManager lifecycle,ContextDiagnosticsStore diagnostics,OutboundExecutionService outbound,DurableTurnExecutionStore turnJournal,TurnBoundaryObserver boundaryObserver,PromptSnapshotProvider snapshots) {
        var result=new ConversationOrchestrator(identities,messages,retriever,memories,policy,contexts,prompts,executor,
                CapabilityPolicy.capabilities(AgentRole.SOCIAL,false),gateway,window,summaries,lifecycle,diagnostics,outbound,turnJournal,boundaryObserver);result.setPromptSnapshotProvider(snapshots);return result;
    }
    @Bean public SocialTriggerPolicy socialTriggerPolicy(XingChenProperties p) { return new SocialTriggerPolicy(p.onebot().loginUserId(),List.of()); }
    @Bean public ParticipationPolicy participationPolicy() { return new ParticipationPolicy(()->0.0); }
    @Bean public ReservedModeMachine reservedModeMachine(Clock clock,Scheduler scheduler) { return new ReservedModeMachine(clock,scheduler,Duration.ofMinutes(10)); }
    @Bean(destroyMethod="close") public SocialRuntime socialRuntime(OneBotGateway gateway,ConversationOrchestrator orchestrator,
            SocialTriggerPolicy triggers,ParticipationPolicy participation,ReservedModeMachine reserved,HumanInteractionRouter interactions,
            SimulationStateService simulation,DurableTurnExecutionStore turnJournal,ShortContextResetService resetService,
            ConversationResetCoordinator resetCoordinator,XingChenProperties properties,RuntimeIntegrationStatus switches,
            online.wanan.xingchen.console.SocialSettingsService socialSettings,online.wanan.xingchen.console.AccessControlService accessControl) {
        var runtime=new SocialRuntime(gateway,orchestrator,triggers,participation,reserved,()->0.0,
                event->switches.dshEnabled()&&interactions.route(event),simulation,turnJournal);
        runtime.setResetService(resetService);runtime.setResetControl(resetCoordinator,properties.owner().platformUserId());runtime.setSocialSettingsService(socialSettings);runtime.setAccessControlService(accessControl);return runtime;
    }
    @Bean public DshSessionService dshSessionService(SqliteDshSessionMappingRepository mappings,DshGateway gateway,Clock clock,
            @Value("${xingchen.dsh.workspace-path:${user.dir}}") String workspace) {
        return new DshSessionService(mappings,gateway,clock,Path.of(workspace));
    }
    @Bean public ShortContextResetService shortContextResetService(DshSessionService sessions,ConversationWindow window,org.springframework.jdbc.core.JdbcTemplate jdbc,
            SimulationStateService simulation,DurableTurnExecutionStore turns,DshPendingInteractionStore interactions){return new ShortContextResetService(sessions,window,jdbc,simulation,turns,interactions);}
    @Bean public ConversationResetCoordinator conversationResetCoordinator(org.springframework.jdbc.core.JdbcTemplate jdbc,SimulationStateService simulation){return new ConversationResetCoordinator(jdbc,simulation);}
}
