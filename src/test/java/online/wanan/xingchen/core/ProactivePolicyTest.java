package online.wanan.xingchen.core;

import online.wanan.xingchen.core.agent.ProactivePolicy;
import online.wanan.xingchen.core.model.ConversationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class ProactivePolicyTest {
    ProactivePolicy policy(int start,int end,String zone){return new ProactivePolicy(true,false,30,30,180,.1,2,start,end,zone);}
    @ParameterizedTest @ValueSource(ints={23,0,7}) void quietPeriodWrapsMidnight(int hour){assertThat(policy(23,8,"UTC").quiet(Instant.parse("2026-11-20T00:00:00Z").plusSeconds(hour*3600L))).isTrue();}
    @ParameterizedTest @ValueSource(ints={8,12,22}) void endBoundaryIsExclusive(int hour){assertThat(policy(23,8,"UTC").quiet(Instant.parse("2026-11-20T00:00:00Z").plusSeconds(hour*3600L))).isFalse();}
    @Test void timezoneAndEqualEndpointsAreExplicit(){assertThat(policy(23,8,"Asia/Shanghai").quiet(Instant.parse("2026-11-20T15:00:00Z"))).isTrue();assertThat(policy(0,0,"UTC").quiet(Instant.EPOCH)).isFalse();}
    @Test void privateAndGroupConsentAreIndependent(){var p=policy(0,0,"UTC");assertThat(p.enabled(ConversationType.PRIVATE)).isTrue();assertThat(p.enabled(ConversationType.GROUP)).isFalse();}
    @ParameterizedTest @ValueSource(doubles={-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY}) void invalidProbabilityFailsClosed(double value){assertThatThrownBy(()->new ProactivePolicy(true,true,30,30,180,value,2,23,8,"UTC")).isInstanceOf(IllegalArgumentException.class);}
    @Test void zeroProbabilityDisablesBothScopes(){var p=new ProactivePolicy(true,true,30,30,180,0,2,0,0,"UTC");assertThat(p.enabled(ConversationType.PRIVATE)).isFalse();assertThat(p.enabled(ConversationType.GROUP)).isFalse();}
    @Test void invalidRangeAndTimezoneCannotStartScheduler(){assertThatThrownBy(()->new ProactivePolicy(true,true,0,30,180,.1,2,23,8,"UTC")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->policy(23,8,"not/a-zone")).isInstanceOf(DateTimeException.class);}
}
