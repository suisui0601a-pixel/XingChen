package online.wanan.xingchen.adapter.deepseek;

import java.util.Map;

/** Contains configuration names/limits only; never stores the API secret itself. */
public record DeepSeekConfiguration(String baseUrl,String model,String apiKeyEnvironmentVariable,int connectTimeoutMillis,int requestTimeoutMillis,int maxRetries,int retryDelayMillis,int maxOutputTokens) {
    public DeepSeekConfiguration(String baseUrl,String model,String apiKeyEnvironmentVariable,int connectTimeoutMillis,int requestTimeoutMillis,int maxRetries){this(baseUrl,model,apiKeyEnvironmentVariable,connectTimeoutMillis,requestTimeoutMillis,maxRetries,250,1024);}
    public DeepSeekConfiguration {
        if(baseUrl==null||baseUrl.isBlank())baseUrl="https://api.deepseek.com";
        if(model==null||model.isBlank())model="deepseek-chat";
        if(apiKeyEnvironmentVariable==null||apiKeyEnvironmentVariable.isBlank())apiKeyEnvironmentVariable="XINGCHEN_DEEPSEEK_API_KEY";
        if(connectTimeoutMillis<1||requestTimeoutMillis<1||maxRetries<0||maxRetries>5||retryDelayMillis<0||maxOutputTokens<1)throw new IllegalArgumentException("invalid DeepSeek timeout/retry/output configuration");
    }
    public static DeepSeekConfiguration fromEnvironment(Map<String,String> env){return new DeepSeekConfiguration(env.getOrDefault("XINGCHEN_DEEPSEEK_BASE_URL","https://api.deepseek.com"),env.getOrDefault("XINGCHEN_DEEPSEEK_MODEL","deepseek-chat"),"XINGCHEN_DEEPSEEK_API_KEY",10_000,60_000,2,250,1024);}
}
