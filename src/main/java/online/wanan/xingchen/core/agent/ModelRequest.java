package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.context.ContextPackage;
import java.util.*;

public record ModelRequest(ContextPackage context,List<AgentTool> tools,List<ModelMessage> messages,
                           int maxOutputTokens,CancellationToken cancellation,ModelTurnExecutionState turnState) {
    public ModelRequest(ContextPackage context,List<AgentTool> tools){this(context,tools,defaultMessages(context),1024,new CancellationToken(),new ModelTurnExecutionState());}
    public ModelRequest(ContextPackage context,List<AgentTool> tools,List<ModelMessage> messages){this(context,tools,messages,1024,new CancellationToken(),new ModelTurnExecutionState());}
    public ModelRequest(ContextPackage context,List<AgentTool> tools,List<ModelMessage> messages,int maxOutputTokens,CancellationToken cancellation){this(context,tools,messages,maxOutputTokens,cancellation,new ModelTurnExecutionState());}
    public ModelRequest { tools=tools==null?List.of():List.copyOf(tools);messages=messages==null?defaultMessages(context):List.copyOf(messages);if(maxOutputTokens<1)throw new IllegalArgumentException("maxOutputTokens must be positive");cancellation=cancellation==null?new CancellationToken():cancellation;turnState=turnState==null?new ModelTurnExecutionState():turnState; }
    public List<ModelToolDefinition> toolDefinitions(){return tools.stream().map(t->new ModelToolDefinition(t.name(),t.description(),t.inputSchema())).toList();}
    private static List<ModelMessage> defaultMessages(ContextPackage context){
        if(context==null)return List.of();String rendered=context.renderSections();int current=rendered.indexOf("\n[Recent Messages]"),tools=rendered.indexOf("\n[Tool Results]");int boundary=current<0?tools:tools<0?current:Math.min(current,tools);String system=boundary<0?rendered:rendered.substring(0,boundary);
        var messages=new ArrayList<ModelMessage>();messages.add(ModelMessage.system(HardSecurityPolicy.TEXT));messages.add(ModelMessage.system(system));if(!context.recentMessages().isEmpty())messages.add(ModelMessage.user("Recent conversation:\n"+String.join("\n",context.recentMessages())));messages.add(ModelMessage.user(context.currentMessage()));return List.copyOf(messages);
    }
}
