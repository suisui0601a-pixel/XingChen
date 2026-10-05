package online.wanan.xingchen.adapter.dsh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.wanan.xingchen.core.agent.ApprovalDecision;

import java.util.*;

/** Encodes pinned qq-bridge 0.1.7 interaction values at the DSH wire boundary. */
public final class DshRc2InteractionOutcomeEncoder {
    private final ObjectMapper mapper;
    public DshRc2InteractionOutcomeEncoder(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public ObjectNode approval(ApprovalDecision decision) {
        String value = Objects.requireNonNull(decision) == ApprovalDecision.ALLOW_ONCE ? "allowed-once" : "rejected";
        return mapper.createObjectNode().put("kind", "result").put("value", value);
    }

    /** A missing answer map means explicit cancellation and follows pinned Bridge's empty answers result. */
    public ObjectNode question(List<DshInteractionEvent.Question> questions, Map<String, String> answers) {
        ObjectNode outcome = mapper.createObjectNode().put("kind", "result");
        var result = mapper.createObjectNode();var encoded = result.putArray("answers");
        if (answers != null) for (var question : List.copyOf(questions)) {
            String text = answers.get(question.id());if (text == null) continue;
            var answer = encoded.addObject().put("id", question.id());
            if (question.options().contains(text)) answer.putArray("selected").add(text);
            else { answer.putArray("selected");answer.put("custom", text); }
        }
        outcome.set("value", result);return outcome;
    }
}
