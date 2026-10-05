package online.wanan.xingchen.core.agent;

public record ModelUsage(int inputTokens,int cacheHitTokens,int cacheMissTokens,int outputTokens,int reasoningTokens,
                         String provider,String model,long durationMillis,boolean reasoningAvailable) {
    public ModelUsage(int inputTokens,int cacheHitTokens,int cacheMissTokens,int outputTokens,int reasoningTokens,String provider,String model,long durationMillis){this(inputTokens,cacheHitTokens,cacheMissTokens,outputTokens,reasoningTokens,provider,model,durationMillis,false);}
    public ModelUsage(int inputTokens,int cacheHitTokens,int cacheMissTokens,int outputTokens,int reasoningTokens){this(inputTokens,cacheHitTokens,cacheMissTokens,outputTokens,reasoningTokens,"mock","mock",0,false);}
    public ModelUsage { provider=provider==null?"unknown":provider;model=model==null?"unknown":model;if(inputTokens<0||cacheHitTokens<0||cacheMissTokens<0||outputTokens<0||reasoningTokens<0||durationMillis<0)throw new IllegalArgumentException("usage counters cannot be negative"); }
    public ModelUsage withDuration(long millis){return new ModelUsage(inputTokens,cacheHitTokens,cacheMissTokens,outputTokens,reasoningTokens,provider,model,millis,reasoningAvailable);}
}
