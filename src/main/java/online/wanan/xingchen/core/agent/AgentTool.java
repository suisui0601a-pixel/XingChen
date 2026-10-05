package online.wanan.xingchen.core.agent;

import java.util.Map;

public record AgentTool(String name,AgentCapability capability,String description,Map<String,Object> inputSchema) {
    public AgentTool(String name,AgentCapability capability,String description){this(name,capability,description,Map.of("type","object","properties",Map.of()));}
    public AgentTool { inputSchema=inputSchema==null?Map.of("type","object","properties",Map.of()):Map.copyOf(inputSchema); }
}
