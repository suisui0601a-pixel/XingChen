package online.wanan.xingchen.adapter.dsh;

import java.time.Instant;

/** Host withdrew a still-pending waterfall (for example, its turn was cancelled). */
public record DshInteractionCancelled(String agentId, String clientId, long clientGeneration, String eventId,
                                     Instant receivedAt) implements DshInteractionEvent { }
