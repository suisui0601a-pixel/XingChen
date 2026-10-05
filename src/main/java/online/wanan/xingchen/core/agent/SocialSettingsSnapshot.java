package online.wanan.xingchen.core.agent;

import java.util.List;

/** Immutable effective social policy captured once at the start of an accepted conversation turn. */
public record SocialSettingsSnapshot(long globalRevision, long overrideRevision,
        boolean mentionWake, boolean replyToBotWake, boolean pokeWake, boolean botNameWake,
        List<String> botNameAliases, boolean questionWake, boolean configuredSpeakerWake,
        List<String> configuredSpeakerIds, boolean manualWakeEnabled,
        double ordinaryMessageProbability, int maxReplyMessages, int maxPerMinute, int gapMillis) {
    public SocialSettingsSnapshot {
        botNameAliases=List.copyOf(botNameAliases==null?List.of():botNameAliases);
        configuredSpeakerIds=List.copyOf(configuredSpeakerIds==null?List.of():configuredSpeakerIds);
        if(ordinaryMessageProbability<0||ordinaryMessageProbability>1||Double.isNaN(ordinaryMessageProbability)
                ||maxReplyMessages<1||maxReplyMessages>8||maxPerMinute<1||maxPerMinute>60||gapMillis<0||gapMillis>30_000)
            throw new IllegalArgumentException("invalid effective social settings");
    }
    public static SocialSettingsSnapshot defaults(){return new SocialSettingsSnapshot(0,0,true,true,true,true,List.of(),true,true,List.of(),true,0,8,30,0);}
}
