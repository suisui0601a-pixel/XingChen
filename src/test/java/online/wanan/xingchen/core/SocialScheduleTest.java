package online.wanan.xingchen.core;

import online.wanan.xingchen.core.agent.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class SocialScheduleTest {
    final SocialSchedule schedule=new SocialSchedule(true,LocalTime.of(7,0),LocalTime.of(22,50),true,0,6,ZoneId.of("Asia/Shanghai"));
    Instant at(String local){return LocalDateTime.parse(local).atZone(schedule.zone()).toInstant();}
    @Test void randomQuietAndInboundSilenceHaveIndependentBoundaries(){
        var random=new ProactivePolicy(true,true,120,120,120,.3,8,23,8,"Asia/Shanghai");
        for(String time:new String[]{"00:00","05:59"})assertThat(schedule.muted(at("2026-11-20T"+time))).isTrue();
        for(String time:new String[]{"06:00","07:00","08:00","22:59","23:00"})assertThat(schedule.muted(at("2026-11-20T"+time))).isFalse();
        assertThat(random.quiet(at("2026-11-20T07:00"))).isTrue();assertThat(random.quiet(at("2026-11-20T23:00"))).isTrue();
        assertThat(random.quiet(at("2026-11-20T08:00"))).isFalse();assertThat(random.quiet(at("2026-11-20T22:50"))).isFalse();
    }
    @Test void greetingsUseLocalDateAndBoundedGraceWithoutCatchup(){
        assertThat(schedule.due(at("2026-11-20T06:59"))).isEmpty();
        var morning=schedule.due(at("2026-11-20T07:00")).getFirst();assertThat(morning.kind()).isEqualTo("MORNING");
        assertThat(morning.key()).isEqualTo("2026-11-20:MORNING");assertThat(morning.current(at("2026-11-20T07:09"))).isTrue();
        assertThat(schedule.due(at("2026-11-20T07:10"))).isEmpty();assertThat(schedule.due(at("2026-11-20T22:50")).getFirst().kind()).isEqualTo("EVENING");
        var edited=new SocialSchedule(true,LocalTime.of(7,15),schedule.evening(),true,0,6,schedule.zone());
        assertThat(edited.due(at("2026-11-20T07:15")).getFirst().key()).isEqualTo(morning.key());
        assertThat(schedule.due(at("2026-11-21T07:00")).getFirst().key()).isNotEqualTo(morning.key());
    }
    @Test void invalidTimesCannotEnterPersistentSettings(){
        for(String bad:new String[]{"24:00","7:00","12:60","07:00:00","garbage"})assertThatThrownBy(()->SocialSchedule.parseTime(bad)).isInstanceOf(IllegalArgumentException.class);
    }
}
