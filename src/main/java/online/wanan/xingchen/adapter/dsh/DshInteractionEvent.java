package online.wanan.xingchen.adapter.dsh;

import java.time.Instant;
import java.util.List;

/** Domain-safe projection of one RC.2 forwarded waterfall. Wire JSON stays inside the adapter. */
public sealed interface DshInteractionEvent permits DshApprovalRequest, DshUserQuestionRequest, DshInteractionCancelled {
    String agentId();
    String clientId();
    long clientGeneration();
    String eventId();
    Instant receivedAt();

    record Question(String id, String question, List<String> options) {
        public Question { options = options == null ? List.of() : List.copyOf(options); }
    }
}
