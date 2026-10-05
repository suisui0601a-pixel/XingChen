package online.wanan.xingchen.console;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class UsageConsoleService {
    private final JdbcTemplate jdbc;
    public UsageConsoleService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private record Range(Instant from, Instant to) {}
    private Range range(String window, String from, String to) {
        Instant end = Instant.now();
        Instant start = switch (window == null ? "7d" : window) {
            case "24h" -> end.minus(Duration.ofHours(24));
            case "3d" -> end.minus(Duration.ofDays(3));
            case "7d" -> end.minus(Duration.ofDays(7));
            case "30d" -> end.minus(Duration.ofDays(30));
            case "custom" -> { try { yield LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant(); }
                catch (Exception e) { throw new IllegalArgumentException("Invalid UTC start date"); } }
            default -> throw new IllegalArgumentException("Unsupported time window");
        };
        if ("custom".equals(window)) try { end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid UTC end date"); }
        if (!start.isBefore(end) || Duration.between(start,end).compareTo(Duration.ofDays(90)) > 0)
            throw new IllegalArgumentException("Time range must be within 90 days");
        return new Range(start,end);
    }
    private void checkPerson(String person) {
        if (empty(person) != null) throw new IllegalArgumentException("Person attribution is not present in the usage ledger");
    }
    private Object[] args(Range r, String p, String m, String c) {
        return new Object[]{r.from.toString(), r.to.toString(), empty(p), empty(p), empty(m), empty(m), empty(c), empty(c)};
    }
    private static final String WHERE = " WHERE u.usage_time>=? AND u.usage_time<? AND (? IS NULL OR u.provider=?) AND (? IS NULL OR u.model=?) AND (? IS NULL OR u.conversation_id=?)";
    @Transactional(readOnly=true)
    public Map<String,Object> summary(String w, String f, String t, String p, String m, String c, String person) {
        checkPerson(person); Range r = range(w,f,t);
        Map<String,Object> counters = jdbc.queryForMap("SELECT COUNT(*) requests,COALESCE(SUM(u.input_tokens),0) inputTokens,COALESCE(SUM(u.cache_hit_tokens),0) cacheHitTokens,COALESCE(SUM(u.cache_miss_tokens),0) cacheMissTokens,COALESCE(SUM(u.output_tokens),0) outputTokens,COALESCE(SUM(u.reasoning_tokens),0) reasoningTokens,COALESCE(SUM(u.input_tokens+u.output_tokens),0) totalTokens,COALESCE(MIN(u.reasoning_available),0) reasoningAvailable FROM token_usage u" + WHERE, args(r,p,m,c));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("from",r.from.toString()); result.put("to",r.to.toString()); result.put("summary",counters);
        result.put("reasoningAvailable",((Number)counters.get("reasoningAvailable")).intValue()==1);
        result.put("personAttributionAvailable",false);
        result.put("cacheSemantics","Cache hit/miss are subsets of input tokens and are not added to total a second time");
        result.put("reasoningSemantics","Reasoning tokens are a subset of output tokens; not added twice");
        result.put("costSemantics","CURRENT_RATE_ESTIMATE_NOT_BILLING_NO_FX");
        result.put("cost",cost(r,p,m,c));
        return result;
    }
    private Map<List<String>,UsageCostAccounting.Price> prices() {
        Map<List<String>,UsageCostAccounting.Price> result = new HashMap<>();
        jdbc.query("SELECT provider,model,input,cache_hit,cache_miss,output,reasoning,currency FROM console_pricing", (RowCallbackHandler) rs -> {
            List<BigDecimal> rates = new ArrayList<>();
            for (String column : List.of("input","cache_hit","cache_miss","output","reasoning")) {
                String raw = rs.getString(column); rates.add(raw == null ? null : new BigDecimal(raw));
            }
            result.put(List.of(rs.getString("provider"),rs.getString("model")), new UsageCostAccounting.Price(rs.getString("currency"),rates));
        });
        return result;
    }
    private UsageCostAccounting.Summary cost(Range r,String p,String m,String c) {
        var prices = prices(); var totals = new UsageCostAccounting.Accumulator();
        // Presence grouping ensures a missing rate invalidates only rows using it.
        // Stream grouped integers; never load all usage rows or sum floating money.
        String base = "SELECT u.provider,u.model,COUNT(*) requests,SUM(u.input_tokens+u.output_tokens) totalTokens,SUM(MAX(0,u.input_tokens-u.cache_hit_tokens-u.cache_miss_tokens)) input,SUM(u.cache_hit_tokens) cacheHit,SUM(u.cache_miss_tokens) cacheMiss,SUM(MAX(0,u.output_tokens-u.reasoning_tokens)) output,SUM(u.reasoning_tokens) reasoning FROM token_usage u";
        String group = " GROUP BY u.provider,u.model,(u.input_tokens-u.cache_hit_tokens-u.cache_miss_tokens>0),(u.cache_hit_tokens>0),(u.cache_miss_tokens>0),(u.output_tokens-u.reasoning_tokens>0),(u.reasoning_tokens>0)";
        jdbc.query(base + WHERE + group,(RowCallbackHandler) rs -> {
            long[] tokens = new long[5]; for(int i=0;i<5;i++) tokens[i] = rs.getLong(UsageCostAccounting.CATEGORIES.get(i));
            totals.add(rs.getLong("requests"),rs.getLong("totalTokens"),UsageCostAccounting.value(tokens,prices.get(List.of(rs.getString("provider"),rs.getString("model")))));
        },args(r,p,m,c));
        return totals.summary();
    }
    public List<Map<String,Object>> series(String w,String f,String t,String p,String m,String c,String person) {
        checkPerson(person); Range r=range(w,f,t);
        // Token-only series: no monetary sum or cross-currency ranking.
        return jdbc.queryForList("SELECT substr(u.usage_time,1,10) day,COUNT(*) requests,SUM(u.input_tokens) inputTokens,SUM(u.cache_hit_tokens) cacheHitTokens,SUM(u.cache_miss_tokens) cacheMissTokens,SUM(u.output_tokens) outputTokens,SUM(u.reasoning_tokens) reasoningTokens,MIN(u.reasoning_available) reasoningAvailable FROM token_usage u" + WHERE + " GROUP BY substr(u.usage_time,1,10) ORDER BY day",args(r,p,m,c));
    }
    public Map<String,Object> breakdown(String w,String f,String t) {
        Range r=range(w,f,t);
        // Explicitly token-only, sorted by requests/time, never by cost.
        return Map.of("metric","TOKENS_ONLY","providers",jdbc.queryForList("SELECT provider,model,COUNT(*) requests,SUM(input_tokens) inputTokens,SUM(cache_hit_tokens) cacheHitTokens,SUM(cache_miss_tokens) cacheMissTokens,SUM(output_tokens) outputTokens,SUM(reasoning_tokens) reasoningTokens FROM token_usage WHERE usage_time>=? AND usage_time<? GROUP BY provider,model ORDER BY requests DESC LIMIT 200",r.from.toString(),r.to.toString()),"conversations",jdbc.queryForList("SELECT u.conversation_id conversationId,c.type conversationType,c.platform_conversation_id platformConversationId,COUNT(*) requests,SUM(u.input_tokens+u.output_tokens) totalTokens FROM token_usage u LEFT JOIN conversations c ON c.id=u.conversation_id WHERE u.usage_time>=? AND u.usage_time<? GROUP BY u.conversation_id,c.type,c.platform_conversation_id ORDER BY MAX(u.usage_time) DESC LIMIT 200",r.from.toString(),r.to.toString()),"people",List.of(),"personAttributionAvailable",false);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> records(String w,String f,String t,String p,String m,String c,String person,int page,int size) {
        if(page<0||size<1||size>100) throw new IllegalArgumentException("Invalid page bounds");
        checkPerson(person); Range r=range(w,f,t);
        List<Object> parameters=new ArrayList<>(Arrays.asList(args(r,p,m,c))); parameters.add(size); parameters.add((long)page*size);
        var rows=jdbc.queryForList("SELECT u.id,u.provider,u.model,u.conversation_id conversationId,u.turn_id turnId,u.usage_time usageTime,u.input_tokens inputTokens,u.cache_hit_tokens cacheHitTokens,u.cache_miss_tokens cacheMissTokens,u.output_tokens outputTokens,u.reasoning_tokens reasoningTokens,u.reasoning_available reasoningAvailable,u.duration_ms durationMillis FROM token_usage u" + WHERE + " ORDER BY u.usage_time DESC,u.id LIMIT ? OFFSET ?",parameters.toArray());
        var prices=prices();
        for(var row:rows) {
            long input=n(row,"inputTokens"), hit=n(row,"cacheHitTokens"), miss=n(row,"cacheMissTokens"), output=n(row,"outputTokens"), reasoning=n(row,"reasoningTokens");
            var value=UsageCostAccounting.value(new long[]{Math.max(0,input-hit-miss),hit,miss,Math.max(0,output-reasoning),reasoning},prices.get(List.of((String)row.get("provider"),(String)row.get("model"))));
            row.put("pricingStatus",value.pricingStatus()); row.put("pricingReason",value.pricingReason()); row.put("currency",value.currency());
            row.put("estimatedCost",value.estimatedCost()==null?null:value.estimatedCost().setScale(UsageCostAccounting.SCALE,java.math.RoundingMode.HALF_UP).toPlainString());
        }
        return rows;
    }
    private long n(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public static String empty(String s) { return s==null||s.isBlank()?null:s; }
}
