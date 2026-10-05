package online.wanan.xingchen.core.context;

import java.util.List;
import java.util.UUID;

public record ContextDiagnostics(int totalEstimatedTokens,int personaTokens,int simulationTokens,int memoryTokens,
                                 int recentTokens,int summaryTokens,List<UUID> includedMemoryIds,
                                 int excludedMemoryCount,SessionLifecycleStatus lifecycleState,int toolResultTokens,
                                 int calibratedTokens,int safetyMarginTokens) {
    public ContextDiagnostics(int totalEstimatedTokens,int personaTokens,int simulationTokens,int memoryTokens,int recentTokens,int summaryTokens,List<UUID> includedMemoryIds,int excludedMemoryCount,SessionLifecycleStatus lifecycleState){this(totalEstimatedTokens,personaTokens,simulationTokens,memoryTokens,recentTokens,summaryTokens,includedMemoryIds,excludedMemoryCount,lifecycleState,0,totalEstimatedTokens,0);}
    public ContextDiagnostics(int totalEstimatedTokens,int personaTokens,int simulationTokens,int memoryTokens,int recentTokens,int summaryTokens,List<UUID> includedMemoryIds,int excludedMemoryCount,SessionLifecycleStatus lifecycleState,int toolResultTokens){this(totalEstimatedTokens,personaTokens,simulationTokens,memoryTokens,recentTokens,summaryTokens,includedMemoryIds,excludedMemoryCount,lifecycleState,toolResultTokens,totalEstimatedTokens,0);}
    public ContextDiagnostics { includedMemoryIds=List.copyOf(includedMemoryIds); }
}
