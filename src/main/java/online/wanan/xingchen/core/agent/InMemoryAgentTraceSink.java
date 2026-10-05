package online.wanan.xingchen.core.agent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryAgentTraceSink implements AgentTraceSink {private final List<AgentTrace> traces=new CopyOnWriteArrayList<>();@Override public void record(AgentTrace trace){traces.add(trace);}public List<AgentTrace> traces(){return List.copyOf(traces);}}
