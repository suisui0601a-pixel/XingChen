package online.wanan.xingchen.config;

import java.util.Objects;

/** Applies fail-closed startup gates and reports each integration independently. */
public final class RuntimeIntegrationStartup {
    private static final System.Logger LOG = System.getLogger(RuntimeIntegrationStartup.class.getName());

    public void start(RuntimeIntegrationStatus switches, Runnable startSocial, Runnable startDshInteractions) {
        Objects.requireNonNull(switches);
        Objects.requireNonNull(startSocial);
        Objects.requireNonNull(startDshInteractions);

        logSwitch("Social", switches.socialEnabled());
        logSwitch("Model", switches.modelEnabled());
        logSwitch("OneBot", switches.oneBotEnabled());
        logSwitch("DSH", switches.dshEnabled());

        if (switches.socialRuntimeEnabled()) {
            startSocial.run();
            LOG.log(System.Logger.Level.INFO, "SocialRuntime: STARTED");
        } else {
            LOG.log(System.Logger.Level.INFO, "SocialRuntime: NOT STARTED; Reason: {0}", switches.socialRuntimeDisabledReason());
        }

        if (switches.dshInteractionRuntimeEnabled()) {
            startDshInteractions.run();
            LOG.log(System.Logger.Level.INFO, "DSH interaction integration: STARTED");
        } else {
            LOG.log(System.Logger.Level.INFO, "DSH interaction integration: NOT STARTED; Reason: {0}", switches.dshInteractionRuntimeDisabledReason());
        }
    }

    private static void logSwitch(String name, boolean enabled) {
        LOG.log(System.Logger.Level.INFO, "{0} integration: {1}", name, enabled ? "ENABLED" : "DISABLED");
    }
}
