package online.wanan.xingchen.config;

/** Effective outbound integration switches; all default to disabled. */
public record RuntimeIntegrationStatus(boolean dshEnabled, boolean oneBotEnabled, boolean modelEnabled, boolean socialEnabled) {
    /** Normal social turns need the Social policy, model provider, and OneBot transport, but not DSH. */
    public boolean socialRuntimeEnabled() { return socialEnabled && modelEnabled && oneBotEnabled; }

    /** DSH interaction events also need the inbound Social router and OneBot for owner notifications. */
    public boolean dshInteractionRuntimeEnabled() { return dshEnabled && socialEnabled && oneBotEnabled; }

    public String socialRuntimeDisabledReason() {
        if (!socialEnabled) return "Social disabled";
        if (!oneBotEnabled) return "OneBot disabled";
        if (!modelEnabled) return "Model disabled";
        return "unknown configuration";
    }

    public String dshInteractionRuntimeDisabledReason() {
        if (!dshEnabled) return "DSH disabled";
        if (!oneBotEnabled) return "OneBot disabled";
        if (!socialEnabled) return "Social disabled";
        return "unknown configuration";
    }
}
