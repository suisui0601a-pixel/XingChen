package online.wanan.xingchen.adapter.snowluma;

import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.adapter.onebot.OneBotAccountInfo;
import online.wanan.xingchen.config.RuntimeIntegrationStatus;
import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import online.wanan.xingchen.core.gateway.GatewayState;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Observes the documented OneBot contract; it does not invent SnowLuma login-control endpoints. */
public final class SnowLumaGatewayAdapter implements GatewayManagementPort {
    private final OneBotGateway oneBot;
    private final BooleanSupplier enabled;
    public SnowLumaGatewayAdapter(OneBotGateway oneBot, RuntimeIntegrationStatus switches) { this(oneBot,switches,switches::oneBotEnabled); }
    public SnowLumaGatewayAdapter(OneBotGateway oneBot, RuntimeIntegrationStatus switches,BooleanSupplier enabled) { this.oneBot=oneBot;this.enabled=enabled; }
    @Override public GatewaySnapshot getStatus() {
        var status=oneBot.status();
        if (!enabled.getAsBoolean()) return new GatewaySnapshot(GatewayState.DISABLED,false,false,"OneBot integration is disabled");
        return new GatewaySnapshot(status.connected()?GatewayState.CONNECTED:GatewayState.DISCONNECTED,true,status.connected(),status.detail());
    }
    @Override public Optional<GatewayAccount> getAccountInfo() {
        if (!enabled.getAsBoolean() || !oneBot.status().connected()) return Optional.empty();
        return oneBot.getLoginInfo().map(OneBotAccountInfo::toGatewayAccount);
    }
    @Override public GatewayCapabilities capabilities() { return new GatewayCapabilities(false,false,false,true); }
}
