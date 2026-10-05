package online.wanan.xingchen.adapter.dsh;

import java.time.Instant;
import java.util.Map;

/** Domain projection of one session-bound DSH follow item. */
public record DshFollowEvent(String sessionId, Kind kind, String eventType, long sequence,
                             Instant occurredAt, Map<String,Object> attributes) {
    public enum Kind { SNAPSHOT, DURABLE_EVENT, ASSISTANT_STREAM }
    public DshFollowEvent { attributes=attributes==null?Map.of():Map.copyOf(attributes); }
}
