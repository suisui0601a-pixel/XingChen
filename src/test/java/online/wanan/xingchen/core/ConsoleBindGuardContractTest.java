package online.wanan.xingchen.core;

import online.wanan.xingchen.console.ConsoleBindGuard;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleBindGuardContractTest {
    @Test void consoleBindDefaultsToLoopbackSafeAddresses() {
        assertThatCode(() -> ConsoleBindGuard.validate("127.0.0.1", false)).doesNotThrowAnyException();
        assertThatCode(() -> ConsoleBindGuard.validate("localhost", false)).doesNotThrowAnyException();
    }
    @Test void consoleBindRejectsNonLoopbackUnlessExplicitlyOptedIn() {
        assertThatThrownBy(() -> ConsoleBindGuard.validate("0.0.0.0", false)).hasMessageContaining("requires");
        assertThatThrownBy(() -> ConsoleBindGuard.validate("0.0.0.0", true)).hasMessageContaining("authentication");
        assertThatCode(() -> ConsoleBindGuard.validate("0.0.0.0", true, true)).doesNotThrowAnyException();
    }
}
