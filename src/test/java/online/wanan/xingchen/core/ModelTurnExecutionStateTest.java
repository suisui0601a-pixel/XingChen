package online.wanan.xingchen.core;

import online.wanan.xingchen.core.agent.ModelTurnExecutionState;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ModelTurnExecutionStateTest {
    @Test void streamInterruptedBeforeToolIsSafeToRetry(){var state=new ModelTurnExecutionState();state.begin();assertThat(state.interrupted()).isTrue();state.resetForSafeRetry();assertThat(state.state()).isEqualTo(ModelTurnExecutionState.State.NOT_STARTED);}
    @Test void fragmentedToolThenDisconnectHasNoFormedProposalOrSideEffect(){var state=new ModelTurnExecutionState();state.begin();assertThat(state.interrupted()).isTrue();}
    @Test void executedToolThenDisconnectIsUncertainAndNeverRetried(){var state=new ModelTurnExecutionState();state.begin();state.toolProposed();state.toolExecuted();assertThat(state.interrupted()).isFalse();assertThat(state.state()).isEqualTo(ModelTurnExecutionState.State.UNCERTAIN);}
    @Test void bufferedModelOutputIsNotAnExternalEffectAndCanRetry(){var state=new ModelTurnExecutionState();state.begin();state.modelOutputReceived();assertThat(state.interrupted()).isTrue();state.resetForSafeRetry();assertThat(state.state()).isEqualTo(ModelTurnExecutionState.State.NOT_STARTED);}
    @Test void confirmedOutboundOutputPreventsRetry(){var state=new ModelTurnExecutionState();state.begin();state.modelOutputReceived();state.outboundOutputSent();assertThat(state.interrupted()).isFalse();assertThat(state.state()).isEqualTo(ModelTurnExecutionState.State.UNCERTAIN);}
    @Test void unknownOutboundResultFailsClosed(){var state=new ModelTurnExecutionState();state.begin();state.outboundOutputUnknown();assertThat(state.mayAutoRetry()).isFalse();assertThat(state.interrupted()).isFalse();}
    @Test void unexecutedToolProposalCanBeSafelyReplayed(){var state=new ModelTurnExecutionState();state.begin();state.toolProposed();assertThat(state.interrupted()).isTrue();state.resetForSafeRetry();assertThat(state.state()).isEqualTo(ModelTurnExecutionState.State.NOT_STARTED);}
    @Test void interruptedUnsafeTurnPersistsUncertainBoundary(){var observed=new java.util.ArrayList<ModelTurnExecutionState.State>();var state=new ModelTurnExecutionState(observed::add);state.begin();state.toolProposed();state.toolExecuted();assertThat(state.interrupted()).isFalse();assertThat(observed).last().isEqualTo(ModelTurnExecutionState.State.UNCERTAIN);}
}
