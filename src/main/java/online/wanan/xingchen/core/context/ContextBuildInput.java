package online.wanan.xingchen.core.context;

import online.wanan.xingchen.core.memory.Memory;
import online.wanan.xingchen.core.memory.RequestContext;
import java.util.*;

public record ContextBuildInput(String simulationPrompt,String personaPrompt,String actorId,String displayName,
                                String address,String conversationId,Collection<Memory> memories,
                                String conversationSummary,List<String> recentMessages,
                                Map<String,String> taskState,String currentMessage,String toolDescription,
                                RequestContext requestContext,List<String> toolResults) {
    public ContextBuildInput(String simulationPrompt,String personaPrompt,String actorId,String displayName,String address,String conversationId,Collection<Memory> memories,String conversationSummary,List<String> recentMessages,Map<String,String> taskState,String currentMessage,String toolDescription,RequestContext requestContext){this(simulationPrompt,personaPrompt,actorId,displayName,address,conversationId,memories,conversationSummary,recentMessages,taskState,currentMessage,toolDescription,requestContext,List.of());}
    public ContextBuildInput { memories=memories==null?List.of():List.copyOf(memories);recentMessages=recentMessages==null?List.of():List.copyOf(recentMessages);taskState=taskState==null?Map.of():Map.copyOf(taskState);toolResults=toolResults==null?List.of():List.copyOf(toolResults); }
}
