package online.wanan.xingchen.core.agent;

import java.util.function.Consumer;

public interface ModelProvider {
    ModelResponse complete(ModelRequest request);
    /** Freezes mutable provider configuration once per agent turn. Immutable providers return themselves. */
    default ModelProvider snapshot(){return this;}
    default boolean available(){return true;}
    default String unavailableReason(){return "provider-unavailable";}
    default ModelResponse stream(ModelRequest request,Consumer<ModelStreamEvent> onEvent){ModelResponse r=complete(request);if(r.decision()!=null)r.decision().messages().forEach(t->onEvent.accept(new ModelStreamEvent(t,false,null)));if(!r.toolCalls().isEmpty())request.turnState().toolProposed();onEvent.accept(new ModelStreamEvent("",true,r.usage()));return r;}
}
