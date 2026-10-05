package online.wanan.xingchen.core.agent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemorySecurityEventSink implements SecurityEventSink {
    private final List<SecurityEvent> events=new CopyOnWriteArrayList<>();
    @Override public void record(SecurityEvent event){events.add(event);}
    public List<SecurityEvent> events(){return List.copyOf(events);}
}
