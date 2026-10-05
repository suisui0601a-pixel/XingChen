package online.wanan.xingchen.core.memory;

import java.util.UUID;

public record MemoryCandidate(UUID subjectPersonId,MemoryType type,String content,MemoryScope requestedScope,
                              UUID conversationId,String projectKey,double confidence,double importance,
                              boolean explicit,String sourceMessageId) {
    public MemoryCandidate { if(type==null||content==null||content.isBlank()||requestedScope==null)throw new IllegalArgumentException("memory candidate needs type/content/scope"); }
}
