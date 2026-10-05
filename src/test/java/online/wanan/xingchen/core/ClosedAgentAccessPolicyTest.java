package online.wanan.xingchen.core;

import online.wanan.xingchen.core.agent.ClosedAgentAccessPolicy;
import online.wanan.xingchen.core.model.ConversationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClosedAgentAccessPolicyTest {
    private final ClosedAgentAccessPolicy policy=new ClosedAgentAccessPolicy();
    @Test void onlyCertainOwnerPrivateConversationMayUseClosedAgent(){
        assertThat(policy.allows(ConversationType.PRIVATE,true,"CLOSED_AGENT",true)).isTrue();
        assertThat(policy.allows(ConversationType.GROUP,true,"CLOSED_AGENT",true)).isFalse();
        assertThat(policy.allows(ConversationType.PRIVATE,false,"CLOSED_AGENT",true)).isFalse();
        assertThat(policy.allows(ConversationType.PRIVATE,true,"CLOSED_AGENT",false)).isFalse();
        assertThat(policy.allows(ConversationType.PRIVATE,true,"SOCIAL",true)).isFalse();
    }
}
