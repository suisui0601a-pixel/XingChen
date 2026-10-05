package online.wanan.xingchen.core.agent;

import java.util.Objects;

/** Framework-independent social response policy over an injected model provider. */
public final class SocialAgent {
    private final ModelProvider provider;private final AgentToolCatalog catalog;
    public SocialAgent(ModelProvider provider,AgentToolCatalog catalog){this.provider=Objects.requireNonNull(provider);this.catalog=Objects.requireNonNull(catalog);}
    public AgentDecision decide(AgentRequest request){
        var response=provider.complete(new ModelRequest(request.context(),catalog.available(request.availableCapabilities())));
        AgentDecision decision=response.decision();
        if(response.memoryCandidate()!=null)decision=decision.withMemory(response.memoryCandidate());
        if(decision.type()==DecisionType.TOOL_CALL&&(decision.toolCall()==null||!catalog.allows(decision.toolCall().name(),request.availableCapabilities())))return AgentDecision.noReply();
        if((decision.type()==DecisionType.TEXT_REPLY||decision.type()==DecisionType.MULTI_REPLY)&&decision.messages().stream().anyMatch(s->s==null||s.isBlank()))return AgentDecision.noReply();
        return decision;
    }
}
