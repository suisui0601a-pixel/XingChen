package online.wanan.xingchen.adapter.onebot;

import online.wanan.xingchen.core.gateway.GatewayManagementPort;

public record OneBotAccountInfo(String userId, String nickname) {
    public GatewayManagementPort.GatewayAccount toGatewayAccount() { return new GatewayManagementPort.GatewayAccount("QQ",userId,nickname); }
}
