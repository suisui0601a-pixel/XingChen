package online.wanan.xingchen.adapter.dsh;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.agent.ApprovalDecision;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DshRc2InteractionOutcomeEncoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DshRc2InteractionOutcomeEncoder encoder = new DshRc2InteractionOutcomeEncoder(mapper);

    @Test void approvalDecisionsUseThePinnedBridgeWireValues() {
        assertThat(encoder.approval(ApprovalDecision.ALLOW_ONCE).path("value").asText()).isEqualTo("allowed-once");
        assertThat(encoder.approval(ApprovalDecision.REJECT).path("value").asText()).isEqualTo("rejected");
    }

    @Test void questionsMatchExactLabelsUseCustomTextAndCancellationIsEmpty() {
        var questions=List.of(new DshInteractionEvent.Question("q1","Choice",List.of("Yes","No")),new DshInteractionEvent.Question("q2","Reason",List.of("A","B")));
        var value=encoder.question(questions,Map.of("q1","Yes","q2","because" )).path("value").path("answers");
        assertThat(value.get(0).path("selected").get(0).asText()).isEqualTo("Yes");
        assertThat(value.get(0).has("custom")).isFalse();
        assertThat(value.get(1).path("selected")).isEmpty();
        assertThat(value.get(1).path("custom").asText()).isEqualTo("because");
        assertThat(encoder.question(questions,null).path("value").path("answers")).isEmpty();
    }
}
