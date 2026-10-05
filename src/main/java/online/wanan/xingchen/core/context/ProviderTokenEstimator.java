package online.wanan.xingchen.core.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Heuristic estimator adjusted by observed provider usage; never claims tokenizer parity. */
public final class ProviderTokenEstimator implements TokenEstimator {
    private final TokenEstimator fallback;
    private final Map<String, Calibration> calibrations = new ConcurrentHashMap<>();
    private final String provider;
    private final String model;

    public ProviderTokenEstimator() { this("deepseek-official", "unknown", new ApproximateTokenEstimator()); }
    public ProviderTokenEstimator(String provider, String model) { this(provider, model, new ApproximateTokenEstimator()); }
    public ProviderTokenEstimator(String provider, String model, TokenEstimator fallback) {
        this.provider = require(provider); this.model = require(model); this.fallback = fallback;
    }

    @Override public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        Calibration c = calibrations.get(key(provider, model));
        if (c == null || c.charactersPerToken() <= 0) return fallback.estimate(text);
        return Math.max(1, (int) Math.ceil(text.length() / c.charactersPerToken()));
    }

    /** Record provider-reported input usage for the exact provider/model; usage is calibration data, not a tokenizer. */
    public void recordUsage(String provider, String model, String submittedText, int reportedInputTokens) {
        if (reportedInputTokens <= 0 || submittedText == null || submittedText.isEmpty()) return;
        String key = key(require(provider), require(model));
        double observed = (double) submittedText.length() / reportedInputTokens;
        calibrations.compute(key, (ignored, previous) -> previous == null
                ? new Calibration(observed, 1)
                : new Calibration(previous.charactersPerToken() * 0.75 + observed * 0.25, previous.samples() + 1));
    }

    public Calibration calibration(String provider, String model) { return calibrations.get(key(require(provider), require(model))); }
    private static String key(String provider, String model) { return provider + "/" + model; }
    private static String require(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("provider and model are required"); return value; }
    public record Calibration(double charactersPerToken, long samples) { }
}
