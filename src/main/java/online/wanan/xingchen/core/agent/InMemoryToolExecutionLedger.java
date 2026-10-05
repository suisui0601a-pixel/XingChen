package online.wanan.xingchen.core.agent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local fallback for tests and isolated runs. Production composition should inject the SQLite ledger. */
public final class InMemoryToolExecutionLedger implements ToolExecutionLedger {
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();
    @Override public boolean claim(String eventId, String callId) {
        return claimed.add(eventId + "\u0000" + callId);
    }
}
