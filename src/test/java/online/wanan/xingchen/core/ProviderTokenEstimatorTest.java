package online.wanan.xingchen.core;

import online.wanan.xingchen.core.context.ContextBudget;
import online.wanan.xingchen.core.context.ContextBuilder;
import online.wanan.xingchen.core.context.ContextBuildInput;
import online.wanan.xingchen.core.context.ProviderTokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderTokenEstimatorTest {
    @Test void providerUsageCalibratesHeuristicWithoutClaimingExactTokenizer() {
        var estimator = new ProviderTokenEstimator("deepseek-official", "model-a");
        String sample = "a".repeat(100);
        int before = estimator.estimate(sample);
        estimator.recordUsage("deepseek-official", "model-a", sample, 50);
        assertThat(estimator.estimate(sample)).isEqualTo(50);
        assertThat(estimator.calibration("deepseek-official", "model-a").samples()).isEqualTo(1);
        assertThat(before).isNotEqualTo(50);
    }

    @Test void contextDiagnosticsExposeCalibratedEstimateAndConservativeMargin() {
        var budget = new ContextBudget(100, 150, 300, 10, 100, 40, 40, 25);
        var builder = new ContextBuilder(budget, new ProviderTokenEstimator("deepseek-official", "test"));
        var result = builder.build(new ContextBuildInput("sim", "persona", "actor", "name", "address", "conversation",
                List.of(), "", List.of(), Map.of(), "hello", "", null));
        assertThat(result.diagnostics().safetyMarginTokens()).isPositive();
        assertThat(result.estimatedTokens()).isEqualTo(result.diagnostics().calibratedTokens()+result.diagnostics().safetyMarginTokens());
    }
}
