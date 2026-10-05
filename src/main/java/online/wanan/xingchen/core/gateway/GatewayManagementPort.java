package online.wanan.xingchen.core.gateway;

import java.util.Optional;

/** Stable Console boundary for a QQ gateway. Unsupported external controls stay explicit. */
public interface GatewayManagementPort {
    GatewaySnapshot getStatus();
    Optional<GatewayAccount> getAccountInfo();
    GatewayCapabilities capabilities();
    default OperationResult beginLogin() { return OperationResult.unsupported("Login challenge is managed by SnowLuma WebUI/noVNC"); }
    default OperationResult refreshLoginChallenge() { return OperationResult.unsupported("SnowLuma does not document a QR refresh API"); }
    default OperationResult logout() { return OperationResult.unsupported("SnowLuma does not document a programmatic QQ logout API"); }
    default OperationResult reconnect() { return OperationResult.unsupported("SnowLuma does not document a programmatic QQ reconnect API"); }
    record GatewaySnapshot(GatewayState state, boolean enabled, boolean transportConnected, String detail) {}
    record GatewayAccount(String platform, String userId, String displayName) {}
    record GatewayCapabilities(boolean qrLogin, boolean logout, boolean reconnect, boolean accountLookup) {}
    record OperationResult(boolean supported, boolean accepted, String message) {
        public static OperationResult unsupported(String message) { return new OperationResult(false, false, message); }
    }
}
