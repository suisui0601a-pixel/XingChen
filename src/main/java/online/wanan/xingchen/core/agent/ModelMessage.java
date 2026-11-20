package online.wanan.xingchen.core.agent;

import java.util.List;

public record ModelMessage(ModelRole role,String content,String toolCallId,String name,List<ModelToolCall> toolCalls,List<ModelImage> images) {
    public ModelMessage(ModelRole role,String content,String toolCallId,String name,List<ModelToolCall> toolCalls){this(role,content,toolCallId,name,toolCalls,List.of());}
    public ModelMessage { if(role==null)throw new IllegalArgumentException("message role is required");content=content==null?"":content;toolCalls=toolCalls==null?List.of():List.copyOf(toolCalls);images=images==null?List.of():List.copyOf(images);if(!images.isEmpty()&&role!=ModelRole.USER)throw new IllegalArgumentException("images require user role");if(images.size()>2)throw new IllegalArgumentException("image count limit"); }
    public static ModelMessage system(String text){return new ModelMessage(ModelRole.SYSTEM,text,null,null,List.of());}
    public static ModelMessage user(String text){return new ModelMessage(ModelRole.USER,text,null,null,List.of());}
    public static ModelMessage assistant(String text){return new ModelMessage(ModelRole.ASSISTANT,text,null,null,List.of());}
    public static ModelMessage assistantTools(String text,List<ModelToolCall> calls){return new ModelMessage(ModelRole.ASSISTANT,text,null,null,calls);}
    public static ModelMessage tool(ModelToolResult result){return new ModelMessage(ModelRole.TOOL,result.promptContent(),result.toolCallId(),result.name(),List.of());}
}
