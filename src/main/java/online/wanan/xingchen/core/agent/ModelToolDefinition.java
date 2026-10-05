package online.wanan.xingchen.core.agent;

import java.util.Map;

public record ModelToolDefinition(String name,String description,Map<String,Object> inputSchema) {
    public ModelToolDefinition { inputSchema=inputSchema==null?Map.of("type","object","properties",Map.of()):Map.copyOf(inputSchema); }
}
