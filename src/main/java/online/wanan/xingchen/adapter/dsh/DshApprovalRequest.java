package online.wanan.xingchen.adapter.dsh;

import java.time.Instant;

public record DshApprovalRequest(String agentId, String clientId, long clientGeneration, String eventId, Instant receivedAt,
                                 String toolName, String callId, String reason) implements DshInteractionEvent { }
