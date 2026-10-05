package online.wanan.xingchen.core.agent;

import java.util.Map;
import java.util.Objects;

public record ModelToolCall(String id,String name,Map<String,Object> arguments) {
    public ModelToolCall { Objects.requireNonNull(id);Objects.requireNonNull(name);arguments=arguments==null?Map.of():Map.copyOf(arguments); }
    public ToolCall toToolCall(){return new ToolCall(name,arguments,id);}
}
