package online.wanan.xingchen.core.agent;

import java.time.Instant;

public record SecurityEvent(String code,String toolName,String detail,Instant occurredAt) {
    public SecurityEvent { occurredAt=occurredAt==null?Instant.now():occurredAt;detail=detail==null?"":detail; }
}
