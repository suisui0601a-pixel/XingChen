package online.wanan.xingchen.core;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class DecimalPricingMigrationTest {
    @Test void existingRealRatesAndAuditSurviveDecimalMigration() {
        var db=TestSqliteDatabase.create("decimal-migration");
        var source=new DriverManagerDataSource(TestSqliteDatabase.jdbcUrl(db));
        var jdbc=new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).target("26").load().migrate();
            jdbc.update("INSERT INTO console_pricing VALUES('p','m',1.25,0,2.5,3.75,4,'CNY',8,'2026-10-01T00:00:00Z','test')");
            jdbc.update("INSERT INTO console_pricing_audit(occurred_at,actor,provider,model,new_input,new_cache_hit,new_cache_miss,new_output,new_reasoning,currency,revision) VALUES('2026-10-01T00:00:00Z','test','p','m',1.25,0,2.5,3.75,4,'CNY',8)");
            jdbc.update("UPDATE console_pricing_meta SET revision=8");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForMap("SELECT input,cache_hit,cache_miss,output,reasoning,currency,revision FROM console_pricing"))
                    .containsEntry("input","1.25").containsEntry("cache_hit","0.0").containsEntry("cache_miss","2.5").containsEntry("output","3.75").containsEntry("currency","CNY").containsEntry("revision",8);
            assertThat(jdbc.queryForObject("SELECT new_input FROM console_pricing_audit",String.class)).isEqualTo("1.25");
            assertThat(jdbc.queryForObject("SELECT revision FROM console_pricing_meta",Long.class)).isEqualTo(8L);
            jdbc.update("UPDATE console_pricing SET cache_hit=NULL");
            assertThat(jdbc.queryForObject("SELECT cache_hit FROM console_pricing",String.class)).isNull();
        } finally { TestSqliteDatabase.clean(db); }
    }
}
