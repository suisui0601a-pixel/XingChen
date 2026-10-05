package online.wanan.xingchen.adapter.onebot;

import java.time.Instant;

public record GatewayStatus(boolean connected,String accountId,String detail,Instant lastHeartbeat,int reconnectCount) {
    public GatewayStatus(boolean connected,String accountId,String detail){this(connected,accountId,detail,null,0);}
}
