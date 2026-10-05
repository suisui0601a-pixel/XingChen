package online.wanan.xingchen.adapter.onebot;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Secrets are resolved from a designated environment variable at runtime, never stored in this object. */
public record OneBotV11Configuration(URI httpBaseUri,URI websocketUri,String tokenEnvironmentVariable,String selfId,
                                     boolean allowNonLoopback,int connectTimeoutMillis,int requestTimeoutMillis,
                                     int reconnectBaseMillis,int maxReconnectMillis,int eventBufferSize,int heartbeatTimeoutMillis) {
    public OneBotV11Configuration(URI httpBaseUri,URI websocketUri,String tokenEnvironmentVariable,String selfId,boolean allowNonLoopback,int connectTimeoutMillis,int requestTimeoutMillis,int reconnectBaseMillis,int maxReconnectMillis,int eventBufferSize){this(httpBaseUri,websocketUri,tokenEnvironmentVariable,selfId,allowNonLoopback,connectTimeoutMillis,requestTimeoutMillis,reconnectBaseMillis,maxReconnectMillis,eventBufferSize,90_000);}
    public OneBotV11Configuration {if(tokenEnvironmentVariable==null||tokenEnvironmentVariable.isBlank())tokenEnvironmentVariable="XINGCHEN_ONEBOT_ACCESS_TOKEN";if(connectTimeoutMillis<1||requestTimeoutMillis<1||reconnectBaseMillis<1||maxReconnectMillis<reconnectBaseMillis||eventBufferSize<1||heartbeatTimeoutMillis<1)throw new IllegalArgumentException("invalid OneBot configuration");}
    public static OneBotV11Configuration fromEnvironment(Map<String,String> env){return new OneBotV11Configuration(URI.create(env.getOrDefault("ONEBOT_HTTP_URL","http://127.0.0.1:3000")),URI.create(env.getOrDefault("ONEBOT_WS_URL","ws://127.0.0.1:3001")),"XINGCHEN_ONEBOT_ACCESS_TOKEN",env.getOrDefault("ONEBOT_LOGIN_USER_ID",""),Boolean.parseBoolean(env.getOrDefault("ONEBOT_ALLOW_NON_LOOPBACK","false")),5000,10000,1000,30000,1000,Integer.parseInt(env.getOrDefault("ONEBOT_HEARTBEAT_TIMEOUT_MS","90000")));}
    public Duration requestTimeout(){return Duration.ofMillis(requestTimeoutMillis);}
}
