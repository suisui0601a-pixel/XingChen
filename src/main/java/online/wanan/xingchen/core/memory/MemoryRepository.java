package online.wanan.xingchen.core.memory;

import java.util.*;

public interface MemoryRepository {
    Memory save(Memory memory, Collection<MemorySource> sources);
    Optional<Memory> get(UUID id);
    Memory update(Memory memory);
    boolean delete(UUID id);
    List<Memory> findBySubject(UUID subjectPersonId, RequestContext request);
    List<Memory> findByConversation(UUID conversationId, RequestContext request);
    List<Memory> findByType(MemoryType type, RequestContext request);
    List<Memory> searchText(String query, RequestContext request, int limit);
    List<Memory> findRelevantCandidates(RequestContext request, int limit);
    List<MemorySource> sources(UUID memoryId);
}
