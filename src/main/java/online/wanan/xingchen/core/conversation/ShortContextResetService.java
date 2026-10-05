package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.adapter.dsh.DshSessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import online.wanan.xingchen.core.agent.SimulationStateService;
import online.wanan.xingchen.core.agent.DshPendingInteractionStore;
import online.wanan.xingchen.storage.DurableTurnExecutionStore;

/** Clears only transient conversation context after the external DSH session is stopped and archived. */
public class ShortContextResetService {
    private final DshSessionService sessions;private final ConversationWindow window;private final JdbcTemplate jdbc;
    private final SimulationStateService simulation;private final DurableTurnExecutionStore turns;private final DshPendingInteractionStore interactions;
    public ShortContextResetService(DshSessionService sessions,ConversationWindow window,JdbcTemplate jdbc){this(sessions,window,jdbc,null,null,null);}
    public ShortContextResetService(DshSessionService sessions,ConversationWindow window,JdbcTemplate jdbc,SimulationStateService simulation,DurableTurnExecutionStore turns,DshPendingInteractionStore interactions){this.sessions=sessions;this.window=window;this.jdbc=jdbc;this.simulation=simulation;this.turns=turns;this.interactions=interactions;}
    @Transactional public void reset(UUID conversation){
        long generation=simulation==null?-1:simulation.reset(conversation).generation();cleanupReset(conversation,generation);
    }
    @Transactional public boolean cleanupReset(UUID conversation,long generation){
        if(simulation!=null&&!simulation.cleanupReset(conversation,generation))return false;
        if(turns!=null)turns.markConversationStale(conversation,generation-1);
        if(interactions!=null)interactions.interruptConversation(conversation.toString());
        sessions.reset(conversation);window.clear(conversation.toString());
        jdbc.update("DELETE FROM conversation_summaries WHERE conversation_id=?",conversation.toString());jdbc.update("DELETE FROM session_handoffs WHERE conversation_id=?",conversation.toString());
        jdbc.update("UPDATE session_state SET status='RESET',current_summary_id=NULL,estimated_context_tokens=0,token_estimate=0,updated_at=CURRENT_TIMESTAMP WHERE conversation_id=?",conversation.toString());
        return true;
    }
}
