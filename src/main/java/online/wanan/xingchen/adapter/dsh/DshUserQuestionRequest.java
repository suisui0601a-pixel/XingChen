package online.wanan.xingchen.adapter.dsh;

import java.time.Instant;
import java.util.List;

public record DshUserQuestionRequest(String agentId, String clientId, long clientGeneration, String eventId, Instant receivedAt,
                                     List<Question> questions) implements DshInteractionEvent {
    public DshUserQuestionRequest { questions = questions == null ? List.of() : List.copyOf(questions); }
}
