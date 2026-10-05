package online.wanan.xingchen.config;

/** Effective outbound integration switches; all default to disabled. */
public record RuntimeIntegrationStatus(boolean dshEnabled, boolean oneBotEnabled, boolean modelEnabled, boolean socialEnabled) {
    public boolean allEnabled() { return dshEnabled && oneBotEnabled && modelEnabled && socialEnabled; }
}
