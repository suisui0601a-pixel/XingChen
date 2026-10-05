package online.wanan.xingchen.core.slang;

import java.time.Instant;
import java.util.*;

public record SlangEntry(UUID id,String term,String meaning,String usage,String example,String risk,List<String> sources,List<String> evidence,SlangStatus status,Instant createdAt,Instant updatedAt) {
    public SlangEntry {sources=sources==null?List.of():List.copyOf(sources);evidence=evidence==null?List.of():List.copyOf(evidence);}
}
