package online.wanan.xingchen.core.context;

import online.wanan.xingchen.core.memory.Memory;
import java.util.List;
import java.util.Map;

public record ContextPackage(String persona, String simulationPrompt, String actorId, String conversationId,
                             String relationshipAddress, List<Memory> memories, List<String> recentMessages,
                             Map<String,String> taskState, String currentMessage, int estimatedTokens,
                             SessionLifecycleStatus lifecycleStatus,String displayName,String identityBlock,
                             String relationshipBlock,String conversationSummary,String toolDescription,
                             ContextDiagnostics diagnostics,List<String> toolResults) {
    public ContextPackage(String persona,String simulationPrompt,String actorId,String conversationId,String relationshipAddress,List<Memory> memories,List<String> recentMessages,Map<String,String> taskState,String currentMessage,int estimatedTokens,SessionLifecycleStatus lifecycleStatus,String displayName,String identityBlock,String relationshipBlock,String conversationSummary,String toolDescription,ContextDiagnostics diagnostics){this(persona,simulationPrompt,actorId,conversationId,relationshipAddress,memories,recentMessages,taskState,currentMessage,estimatedTokens,lifecycleStatus,displayName,identityBlock,relationshipBlock,conversationSummary,toolDescription,diagnostics,List.of());}
    public ContextPackage(String persona,String simulationPrompt,String actorId,String conversationId,String relationshipAddress,
                          List<Memory> memories,List<String> recentMessages,Map<String,String> taskState,String currentMessage,
                          int estimatedTokens,SessionLifecycleStatus lifecycleStatus){
        this(persona,simulationPrompt,actorId,conversationId,relationshipAddress,memories,recentMessages,taskState,currentMessage,
                estimatedTokens,lifecycleStatus,actorId,actorId,"称呼: "+relationshipAddress,"","",null,List.of());
    }
    public String renderSections(){
        String memoryText=memories.stream().map(Memory::content).reduce((a,b)->a+"\n"+b).orElse("");
        String taskText=taskState.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+"\n"+b).orElse("");
        return "[Identity]\n"+identityBlock+"\n[Persona Prompt]\n"+persona+"\n[Simulation Prompt]\n"+simulationPrompt+
                "\n[Relationship]\n"+relationshipBlock+"\n[Relevant Memories]\n"+memoryText+"\n[Conversation Summary]\n"+conversationSummary+"\n[Task State]\n"+taskText+
                "\n[Agent Tools]\n"+toolDescription+"\n[Tool Results]\n"+String.join("\n",toolResults)+"\n[Recent Messages]\n"+String.join("\n",recentMessages)+"\n[Current Message]\n"+currentMessage;
    }
}
