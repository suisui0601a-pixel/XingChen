package online.wanan.xingchen.core.agent;

import java.util.Map;

public record ToolResult(boolean success,String message,Map<String,Object> data) { public ToolResult{data=data==null?Map.of():Map.copyOf(data);} }
