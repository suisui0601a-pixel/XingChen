package online.wanan.xingchen.core.sticker;

import java.util.*;

public interface StickerRepository {
    StickerMetadata save(StickerMetadata sticker);
    Optional<StickerMetadata> get(UUID id);
    Optional<StickerMetadata> findByHash(String hash);
    List<StickerMetadata> list(int limit);
    List<StickerMetadata> search(String query,int limit);
    Optional<StickerMetadata> updateNote(UUID id,String note);
    Optional<StickerMetadata> recordUse(UUID id,java.time.Instant at);
    default StickerPage page(String query,Boolean enabled,String format,Boolean animated,int offset,int limit){
        List<StickerMetadata> all=(query==null||query.isBlank())?list(500):search(query,500);
        List<StickerMetadata> filtered=all.stream().filter(s->enabled==null||s.enabled()==enabled)
                .filter(s->format==null||format.isBlank()||s.mimeType().endsWith("/"+format.toLowerCase(java.util.Locale.ROOT))||s.fileName().toLowerCase(java.util.Locale.ROOT).endsWith("."+format.toLowerCase(java.util.Locale.ROOT)))
                .filter(s->animated==null||java.util.Objects.equals(s.animated(),animated)).toList();
        int from=Math.min(Math.max(0,offset),filtered.size()),to=Math.min(filtered.size(),from+Math.max(1,Math.min(100,limit)));
        return new StickerPage(filtered.subList(from,to),filtered.size(),from,to-from);
    }
    default Optional<StickerMetadata> updateAdmin(UUID id,java.util.List<String> tags,String note,boolean enabled){throw new UnsupportedOperationException("sticker metadata editing is unsupported");}
    default Optional<StickerMetadata> updateAdmin(UUID id,java.util.List<String> tags,String note,boolean enabled,String expectedUpdatedAt){return updateAdmin(id,tags,note,enabled);}
}
