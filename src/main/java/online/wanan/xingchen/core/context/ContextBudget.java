package online.wanan.xingchen.core.context;

public record ContextBudget(int softLimitTokens, int rolloverTokens, int hardLimitTokens,
                            int recentMessageLimit, int memoryTokenBudget, int summaryTokenBudget,int toolResultTokenBudget,
                            int safetyMarginPercent) {
    public ContextBudget(int softLimitTokens,int rolloverTokens,int hardLimitTokens,int recentMessageLimit,int memoryTokenBudget,int summaryTokenBudget){this(softLimitTokens,rolloverTokens,hardLimitTokens,recentMessageLimit,memoryTokenBudget,summaryTokenBudget,Math.min(6000,hardLimitTokens/4),20);}
    public ContextBudget(int softLimitTokens,int rolloverTokens,int hardLimitTokens,int recentMessageLimit,int memoryTokenBudget,int summaryTokenBudget,int toolResultTokenBudget){this(softLimitTokens,rolloverTokens,hardLimitTokens,recentMessageLimit,memoryTokenBudget,summaryTokenBudget,toolResultTokenBudget,20);}
    public ContextBudget { if (softLimitTokens <= 0 || rolloverTokens <= softLimitTokens || hardLimitTokens <= rolloverTokens||recentMessageLimit<0||memoryTokenBudget<0||summaryTokenBudget<0||toolResultTokenBudget<0||safetyMarginPercent<0||safetyMarginPercent>100) throw new IllegalArgumentException("invalid context limits"); }
    public static ContextBudget defaults() { return new ContextBudget(60000,80000,120000,40,12000,4000,6000,20); }
}
