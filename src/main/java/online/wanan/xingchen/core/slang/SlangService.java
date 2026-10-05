package online.wanan.xingchen.core.slang;

import java.time.*;
import java.util.*;

/** Offline slang dictionary; candidate submissions never trigger network research. */
public final class SlangService {
    private final SlangRepository repository;private final Clock clock;
    public SlangService(SlangRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    public SlangEntry submit(String term,String meaning,String usage,String example,String risk,List<String> sources,List<String> evidence){String t=clean(term,80,"term"),m=clean(meaning,500,"meaning");Instant now=clock.instant();return repository.save(new SlangEntry(UUID.randomUUID(),t,m,cleanOptional(usage),cleanOptional(example),cleanOptional(risk),bounded(sources),bounded(evidence),SlangStatus.CANDIDATE,now,now));}
    public List<SlangEntry> search(String q,int limit){if(q==null||q.isBlank())return List.of();return repository.searchConfirmed(q.trim(),Math.min(100,Math.max(1,limit)));}
    public List<SlangEntry> list(SlangStatus status,int limit){return repository.list(status,Math.min(500,Math.max(1,limit)));}
    private static String clean(String s,int max,String field){if(s==null||s.isBlank()||s.length()>max)throw new IllegalArgumentException("invalid slang "+field);return s.trim();}
    private static String cleanOptional(String s){if(s==null)return "";if(s.length()>1000)throw new IllegalArgumentException("slang field too long");return s.trim();}
    private static List<String> bounded(List<String> values){if(values==null)return List.of();return values.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isBlank()).map(s->s.substring(0,Math.min(500,s.length()))).limit(20).toList();}
}
