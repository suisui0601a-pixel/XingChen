package online.wanan.xingchen.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.slang.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

@Repository public class SqliteSlangRepository implements SlangRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public SqliteSlangRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @Override public SlangEntry save(SlangEntry e){jdbc.update("INSERT INTO slang_entries(id,term,meaning,usage_text,example_text,risk,sources_json,evidence_json,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",e.id().toString(),e.term(),e.meaning(),e.usage(),e.example(),e.risk(),json(e.sources()),json(e.evidence()),e.status().name(),e.createdAt().toString(),e.updatedAt().toString());return e;}
    @Override public List<SlangEntry> search(String q,int limit){String v="%"+q.replace("%","\\%").replace("_","\\_")+"%";return jdbc.query("SELECT * FROM slang_entries WHERE (term LIKE ? ESCAPE '\\' OR meaning LIKE ? ESCAPE '\\') AND status IN ('CANDIDATE','CONFIRMED') ORDER BY updated_at DESC LIMIT ?",(r,n)->map(r),v,v,limit);}
    @Override public List<SlangEntry> searchConfirmed(String q,int limit){String v="%"+q.replace("%","\\%").replace("_","\\_")+"%";return jdbc.query("SELECT * FROM slang_entries WHERE (term LIKE ? ESCAPE '\\' OR meaning LIKE ? ESCAPE '\\') AND status='CONFIRMED' ORDER BY updated_at DESC LIMIT ?",(r,n)->map(r),v,v,Math.max(1,Math.min(500,limit)));}
    @Override public List<SlangEntry> list(SlangStatus status,int limit){return jdbc.query("SELECT * FROM slang_entries WHERE (? IS NULL OR status=?) ORDER BY updated_at DESC LIMIT ?",(r,n)->map(r),status==null?null:status.name(),status==null?null:status.name(),limit);}
    @Override public Optional<SlangEntry> get(UUID id){return getById(id);}
    @Override public SlangPage page(String query,SlangStatus status,String from,String to,int offset,int limit){StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();if(query!=null&&!query.isBlank()){where.append(" AND (term LIKE ? ESCAPE '\\' OR meaning LIKE ? ESCAPE '\\')");String term="%"+query.replace("%","\\%").replace("_","\\_")+"%";args.add(term);args.add(term);}if(status!=null){where.append(" AND status=?");args.add(status.name());}if(from!=null){where.append(" AND created_at>=?");args.add(from);}if(to!=null){where.append(" AND created_at<?");args.add(to);}Long total=jdbc.queryForObject("SELECT COUNT(*) FROM slang_entries"+where,Long.class,args.toArray());int pageLimit=Math.max(1,Math.min(100,limit)),pageOffset=Math.max(0,Math.min(100_000,offset));List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(pageLimit);pageArgs.add(pageOffset);List<SlangEntry> items=jdbc.query("SELECT * FROM slang_entries"+where+" ORDER BY updated_at DESC,created_at DESC LIMIT ? OFFSET ?",(r,n)->map(r),pageArgs.toArray());return new SlangPage(items,total==null?0:total,pageOffset,pageLimit);}
    @Override public Optional<SlangEntry> updateStatus(UUID id,SlangStatus status){if(jdbc.update("UPDATE slang_entries SET status=?,updated_at=? WHERE id=?",status.name(),Instant.now().toString(),id.toString())==0)return Optional.empty();return jdbc.query("SELECT * FROM slang_entries WHERE id=?",(r,n)->map(r),id.toString()).stream().findFirst();}
    @Override public Optional<SlangEntry> updateMeaning(UUID id,String meaning,String expected){String now=Instant.now().toString();if(jdbc.update("UPDATE slang_entries SET meaning=?,updated_at=? WHERE id=? AND updated_at=?",meaning,now,id.toString(),expected)==0)return Optional.empty();return get(id);}
    @Override public Optional<SlangEntry> transition(UUID id,SlangStatus status,String expected){String now=Instant.now().toString();if(jdbc.update("UPDATE slang_entries SET status=?,updated_at=? WHERE id=? AND updated_at=?",status.name(),now,id.toString(),expected)==0)return Optional.empty();return get(id);}
    private Optional<SlangEntry> getById(UUID id){return jdbc.query("SELECT * FROM slang_entries WHERE id=?",(r,n)->map(r),id.toString()).stream().findFirst();}
    private SlangEntry map(java.sql.ResultSet r)throws java.sql.SQLException{try{return new SlangEntry(UUID.fromString(r.getString("id")),r.getString("term"),r.getString("meaning"),r.getString("usage_text"),r.getString("example_text"),r.getString("risk"),mapper.readValue(r.getString("sources_json"),new TypeReference<>(){}),mapper.readValue(r.getString("evidence_json"),new TypeReference<>(){}),SlangStatus.valueOf(r.getString("status")),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")));}catch(Exception e){throw new java.sql.SQLException("invalid slang metadata",e);}}
    private String json(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
}
