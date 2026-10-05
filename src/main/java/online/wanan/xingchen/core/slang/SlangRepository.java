package online.wanan.xingchen.core.slang;

import java.util.*;

public interface SlangRepository {
    SlangEntry save(SlangEntry e);List<SlangEntry> search(String query,int limit);List<SlangEntry> list(SlangStatus status,int limit);Optional<SlangEntry> updateStatus(UUID id,SlangStatus status);
    default List<SlangEntry> searchConfirmed(String query,int limit){return search(query,Math.min(500,Math.max(1,limit))).stream().filter(e->e.status()==SlangStatus.CONFIRMED).limit(Math.max(1,limit)).toList();}
    default Optional<SlangEntry> get(UUID id){return list(null,500).stream().filter(e->e.id().equals(id)).findFirst();}
    default SlangPage page(String query,SlangStatus status,String from,String to,int offset,int limit){List<SlangEntry> values=query==null||query.isBlank()?list(status,500):search(query,500).stream().filter(e->status==null||e.status()==status).toList();int start=Math.min(Math.max(0,offset),values.size()),end=Math.min(values.size(),start+Math.max(1,Math.min(100,limit)));return new SlangPage(values.subList(start,end),values.size(),start,end-start);}
    default Optional<SlangEntry> updateMeaning(UUID id,String meaning,String expectedUpdatedAt){throw new UnsupportedOperationException("slang meaning editing is unsupported");}
    default Optional<SlangEntry> transition(UUID id,SlangStatus status,String expectedUpdatedAt){throw new UnsupportedOperationException("slang status transition is unsupported");}
}
