package online.wanan.xingchen.adapter.deepseek;

import online.wanan.xingchen.core.agent.ModelRequest;
import java.util.*;

/** Immutable, request-local transport spelling. Never changes canonical tool identities or permissions. */
public final class ToolNameCodec {
    private final Map<String,String> internalToWire;
    private final Map<String,String> wireToInternal;
    private final Set<String> offered;

    public ToolNameCodec(Collection<String> internalNames) { this(internalNames,internalNames); }
    private ToolNameCodec(Collection<String> names,Collection<String> offeredNames) {
        Map<String,String> outbound=new LinkedHashMap<>(),inbound=new LinkedHashMap<>();
        for(String internal:names) {
            if(internal==null||internal.isEmpty())throw invalid();
            String wire=internal.replace('.','_');
            if(!wire.matches("[A-Za-z0-9_-]{1,128}"))throw invalid();
            String previous=inbound.putIfAbsent(wire,internal);
            if(previous!=null&&!previous.equals(internal))throw invalid();
            outbound.put(internal,wire);
        }
        internalToWire=Map.copyOf(outbound);wireToInternal=Map.copyOf(inbound);
        offered=Set.copyOf(offeredNames);
    }
    public static ToolNameCodec forRequest(ModelRequest request) {
        Set<String> offered=new LinkedHashSet<>(),all=new LinkedHashSet<>();
        request.toolDefinitions().forEach(t->offered.add(t.name()));all.addAll(offered);
        request.messages().forEach(m->{
            m.toolCalls().forEach(c->all.add(c.name()));
            if(m.role()==online.wanan.xingchen.core.agent.ModelRole.TOOL&&m.name()!=null)all.add(m.name());
        });
        return new ToolNameCodec(all,offered);
    }
    public String toWire(String internal) {
        String wire=internalToWire.get(internal);
        if(wire==null)throw invalid();
        return wire;
    }
    public String toInternal(String wire) {
        String internal=wireToInternal.get(wire);
        if(internal==null||!offered.contains(internal))
            throw new ModelProviderException("DeepSeek returned an unknown or unoffered tool name",0,false,null);
        return internal;
    }
    private static ModelProviderException invalid() {
        return new ModelProviderException("DeepSeek tool name mapping is invalid or colliding",0,false,null);
    }
}
