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
        boolean mention=settings==null||settings.mentionWake();
        boolean reply=settings==null||settings.replyToBotWake();
        boolean poke=settings==null||settings.pokeWake();
        boolean structuredWake=(mention&&!botId.isBlank()&&event.details().mentionIds().contains(botId))
                ||(reply&&event.details().reply()!=null&&event.details().reply().quotedBot())
                ||(poke&&"poke".equals(event.eventType())&&Objects.equals(event.details().pokeTargetId(),botId));
        if(!hasVisibleText(text)&&!structuredWake)return new WakeDecision(SocialTrigger.IGNORE,Set.of());
        if(event.conversation().type()==ConversationType.PRIVATE)reasons.add("private");
        if(event.actor().owner())reasons.add("owner");
        if(mustReplyKeywords.stream().anyMatch(k->!k.isBlank()&&text.contains(k)))reasons.add("must-reply-keyword");
        boolean name=settings==null||settings.botNameWake();
        boolean question=settings==null||settings.questionWake();
        boolean speaker=settings==null||settings.configuredSpeakerWake();
        boolean manual=settings==null||settings.manualWakeEnabled();
        if(mention&&!botId.isBlank()&&event.details().mentionIds().contains(botId))reasons.add("mention");
        if(reply&&event.details().reply()!=null&&event.details().reply().quotedBot())reasons.add("reply-to-bot");
        if(poke&&"poke".equals(event.eventType())&&Objects.equals(event.details().pokeTargetId(),botId))reasons.add("poke");
        if(name&&(botNames.stream().anyMatch(n->matchesName(text,n))||(settings!=null&&settings.botNameAliases().stream().map(s->s.toLowerCase(Locale.ROOT)).anyMatch(n->matchesName(text,n)))))reasons.add("bot-name");
        if(question&&(text.stripTrailing().endsWith("?")||text.stripTrailing().endsWith("？")))reasons.add("question");
        if(manual&&"/wake".equals(text.trim())&&event.actor().owner())reasons.add("manual-wake");
        String stableSpeaker=event.actor().platform().name()+":"+event.actor().platformUserId();
        if(speaker&&((settings!=null&&settings.configuredSpeakerIds().stream().anyMatch(s->s.equalsIgnoreCase(stableSpeaker)))||(configuredSpeakers!=null&&configuredSpeakers.contains(event.actor().platformUserId()))))reasons.add("configured-speaker");
        boolean forced=!reasons.isEmpty();
        SocialTrigger trigger=forced?SocialTrigger.MUST_REPLY:reasons.isEmpty()?SocialTrigger.MAY_REPLY:SocialTrigger.MAY_REPLY;
        return new WakeDecision(trigger,reasons);
    }
    private static boolean hasVisibleText(String text){
        return text.codePoints().anyMatch(codePoint->{
            if(Character.isWhitespace(codePoint)||Character.isISOControl(codePoint))return false;
            int type=Character.getType(codePoint);
            return type!=Character.FORMAT&&type!=Character.SURROGATE&&type!=Character.UNASSIGNED&&type!=Character.PRIVATE_USE;
        });
    }
    private static boolean matchesName(String text,String name){
        if(name==null||name.isBlank())return false;
        boolean asciiWord=name.codePoints().allMatch(c->c<128&&(Character.isLetterOrDigit(c)||c=='_'));
        if(!asciiWord)return text.contains(name);
        for(int from=0;(from=text.indexOf(name,from))>=0;from++){
            int end=from+name.length();
            boolean left=from==0||!isAsciiWord(text.charAt(from-1));
            boolean right=end==text.length()||!isAsciiWord(text.charAt(end));
            if(left&&right)return true;
        }
        return false;
    }
    private static boolean isAsciiWord(char value){return value<128&&(Character.isLetterOrDigit(value)||value=='_');}
    private static Set<String> normalize(Collection<String> values){return values==null?Set.of():values.stream().filter(Objects::nonNull).map(s->s.toLowerCase(Locale.ROOT).trim()).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
}
