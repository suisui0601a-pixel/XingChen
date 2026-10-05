package online.wanan.xingchen.core.agent;

import java.time.Duration;

public record ReservedV1Config(double triggerProbability,Duration activeCheckMin,Duration activeCheckMax,
        Duration idleWindow,double idleRetryProbability,Duration idleRetryWait,Duration activeDuration,
        double skipProbability,double surrenderProbability,double proactiveProbability,int burstMax,
        Duration longGapMin,Duration longGapMax) {
    public ReservedV1Config {prob(triggerProbability);prob(idleRetryProbability);prob(skipProbability);prob(surrenderProbability);prob(proactiveProbability);positive(activeCheckMin);positive(activeCheckMax);positive(idleWindow);positive(idleRetryWait);positive(activeDuration);positive(longGapMin);positive(longGapMax);if(activeCheckMin.compareTo(activeCheckMax)>0||longGapMin.compareTo(longGapMax)>0||burstMax<1||burstMax>5)throw new IllegalArgumentException("invalid reserved V1 ranges");}
    private static void prob(double p){if(p<0||p>1||Double.isNaN(p))throw new IllegalArgumentException("probability must be 0..1");}
    private static void positive(Duration d){if(d==null||d.isNegative()||d.isZero())throw new IllegalArgumentException("duration must be positive");}
    public static ReservedV1Config defaults(){return new ReservedV1Config(.3,Duration.ofSeconds(30),Duration.ofMinutes(2),Duration.ofMinutes(10),.1,Duration.ofMinutes(3),Duration.ofMinutes(5),.1,.1,.02,3,Duration.ofMinutes(10),Duration.ofMinutes(30));}
}
