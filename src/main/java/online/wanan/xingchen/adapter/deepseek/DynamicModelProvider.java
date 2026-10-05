package online.wanan.xingchen.adapter.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.console.ModelProviderConfigService;
import online.wanan.xingchen.core.agent.*;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Resolves one immutable provider/credential snapshot at the start of each turn. */
public final class DynamicModelProvider implements ModelProvider {
    private final ModelProviderConfigService config;private final ObjectMapper mapper;
    public DynamicModelProvider(ModelProviderConfigService config,ObjectMapper mapper){this.config=Objects.requireNonNull(config);this.mapper=Objects.requireNonNull(mapper);}
    @Override public ModelProvider snapshot(){var snapshot=config.runtimeSnapshot();if(!snapshot.enabled())return new Unavailable(snapshot.status());return new DeepSeekProvider(snapshot.configuration(),mapper,Map.of("XINGCHEN_DEEPSEEK_API_KEY",snapshot.credential()));}
    @Override public boolean available(){return snapshot().available();}
    @Override public String unavailableReason(){return snapshot().unavailableReason();}
    @Override public ModelResponse complete(ModelRequest request){ModelProvider p=snapshot();if(!p.available())throw new IllegalStateException("model provider is unavailable");return p.complete(request);}
    @Override public ModelResponse stream(ModelRequest request,Consumer<ModelStreamEvent> onEvent){ModelProvider p=snapshot();if(!p.available())throw new IllegalStateException("model provider is unavailable");return p.stream(request,onEvent);}
    private record Unavailable(String status) implements ModelProvider {
        @Override public ModelProvider snapshot(){return this;}@Override public boolean available(){return false;}@Override public String unavailableReason(){return "provider-"+status.toLowerCase(java.util.Locale.ROOT).replace('_','-');}
        @Override public ModelResponse complete(ModelRequest request){throw new IllegalStateException("model provider is unavailable");}
    }
}
