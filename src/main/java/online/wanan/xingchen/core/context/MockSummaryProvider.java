package online.wanan.xingchen.core.context;

import java.util.List;

public final class MockSummaryProvider {
    public SessionHandoff summarize(String displayName,String address,List<String> recent){
        String summary=recent.isEmpty()?"":"Recent conversation: "+String.join(" | ",recent);
        return new SessionHandoff(summary,List.of(),List.of(displayName),List.of("assistant addresses this person as "+address),List.of(),List.of(),List.of(),List.copyOf(recent));
    }
}
