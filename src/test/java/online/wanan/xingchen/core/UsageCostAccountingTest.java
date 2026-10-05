package online.wanan.xingchen.core;

import online.wanan.xingchen.console.UsageConsoleService;
import online.wanan.xingchen.console.UsageCostAccounting;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class UsageCostAccountingTest {
    private static final Path DB=TestSqliteDatabase.create("usage-cost");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));
        p.add("xingchen.memory.database-path",()->DB.toString());
    }
    @Autowired JdbcTemplate jdbc; @Autowired UsageConsoleService usage; @Autowired MockMvc mvc;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM token_usage"); jdbc.update("DELETE FROM console_pricing");
        jdbc.update("DELETE FROM console_pricing_audit"); jdbc.update("UPDATE console_pricing_meta SET revision=0");
    }
    @AfterAll void cleanup() { TestSqliteDatabase.clean(jdbc,DB); }
    private void event(String model,long input,long hit,long miss,long output,long reasoning) {
        jdbc.update("INSERT INTO token_usage(id,provider,model,usage_time,input_tokens,cache_hit_tokens,cache_miss_tokens,output_tokens,reasoning_tokens) VALUES(?,'provider',?,?,?,?,?,?,?)",UUID.randomUUID().toString(),model,Instant.now().minusSeconds(60).toString(),input,hit,miss,output,reasoning);
    }
    private void price(String model,String currency,String... rates) {
        jdbc.update("INSERT INTO console_pricing(provider,model,input,cache_hit,cache_miss,output,reasoning,currency,revision,updated_at,updated_by) VALUES('provider',?,?,?,?,?,?,?,1,?,'test')",model,rates[0],rates[1],rates[2],rates[3],rates[4],currency,Instant.now().toString());
    }
    private void price(String model,String currency) { price(model,currency,"1","2","3","4","5"); }
    private UsageCostAccounting.Summary cost() { return (UsageCostAccounting.Summary)usage.summary("24h",null,null,null,null,null,null).get("cost"); }
    private List<Map<String,Object>> records() { return usage.records("24h",null,null,null,null,null,null,0,100); }
    @Test void cost001_singleCurrencyCompleteHasValidTotal() throws Exception {
        event("a",100,20,30,40,5);price("a","USD");var s=cost();
        assertThat(s.pricingCoverage()).isEqualTo("COMPLETE");assertThat(s.currency()).isEqualTo("USD");
        assertThat(s.estimatedCost()).isEqualByComparingTo("0.000345");assertThat(s.totalTokens()).isEqualTo(140);
        assertThat(s.costsByCurrency().get("USD").pricedUsageCount()).isEqualTo(1);
        mvc.perform(get("/api/usage/summary?window=24h").with(user("test"))).andExpect(status().isOk()).andExpect(jsonPath("$.cost.estimatedCost").value("0.000345000000"));
    }
    @Test void cost002_mixedCurrenciesNeverHaveCombinedTotal() {
        event("a",100,0,0,0,0);event("b",200,0,0,0,0);price("a","USD");price("b","CNY");var s=cost();
        assertThat(s.pricingCoverage()).isEqualTo("COMPLETE");assertThat(s.mixedCurrency()).isTrue();
        assertThat(s.costsByCurrency()).containsOnlyKeys("USD","CNY");assertThat(s.estimatedCost()).isNull();assertThat(s.currency()).isNull();
        assertThat(s.totalUnavailableReason()).isEqualTo("MIXED_CURRENCIES");
    }
    @Test void cost003_partialSingleCurrencyIsSubtotalOnly() {
        event("a",100,0,0,0,0);event("b",200,0,0,0,0);price("a","USD");
        assertThat(cost().pricingCoverage()).isEqualTo("PARTIAL");assertThat(cost().estimatedCost()).isNull();
        assertThat(cost().totalUnavailableReason()).isEqualTo("INCOMPLETE_PRICING");
    }
    @Test void cost004_allUnpricedIsNoneNotZero() {
        event("a",100,0,0,0,0);assertThat(cost().pricingCoverage()).isEqualTo("NONE");
        assertThat(cost().estimatedCost()).isNull();assertThat(cost().costsByCurrency()).isEmpty();
        assertThat(records().getFirst()).containsEntry("pricingStatus","UNPRICED").containsEntry("pricingReason","NO_MATCHING_PRICE").containsEntry("estimatedCost",null);
    }
    @Test void cost005_explicitZeroPricingIsValidPricedZero() throws Exception {
        event("a",100,0,0,20,0);price("a","JPY","0","0","0","0","0");
        assertThat(cost().pricingCoverage()).isEqualTo("COMPLETE");assertThat(cost().estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
        mvc.perform(get("/api/usage/summary").with(user("test"))).andExpect(jsonPath("$.cost.estimatedCost").value("0.000000000000"));
    }
    @Test void cost006_unpricedCountsTokensAndFiltersAreRetained() {
        event("a",100,0,0,20,0);event("b",200,0,0,40,0);event("c",300,0,0,50,0);price("a","USD");price("b","CNY");var s=cost();
        assertThat(s.usageCount()).isEqualTo(3);assertThat(s.pricedUsageCount()).isEqualTo(2);assertThat(s.unpricedUsageCount()).isEqualTo(1);
        assertThat(s.pricedTokens()).isEqualTo(360);assertThat(s.unpricedTokens()).isEqualTo(350);assertThat(s.totalTokens()).isEqualTo(710);
        assertThat(s.pricingCoverage()).isEqualTo("PARTIAL");assertThat(s.mixedCurrency()).isTrue();
        var filtered=(UsageCostAccounting.Summary)usage.summary("24h",null,null,"provider","c",null,null).get("cost");
        assertThat(filtered.usageCount()).isEqualTo(1);assertThat(filtered.pricingCoverage()).isEqualTo("NONE");
    }
    @Test void cost007_breakdownIsExplicitlyTokenOnlyAndRecordsRetainCurrency() {
        event("a",100,0,0,0,0);event("b",200,0,0,0,0);price("a","USD");price("b","CNY");
        var b=usage.breakdown("24h",null,null);assertThat(b).containsEntry("metric","TOKENS_ONLY");
        assertThat(b.toString()).doesNotContain("estimatedCost","totalCost");
        assertThat(records()).extracting(x->x.get("currency")).containsExactlyInAnyOrder("USD","CNY");
    }
    @Test void cost008_seriesOnlyCountsTokensAcrossCurrencies() {
        event("a",100,0,0,0,0);event("b",200,0,0,0,0);price("a","USD");price("b","CNY");
        var series=usage.series("24h",null,null,null,null,null,null);
        assertThat(series).allSatisfy(row->assertThat(row).doesNotContainKeys("cost","estimatedCost","currency"));
        assertThat(series.stream().mapToLong(x->((Number)x.get("inputTokens")).longValue()).sum()).isEqualTo(300);
    }
    @Test void cost009_currentPriceRevaluesHistoricalUsage() throws Exception {
        event("a",1_000_000,0,0,0,0);price("a","USD");assertThat(cost().estimatedCost()).isEqualByComparingTo("1");
        mvc.perform(patch("/api/pricing").with(user("test")).with(csrf()).contentType("application/json").content("{\"revision\":0,\"rows\":[{\"provider\":\"provider\",\"model\":\"a\",\"input\":2,\"currency\":\"USD\"}]}"))
                .andExpect(status().isOk());
        assertThat(cost().estimatedCost()).isEqualByComparingTo("2");assertThat(cost().usageCount()).isEqualTo(1);
    }
    @Test void cost010_decimalPrecisionAndRoundAfterCurrencyAccumulation() throws Exception {
        event("a",1,0,0,0,0);event("a",1,0,0,0,0);price("a","USD","0.0000004",null,null,null,null);
        assertThat(cost().estimatedCost()).isEqualByComparingTo("0.000000000001");
        assertThat(cost().estimatedCost().scale()).isEqualTo(12);
        mvc.perform(get("/api/usage/summary").with(user("test"))).andExpect(jsonPath("$.cost.costsByCurrency.USD.estimatedCost").value("0.000000000001"));
        mvc.perform(patch("/api/pricing").with(user("test")).with(csrf()).contentType("application/json").content("{\"revision\":0,\"rows\":[{\"provider\":\"provider\",\"model\":\"a\",\"input\":0.123456789012345678,\"currency\":\"USD\"}]}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT input FROM console_pricing",String.class)).isEqualTo("0.123456789012345678");
        assertThat(cost().estimatedCost()).isEqualByComparingTo("0.000000246914");
    }
    @Test void cost011_nonzeroCategoryMissingRateInvalidatesEntireRow() {
        event("a",100,20,0,40,0);event("a",100,0,0,40,0);price("a","USD","1",null,null,"2",null);
        assertThat(cost().pricingCoverage()).isEqualTo("PARTIAL");assertThat(cost().pricedUsageCount()).isEqualTo(1);
        assertThat(cost().unpricedUsageCount()).isEqualTo(1);assertThat(cost().unpricedTokens()).isEqualTo(140);
        assertThat(records()).anySatisfy(row->assertThat(row).containsEntry("pricingReason","MISSING_CACHE_HIT_RATE").containsEntry("estimatedCost",null));
    }
    @Test void cost012_zeroCategoryMissingRateDoesNotInvalidateRow() {
        event("a",100,0,0,40,0);price("a","USD","1",null,null,"2",null);
        assertThat(cost().pricingCoverage()).isEqualTo("COMPLETE");assertThat(cost().estimatedCost()).isEqualByComparingTo("0.00018");
    }
    @Test void cost013_currencyValidationAndExactProviderModelMatch() throws Exception {
        for(String currency:List.of("USD","CNY","JPY","usd","US","USDD","123","")) {
            String body="{\"revision\":"+jdbc.queryForObject("SELECT revision FROM console_pricing_meta",Long.class)+",\"rows\":[{\"provider\":\"provider\",\"model\":\"a\",\"input\":1,\"currency\":\""+currency+"\"}]}";
            mvc.perform(patch("/api/pricing").with(user("test")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().is(Set.of("USD","CNY","JPY").contains(currency)?200:400));
        }
        event("other",100,0,0,0,0);
        jdbc.update("INSERT INTO token_usage(id,provider,model,usage_time,input_tokens) VALUES('other-provider','other','a',?,100)",Instant.now().minusSeconds(60).toString());
        assertThat(cost().pricingCoverage()).isEqualTo("NONE");assertThat(cost().unpricedUsageCount()).isEqualTo(2);
    }
    @Test void cost014_emptyUsageAndNumericOnlyPrivateRecords() throws Exception {
        assertThat(cost().pricingCoverage()).isEqualTo("NONE");assertThat(cost().usageCount()).isZero();assertThat(cost().estimatedCost()).isNull();
        event("a",100,0,0,0,0);
        String body=mvc.perform(get("/api/usage/records").with(user("test"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("pricingStatus","NO_MATCHING_PRICE").doesNotContain("message","prompt","memory","hiddenReasoning");
    }
    @Test void cost015_legacyReadApisNeverSumPlaceholderCharges() throws Exception {
        event("a",100,0,0,20,0);jdbc.update("UPDATE token_usage SET cost=123.45");
        mvc.perform(get("/api/usage").with(user("test"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("TOKENS_ONLY")).andExpect(jsonPath("$.cost").doesNotExist())
                .andExpect(jsonPath("$.inputTokens").value(100)).andExpect(jsonPath("$.outputTokens").value(20));
        mvc.perform(get("/api/usage/conversations").with(user("test"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metric").value("TOKENS_ONLY")).andExpect(jsonPath("$[0].cost").doesNotExist());
    }
}
