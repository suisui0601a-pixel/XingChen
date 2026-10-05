package online.wanan.xingchen.core.agent;

import java.util.UUID;

public interface UsageLedger {void record(UUID conversationId,String turnId,ModelUsage usage);}
