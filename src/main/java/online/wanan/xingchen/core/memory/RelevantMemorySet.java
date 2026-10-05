package online.wanan.xingchen.core.memory;

import java.util.List;

public record RelevantMemorySet(List<Memory> memories,int estimatedTokens,int excludedCount) {
    public RelevantMemorySet { memories=List.copyOf(memories); }
}
