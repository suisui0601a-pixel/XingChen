package online.wanan.xingchen.core.agent;

import java.util.Map;

public record ToolCall(String name,Map<String,Object> arguments,String callId) {
    public ToolCall(String name,Map<String,Object> arguments){this(name,arguments,null);}
    public ToolCall{arguments=arguments==null?Map.of():Map.copyOf(arguments);}
}
