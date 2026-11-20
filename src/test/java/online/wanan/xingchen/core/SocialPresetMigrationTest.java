package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.console.SocialSettingsService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SocialPresetMigrationTest {
    @Test void upgradeBacksUpAndReplacesOldManualValuesButPreservesUnrelatedFieldsAndFutureEdits()throws Exception{
        var db=TestSqliteDatabase.create("social-preset-upgrade");var source=new DriverManagerDataSource(TestSqliteDatabase.jdbcUrl(db));var jdbc=new JdbcTemplate(source);var json=new ObjectMapper();UUID conversation=UUID.randomUUID();
        String oldGlobal="{\"ordinaryMessageProbability\":0,\"proactivePrivateEnabled\":false,\"proactiveGroupEnabled\":false,\"proactiveProbability\":0.9,\"proactiveIntervalMinutes\":5,\"proactiveZoneId\":\"UTC\",\"maxPerMinute\":7,\"mentionWake\":false,\"botNameAliases\":[\"synthetic-alias\"]}";
        String oldOverride="{\"proactiveGroupEnabled\":false,\"proactiveProbability\":0.8,\"ordinaryMessageProbability\":0.7,\"maxReplyMessages\":2,\"configuredSpeakerIds\":[\"QQ:synthetic-speaker\"]}";
        try{
            Flyway.configure().dataSource(source).target("29").load().migrate();
            jdbc.update("INSERT INTO conversations VALUES(?,'QQ','GROUP','preset-group','2026-11-20T00:00:00Z')",conversation.toString());
            jdbc.update("INSERT INTO social_settings_global VALUES(1,9,?,'2026-11-19T00:00:00Z','synthetic-admin')",oldGlobal);
            jdbc.update("INSERT INTO social_settings_override VALUES(?,4,?,'2026-11-19T00:00:00Z','synthetic-admin')",conversation.toString(),oldOverride);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("SELECT values_json FROM social_policy_preset_backups WHERE scope='GLOBAL'",String.class)).isEqualTo(oldGlobal);
            assertThat(jdbc.queryForObject("SELECT values_json FROM social_policy_preset_backups WHERE scope='CONVERSATION'",String.class)).isEqualTo(oldOverride);
            assertThat(jdbc.queryForObject("SELECT revision FROM social_settings_global",Integer.class)).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT revision FROM social_settings_override",Integer.class)).isEqualTo(5);
            var service=new SocialSettingsService(jdbc,json);var effective=(Map<?,?>)service.conversation(conversation).get("effective");
            assertThat(effective.get("ordinaryMessageProbability")).isEqualTo(.05);assertThat(effective.get("proactivePrivateEnabled")).isEqualTo(true);
            assertThat(effective.get("proactiveGroupEnabled")).isEqualTo(true);assertThat(effective.get("proactiveProbability")).isEqualTo(.3);
            assertThat(effective.get("proactiveIntervalMinutes")).isEqualTo(120);assertThat(effective.get("proactiveZoneId")).isEqualTo("Asia/Shanghai");
            assertThat(effective.get("dailyGreetingsEnabled")).isEqualTo(true);assertThat(effective.get("morningGreetingTime")).isEqualTo("07:00");assertThat(effective.get("eveningGreetingTime")).isEqualTo("22:50");
            assertThat(effective.get("nightReplyStartHour")).isEqualTo(0);assertThat(effective.get("nightReplyEndHour")).isEqualTo(6);
            assertThat(effective.get("mentionWake")).isEqualTo(false);assertThat(effective.get("maxPerMinute")).isEqualTo(7);assertThat(effective.get("maxReplyMessages")).isEqualTo(2);
            assertThat(effective.get("configuredSpeakerIds")).isEqualTo(List.of("QQ:synthetic-speaker"));
            service.updateGlobal(10,Map.of("proactiveProbability",.12,"dailyGreetingsEnabled",false),"synthetic-admin");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(((Map<?,?>)service.global().get("values")).get("proactiveProbability")).isEqualTo(.12);
            assertThat(((Map<?,?>)service.global().get("values")).get("dailyGreetingsEnabled")).isEqualTo(false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_policy_preset_backups",Integer.class)).isEqualTo(2);
        }finally{TestSqliteDatabase.clean(db);}
    }
}
