package online.wanan.xingchen.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="xingchen")
public record XingChenProperties(Owner owner, Console console, Onebot onebot, Model model, Dsh dsh,
                                 Memory memory, Context context, Security security, Social social) {
    public record Owner(String platform, String platformUserId) {}
    public record Console(String bind, int port, boolean allowRemote, boolean cookieSecure, String publicBaseUrl, java.util.List<String> trustedProxies) {}
    public record Onebot(String wsUrl, String httpUrl, String loginUserId) {}
    public record Model(String provider, String model) {}
    public record Dsh(String baseUrl) {}
    public record Memory(String databasePath, boolean wal, boolean foreignKeys, int defaultSearchLimit) {}
    public record Context(int softLimitTokens, int rolloverTokens, int hardLimitTokens, int recentMessageLimit, int memoryTokenBudget, int summaryTokenBudget) {}
    public record Security(boolean allowAllWhenEmpty, boolean failClosed) {}
    public record Social(boolean enabled) {}
}
