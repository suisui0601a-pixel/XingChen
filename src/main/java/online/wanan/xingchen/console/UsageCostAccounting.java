package online.wanan.xingchen.console;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Current-rate estimates, never invoices or FX. All amounts are decimal.
 * Sum exact amounts first, then round each currency subtotal to 12 places HALF_UP.
 * A missing rate for any nonzero disjoint category makes the entire row unpriced.
 */
public final class UsageCostAccounting {
    private UsageCostAccounting() {}
    public static final int SCALE = 12;
    public static final List<String> CATEGORIES = List.of("input", "cacheHit", "cacheMiss", "output", "reasoning");
    private static final List<String> REASONS = List.of("MISSING_INPUT_RATE", "MISSING_CACHE_HIT_RATE", "MISSING_CACHE_MISS_RATE", "MISSING_OUTPUT_RATE", "MISSING_REASONING_RATE");
    public record Price(String currency, List<BigDecimal> rates) {}
    public record Valuation(String pricingStatus, String pricingReason, String currency, BigDecimal estimatedCost) {}
    public static final class PlainDecimalSerializer extends com.fasterxml.jackson.databind.JsonSerializer<BigDecimal> {
        @Override public void serialize(BigDecimal value, com.fasterxml.jackson.core.JsonGenerator output,
                                        com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
            output.writeString(value.toPlainString());
        }
    }
    public record CurrencyCost(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=PlainDecimalSerializer.class) BigDecimal estimatedCost, long pricedUsageCount, long pricedTokens) {}
    public record Summary(Map<String, CurrencyCost> costsByCurrency, long usageCount, long pricedUsageCount,
                          long unpricedUsageCount, long totalTokens, long pricedTokens, long unpricedTokens,
                          String pricingCoverage, int currencyCount, boolean mixedCurrency,
                          @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=PlainDecimalSerializer.class) BigDecimal estimatedCost, String currency, String totalUnavailableReason) {}

    public static boolean validCurrency(String currency) { return currency != null && currency.matches("[A-Z]{3}"); }
    public static boolean validRate(BigDecimal rate) {
        return rate == null || (rate.signum() >= 0 && rate.compareTo(BigDecimal.valueOf(1_000_000)) <= 0
                && rate.scale() <= 18 && rate.precision() <= 25);
    }
    public static Valuation value(long[] tokens, Price price) {
        if (price == null) return new Valuation("UNPRICED", "NO_MATCHING_PRICE", null, null);
        if (!validCurrency(price.currency())) return new Valuation("UNPRICED", "INVALID_CURRENCY", null, null);
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < tokens.length; i++) {
            BigDecimal rate = price.rates().get(i);
            if (tokens[i] > 0 && rate == null) return new Valuation("UNPRICED", REASONS.get(i), price.currency(), null);
            if (rate != null && !validRate(rate)) return new Valuation("UNPRICED", "INVALID_RATE", price.currency(), null);
            if (tokens[i] > 0) amount = amount.add(rate.multiply(BigDecimal.valueOf(tokens[i])));
        }
        return new Valuation("PRICED", null, price.currency(), amount.movePointLeft(6));
    }
    public static final class Accumulator {
        private final Map<String, CurrencyCost> currencies = new TreeMap<>();
        private long usage, priced, tokens, pricedTokens;
        public void add(long count, long totalTokens, Valuation value) {
            usage += count; tokens += totalTokens;
            if (!"PRICED".equals(value.pricingStatus())) return;
            priced += count; pricedTokens += totalTokens;
            CurrencyCost old = currencies.getOrDefault(value.currency(), new CurrencyCost(BigDecimal.ZERO, 0, 0));
            currencies.put(value.currency(), new CurrencyCost(old.estimatedCost().add(value.estimatedCost()), old.pricedUsageCount() + count, old.pricedTokens() + totalTokens));
        }
        public Summary summary() {
            String coverage = priced == 0 ? "NONE" : priced == usage ? "COMPLETE" : "PARTIAL";
            Map<String, CurrencyCost> rounded = new TreeMap<>();
            currencies.forEach((key, v) -> rounded.put(key, new CurrencyCost(v.estimatedCost().setScale(SCALE, RoundingMode.HALF_UP), v.pricedUsageCount(), v.pricedTokens())));
            boolean total = rounded.size() == 1 && "COMPLETE".equals(coverage);
            String currency = total ? rounded.keySet().iterator().next() : null;
            String reason = total ? null : rounded.size() > 1 ? "MIXED_CURRENCIES" : priced == 0 ? "NO_PRICED_USAGE" : "INCOMPLETE_PRICING";
            return new Summary(Collections.unmodifiableMap(rounded), usage, priced, usage - priced, tokens, pricedTokens, tokens - pricedTokens, coverage, rounded.size(), rounded.size() > 1, total ? rounded.get(currency).estimatedCost() : null, currency, reason);
        }
    }
}
