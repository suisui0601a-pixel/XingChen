package online.wanan.xingchen.core.agent;

import java.util.List;

public record ModelMessage(ModelRole role,String content,String toolCallId,String name,List<ModelToolCall> toolCalls) {
    public ModelMessage { if(role==null)throw new IllegalArgumentException("message role is required");content=content==null?"":content;toolCalls=toolCalls==null?List.of():List.copyOf(toolCalls); }
    public static ModelMessage system(String text){return new ModelMessage(ModelRole.SYSTEM,text,null,null,List.of());}
    public static ModelMessage user(String text){return new ModelMessage(ModelRole.USER,text,null,null,List.of());}
    public static ModelMessage assistant(String text){return new ModelMessage(ModelRole.ASSISTANT,text,null,null,List.of());}
    public static ModelMessage assistantTools(String text,List<ModelToolCall> calls){return new ModelMessage(ModelRole.ASSISTANT,text,null,null,calls);}
    public static ModelMessage tool(ModelToolResult result){return new ModelMessage(ModelRole.TOOL,result.promptContent(),result.toolCallId(),result.name(),List.of());}
}
