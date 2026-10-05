package online.wanan.xingchen.core.agent;

import java.util.Map;

public record ModelToolResult(String toolCallId,String name,boolean success,String content,Map<String,Object> data) {
    public ModelToolResult { content=content==null?"":content;data=data==null?Map.of():Map.copyOf(data); }
    public static ModelToolResult from(ModelToolCall call,ToolResult result){return new ModelToolResult(call.id(),call.name(),result.success(),result.message(),result.data());}
    public String promptContent(){return name+": "+content+(data.isEmpty()?"":" "+data);}
}
