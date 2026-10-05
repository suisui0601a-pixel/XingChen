package online.wanan.xingchen.adapter.snowluma;

import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import online.wanan.xingchen.core.gateway.GatewayState;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic local/test gateway; no external process or network access. */
public final class FakeGatewayManagementPort implements GatewayManagementPort {
    private final AtomicReference<GatewaySnapshot> snapshot = new AtomicReference<>(new GatewaySnapshot(GatewayState.NOT_CONFIGURED,false,false,"No gateway configured"));
    public void setState(GatewayState state) {
        snapshot.set(new GatewaySnapshot(state,state!=GatewayState.DISABLED&&state!=GatewayState.NOT_CONFIGURED,state==GatewayState.CONNECTED,"Local fake gateway"));
    }
    @Override public GatewaySnapshot getStatus() { return snapshot.get(); }
    @Override public Optional<GatewayAccount> getAccountInfo() { return snapshot.get().state()==GatewayState.CONNECTED?Optional.of(new GatewayAccount("QQ","fake-qq","Fake QQ")):Optional.empty(); }
    @Override public GatewayCapabilities capabilities() { return new GatewayCapabilities(false,false,false,true); }
}
