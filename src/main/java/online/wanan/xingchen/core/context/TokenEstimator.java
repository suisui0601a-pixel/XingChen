package online.wanan.xingchen.core.context;

@FunctionalInterface
public interface TokenEstimator { int estimate(String text); }
