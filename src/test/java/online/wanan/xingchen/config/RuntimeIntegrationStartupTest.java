package online.wanan.xingchen.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeIntegrationStartupTest {
    @Test void startupUsesOnlyActualFailClosedDependencies() {
        assertStarts(true, true, true, false, true, false);
        assertStarts(true, true, true, true, true, true);
        assertStarts(true, true, false, false, false, false);
        assertStarts(true, false, true, false, false, false);
        assertStarts(false, true, true, false, false, false);
    }

    @Test void disabledSocialRuntimeReportsMissingDependency() {
        assertThat(new RuntimeIntegrationStatus(false, false, true, true).socialRuntimeDisabledReason())
                .isEqualTo("OneBot disabled");
        assertThat(new RuntimeIntegrationStatus(false, true, false, true).socialRuntimeDisabledReason())
                .isEqualTo("Model disabled");
        assertThat(new RuntimeIntegrationStatus(false, true, true, false).socialRuntimeDisabledReason())
                .isEqualTo("Social disabled");
    }

    private static void assertStarts(boolean social, boolean model, boolean oneBot, boolean dsh,
                                     boolean expectSocial, boolean expectDshInteractions) {
        AtomicInteger socialStarts = new AtomicInteger();
        AtomicInteger dshStarts = new AtomicInteger();
        new RuntimeIntegrationStartup().start(new RuntimeIntegrationStatus(dsh, oneBot, model, social),
                socialStarts::incrementAndGet, dshStarts::incrementAndGet);
        assertThat(socialStarts.get()).isEqualTo(expectSocial ? 1 : 0);
        assertThat(dshStarts.get()).isEqualTo(expectDshInteractions ? 1 : 0);
    }
}
