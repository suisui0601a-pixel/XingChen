package online.wanan.xingchen.console;

import online.wanan.xingchen.core.slang.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Admin lifecycle for the same slang dictionary used by the Agent; evidence is returned only as short safe excerpts. */
@Service
public class SlangConsoleService {
    private static final Pattern SENSITIVE=Pattern.compile("(?i)(api[_ -]?key|token|password|authorization)\\s*[:=]\\s*[^\\s,;]+"),
            CONTROL=Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private final SlangService slang;private final SlangRepository repository;private final JdbcTemplate jdbc;private final Clock clock;
    public SlangConsoleService(SlangService slang,SlangRepository repository,JdbcTemplate jdbc,Clock clock){this.slang=slang;this.repository=repository;this.jdbc=jdbc;this.clock=clock;}

    public Map<String,Object> list(String query,String status,String from,String to,int page,int size){SlangStatus parsed=parseStatus(status);Instant start=parseInstant(from),end=parseInstant(to);if(start!=null&&end!=null&&!start.isBefore(end))throw new IllegalArgumentException("invalid slang time range");int pageSize=Math.max(1,Math.min(100,size)),pageNo=Math.max(0,Math.min(100_000/pageSize,page));var result=repository.page(query,parsed,start==null?null:start.toString(),end==null?null:end.toString(),pageNo*pageSize,pageSize);return Map.of("items",result.items().stream().map(SlangConsoleService::safeView).toList(),"total",result.total(),"page",pageNo,"pageSize",pageSize,"webResearch","DISABLED");}

    @Transactional public Map<String,Object> manual(String term,String meaning,String actor){SlangEntry entry=slang.submit(term,meaning,"","","",List.of("ADMIN_MANUAL"),List.of());audit(actor,entry.id(),"MANUAL_CREATE",null,entry.status());return safeView(entry);}
    @Transactional public Map<String,Object> transition(UUID id,String action,String expectedUpdatedAt,String actor){SlangEntry old=find(id);if(!old.updatedAt().toString().equals(expectedUpdatedAt))throw new java.util.ConcurrentModificationException("slang entry changed");SlangStatus next=switch(Objects.toString(action,"").toUpperCase(Locale.ROOT)){case "CONFIRM"->SlangStatus.CONFIRMED;case "REJECT"->SlangStatus.REJECTED;default->throw new IllegalArgumentException("unsupported slang action");};if(old.status()!=SlangStatus.CANDIDATE)throw new IllegalArgumentException("only candidate slang can be reviewed");SlangEntry saved=repository.transition(id,next,expectedUpdatedAt).orElseThrow(()->new java.util.ConcurrentModificationException("slang entry changed"));audit(actor,id,next==SlangStatus.CONFIRMED?"CONFIRM":"REJECT",old.status(),saved.status());return safeView(saved);}
    @Transactional public Map<String,Object> meaning(UUID id,String meaning,String expectedUpdatedAt,String actor){if(meaning==null||meaning.isBlank()||meaning.length()>500)throw new IllegalArgumentException("meaning must be 1..500 characters");SlangEntry old=find(id);if(!old.updatedAt().toString().equals(expectedUpdatedAt))throw new java.util.ConcurrentModificationException("slang entry changed");SlangEntry saved=repository.updateMeaning(id,meaning.strip(),expectedUpdatedAt).orElseThrow(()->new java.util.ConcurrentModificationException("slang entry changed"));audit(actor,id,"EDIT_MEANING",old.status(),saved.status());return safeView(saved);}
    private SlangEntry find(UUID id){return repository.get(id).orElseThrow(()->new NoSuchElementException("slang entry not found"));}
    private void audit(String actor,UUID id,String action,SlangStatus oldStatus,SlangStatus newStatus){jdbc.update("INSERT INTO slang_console_audit(occurred_at,actor,term_id,action,old_status,new_status) VALUES(?,?,?,?,?,?)",clock.instant().toString(),actor,id.toString(),action,oldStatus==null?null:oldStatus.name(),newStatus==null?null:newStatus.name());}
    private static SlangStatus parseStatus(String value){if(value==null||value.isBlank())return null;try{return SlangStatus.valueOf(value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){throw new IllegalArgumentException("invalid slang status");}}
    private static Instant parseInstant(String value){if(value==null||value.isBlank())return null;try{return Instant.parse(value);}catch(Exception e){throw new IllegalArgumentException("slang time must be ISO-8601 UTC");}}
    private static Map<String,Object> safeView(SlangEntry e){List<String> evidence=e.evidence().stream().limit(3).map(SlangConsoleService::excerpt).toList();List<String> sources=e.sources().stream().limit(10).map(SlangConsoleService::sourceLabel).toList();return Map.ofEntries(Map.entry("id",e.id().toString()),Map.entry("term",e.term()),Map.entry("meaning",excerpt(e.meaning())),Map.entry("usage",excerpt(e.usage())),Map.entry("example",excerpt(e.example())),Map.entry("risk",excerpt(e.risk())),Map.entry("status",e.status().name()),Map.entry("source",sources.contains("ADMIN_MANUAL")?"ADMIN_MANUAL":String.join(", ",sources)),Map.entry("sources",sources),Map.entry("evidenceCount",e.evidence().size()),Map.entry("evidencePreview",evidence),Map.entry("createdAt",e.createdAt().toString()),Map.entry("updatedAt",e.updatedAt().toString()));}
    private static String excerpt(String value){String clean=CONTROL.matcher(Objects.requireNonNullElse(value,"")).replaceAll(" ");clean=SENSITIVE.matcher(clean).replaceAll("$1=[redacted]").strip();int end=clean.offsetByCodePoints(0,Math.min(140,clean.codePointCount(0,clean.length())));return clean.substring(0,end);}
    private static String sourceLabel(String value){String clean=CONTROL.matcher(Objects.requireNonNullElse(value,"")).replaceAll("").strip();return clean.substring(0,Math.min(80,clean.length()));}
}
