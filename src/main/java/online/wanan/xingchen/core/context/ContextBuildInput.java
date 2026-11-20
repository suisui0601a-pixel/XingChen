package online.wanan.xingchen.core.context;

import online.wanan.xingchen.core.memory.Memory;
import online.wanan.xingchen.core.memory.RequestContext;
import online.wanan.xingchen.core.model.ConversationIdentity;
import java.util.*;

public record ContextBuildInput(String simulationPrompt,String personaPrompt,String actorId,String displayName,
                                String address,String conversationId,Collection<Memory> memories,
                                String conversationSummary,List<String> recentMessages,
                                Map<String,String> taskState,String currentMessage,String toolDescription,
                                RequestContext requestContext,List<String> toolResults,ConversationIdentity conversationIdentity,List<ContextMessage> historyMessages) {
    public ContextBuildInput(String simulationPrompt,String personaPrompt,String actorId,String displayName,String address,String conversationId,Collection<Memory> memories,String conversationSummary,List<String> recentMessages,Map<String,String> taskState,String currentMessage,String toolDescription,RequestContext requestContext){this(simulationPrompt,personaPrompt,actorId,displayName,address,conversationId,memories,conversationSummary,recentMessages,taskState,currentMessage,toolDescription,requestContext,List.of(),null);}
    public ContextBuildInput(String simulationPrompt,String personaPrompt,String actorId,String displayName,String address,String conversationId,Collection<Memory> memories,String conversationSummary,List<String> recentMessages,Map<String,String> taskState,String currentMessage,String toolDescription,RequestContext requestContext,List<String> toolResults){this(simulationPrompt,personaPrompt,actorId,displayName,address,conversationId,memories,conversationSummary,recentMessages,taskState,currentMessage,toolDescription,requestContext,toolResults,null);}
    public ContextBuildInput(String simulationPrompt,String personaPrompt,String actorId,String displayName,String address,String conversationId,Collection<Memory> memories,String conversationSummary,List<String> recentMessages,Map<String,String> taskState,String currentMessage,String toolDescription,RequestContext requestContext,List<String> toolResults,ConversationIdentity conversationIdentity){this(simulationPrompt,personaPrompt,actorId,displayName,address,conversationId,memories,conversationSummary,recentMessages,taskState,currentMessage,toolDescription,requestContext,toolResults,conversationIdentity,List.of());}
    public ContextBuildInput { if(conversationIdentity!=null&&!Objects.equals(conversationId,conversationIdentity.platformConversationId()))throw new IllegalArgumentException("conversation target mismatch");memories=memories==null?List.of():List.copyOf(memories);recentMessages=recentMessages==null?List.of():List.copyOf(recentMessages);taskState=taskState==null?Map.of():Map.copyOf(taskState);toolResults=toolResults==null?List.of():List.copyOf(toolResults);historyMessages=historyMessages==null?List.of():List.copyOf(historyMessages); }
}
