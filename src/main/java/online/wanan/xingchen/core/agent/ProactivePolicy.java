package online.wanan.xingchen.core.agent;

import java.time.*;
import java.util.Map;
import online.wanan.xingchen.core.model.ConversationType;

/** Independent opt-in policy for idle conversations; ordinary inbound probability is unrelated. */
public record ProactivePolicy(boolean privateEnabled, boolean groupEnabled, int idleMinutes,
        int intervalMinutes, int cooldownMinutes, double probability, int maxPerDay,
        int quietStartHour, int quietEndHour, String zoneId) {
    public static ProactivePolicy from(Map<String,Object> values) {
        return new ProactivePolicy(Boolean.TRUE.equals(values.get("proactivePrivateEnabled")),
                Boolean.TRUE.equals(values.get("proactiveGroupEnabled")), number(values,"proactiveIdleMinutes"),
                number(values,"proactiveIntervalMinutes"),number(values,"proactiveCooldownMinutes"),
                ((Number)values.get("proactiveProbability")).doubleValue(),number(values,"proactiveMaxPerDay"),
                number(values,"proactiveQuietStartHour"),number(values,"proactiveQuietEndHour"),(String)values.get("proactiveZoneId"));
    }
    public ProactivePolicy {
        if(idleMinutes<5||idleMinutes>1440||intervalMinutes<5||intervalMinutes>1440||cooldownMinutes<30||cooldownMinutes>10080
                ||!Double.isFinite(probability)||probability<0||probability>1||maxPerDay<1||maxPerDay>6
                ||quietStartHour<0||quietStartHour>23||quietEndHour<0||quietEndHour>23)throw new IllegalArgumentException("invalid proactive policy");
        ZoneId.of(zoneId);
    }
    public boolean enabled(ConversationType type){return probability>0&&(type==ConversationType.PRIVATE?privateEnabled:type==ConversationType.GROUP&&groupEnabled);}
    public boolean quiet(Instant now){int hour=now.atZone(ZoneId.of(zoneId)).getHour();return quietStartHour!=quietEndHour&&(quietStartHour<quietEndHour?hour>=quietStartHour&&hour<quietEndHour:hour>=quietStartHour||hour<quietEndHour);}
    private static int number(Map<String,Object> values,String key){return ((Number)values.get(key)).intValue();}
}
