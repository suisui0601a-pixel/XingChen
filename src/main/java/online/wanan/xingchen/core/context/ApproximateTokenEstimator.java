package online.wanan.xingchen.core.context;

/** Replaceable character-based estimate (~4 UTF-16 characters per token). */
public final class ApproximateTokenEstimator implements TokenEstimator {
    @Override public int estimate(String text){return text==null||text.isEmpty()?0:Math.max(1,(text.length()+3)/4);}
}
