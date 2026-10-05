package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.core.model.*;
import java.util.*;

/** Determines whether an event requires, permits, or forbids model participation; identity resolution remains separate. */
public final class SocialTriggerPolicy {
    private final String botId;private final Set<String> mustReplyKeywords;private final Set<String> botNames;
    public SocialTriggerPolicy(String botId,Collection<String> mustReplyKeywords){this(botId,mustReplyKeywords,Set.of());}
    public SocialTriggerPolicy(String botId,Collection<String> mustReplyKeywords,Collection<String> botNames){this.botId=Objects.requireNonNullElse(botId,"");this.mustReplyKeywords=normalize(mustReplyKeywords);this.botNames=normalize(botNames);}
    public SocialTrigger evaluate(PlatformEvent event){
        return evaluate(event,Set.of()).trigger();
    }
    public WakeDecision evaluate(PlatformEvent event,Collection<String> configuredSpeakers){
        return evaluate(event,configuredSpeakers,null);
    }
    public WakeDecision evaluate(PlatformEvent event,Collection<String> configuredSpeakers,SocialSettingsSnapshot settings){
        if(event.actor().self()||event.eventType().startsWith("notice:"))return new WakeDecision(SocialTrigger.IGNORE,Set.of());
        Set<String> reasons=new LinkedHashSet<>();String text=Objects.requireNonNullElse(event.text(),"").toLowerCase(Locale.ROOT);
        if(event.conversation().type()==ConversationType.PRIVATE)reasons.add("private");
        if(event.actor().owner())reasons.add("owner");
        if(mustReplyKeywords.stream().anyMatch(k->!k.isBlank()&&text.contains(k)))reasons.add("must-reply-keyword");
        boolean mention=settings==null||settings.mentionWake();
        boolean reply=settings==null||settings.replyToBotWake();
        boolean poke=settings==null||settings.pokeWake();
        boolean name=settings==null||settings.botNameWake();
        boolean question=settings==null||settings.questionWake();
        boolean speaker=settings==null||settings.configuredSpeakerWake();
        boolean manual=settings==null||settings.manualWakeEnabled();
        if(mention&&!botId.isBlank()&&event.details().mentionIds().contains(botId))reasons.add("mention");
        if(reply&&event.details().reply()!=null&&event.details().reply().quotedBot())reasons.add("reply-to-bot");
        if(poke&&"poke".equals(event.eventType())&&Objects.equals(event.details().pokeTargetId(),botId))reasons.add("poke");
        if(name&&(botNames.stream().anyMatch(n->!n.isBlank()&&text.contains(n))||(settings!=null&&settings.botNameAliases().stream().map(s->s.toLowerCase(Locale.ROOT)).anyMatch(n->text.contains(n)))))reasons.add("bot-name");
        if(question&&(text.stripTrailing().endsWith("?")||text.stripTrailing().endsWith("？")))reasons.add("question");
        if(manual&&"/wake".equals(text.trim())&&event.actor().owner())reasons.add("manual-wake");
        String stableSpeaker=event.actor().platform().name()+":"+event.actor().platformUserId();
        if(speaker&&((settings!=null&&settings.configuredSpeakerIds().stream().anyMatch(s->s.equalsIgnoreCase(stableSpeaker)))||(configuredSpeakers!=null&&configuredSpeakers.contains(event.actor().platformUserId()))))reasons.add("configured-speaker");
        boolean forced=reasons.stream().anyMatch(r->!r.equals("configured-speaker"));
        SocialTrigger trigger=forced?SocialTrigger.MUST_REPLY:reasons.isEmpty()?SocialTrigger.MAY_REPLY:SocialTrigger.MAY_REPLY;
        return new WakeDecision(trigger,reasons);
    }
    private static Set<String> normalize(Collection<String> values){return values==null?Set.of():values.stream().filter(Objects::nonNull).map(s->s.toLowerCase(Locale.ROOT).trim()).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
}
