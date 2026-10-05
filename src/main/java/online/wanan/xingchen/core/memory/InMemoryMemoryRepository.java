package online.wanan.xingchen.core.memory;

import java.util.*;
import java.util.stream.Stream;

/** Deterministic repository for pure domain tests; SQLite adapter follows the same contract. */
public final class InMemoryMemoryRepository implements MemoryRepository {
    private final Map<UUID, Memory> records = new LinkedHashMap<>();
    private final Map<UUID, List<MemorySource>> provenance = new HashMap<>();
    public Memory save(Memory memory, Collection<MemorySource> sources) {
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("long-term memory requires provenance");
        if (sources.stream().anyMatch(s -> !s.memoryId().equals(memory.id()))) throw new IllegalArgumentException("source memory id mismatch");
        records.put(memory.id(), memory); provenance.put(memory.id(), List.copyOf(sources)); return memory;
    }
    public Optional<Memory> get(UUID id) { return Optional.ofNullable(records.get(id)); }
    public Memory update(Memory memory) { if (!records.containsKey(memory.id())) throw new NoSuchElementException("memory not found"); records.put(memory.id(), memory); return memory; }
    public boolean delete(UUID id) { Memory m=records.get(id); if (m==null || m.status()==MemoryStatus.SOFT_DELETED) return false; records.put(id, new Memory(m.id(),m.type(),m.subjectPersonId(),m.conversationId(),m.projectKey(),m.content(),m.scopeType(),m.scopeId(),m.confidence(),m.importance(),m.explicit(),m.createdAt(),m.updatedAt(),m.lastAccessedAt(),m.expiresAt(),MemoryStatus.SOFT_DELETED)); return true; }
    public List<Memory> findBySubject(UUID id, RequestContext r) { return select(r, m -> Objects.equals(m.subjectPersonId(),id)); }
    public List<Memory> findByConversation(UUID id, RequestContext r) { return select(r, m -> Objects.equals(m.conversationId(),id)); }
    public List<Memory> findByType(MemoryType t, RequestContext r) { return select(r, m -> m.type()==t); }
    public List<Memory> searchText(String query, RequestContext r, int limit) { String q=query.toLowerCase(Locale.ROOT); return select(r,m -> m.content().toLowerCase(Locale.ROOT).contains(q)).stream().limit(limit).toList(); }
    public List<Memory> findRelevantCandidates(RequestContext r, int limit) { return select(r,m -> true).stream().sorted(Comparator.comparingDouble(Memory::importance).reversed()).limit(limit).toList(); }
    public List<MemorySource> sources(UUID id) { return provenance.getOrDefault(id,List.of()); }
    private List<Memory> select(RequestContext r, java.util.function.Predicate<Memory> predicate) { return records.values().stream().filter(m -> MemoryVisibility.canRead(m,r)).filter(predicate).toList(); }
}
