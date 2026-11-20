package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.identity.ResolvedActorContext;
import online.wanan.xingchen.core.model.ConversationType;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Bounded multipart sender with explicit burst, pacing, text-size and rolling-rate limits. */
public final class ReplyDispatcher {
    private final OneBotGateway gateway;private final int maxMessages,maxChars,maxPerMinute;private final Duration gap;private final Clock clock;private final Consumer<Duration> sleeper;private final Deque<Instant> sent=new ArrayDeque<>();
    private final OutboundExecutionService outbound;
    public ReplyDispatcher(OneBotGateway gateway,int maxMessages,int maxChars,int maxPerMinute,Duration gap,Clock clock,Consumer<Duration> sleeper){this(gateway,maxMessages,maxChars,maxPerMinute,gap,clock,sleeper,null);}
    public ReplyDispatcher(OneBotGateway gateway,int maxMessages,int maxChars,int maxPerMinute,Duration gap,Clock clock,Consumer<Duration> sleeper,OutboundExecutionService outbound){this.gateway=Objects.requireNonNull(gateway);if(maxMessages<1||maxChars<1||maxPerMinute<1||gap.isNegative())throw new IllegalArgumentException("invalid reply limits");this.maxMessages=maxMessages;this.maxChars=maxChars;this.maxPerMinute=maxPerMinute;this.gap=gap;this.clock=clock;this.sleeper=sleeper;this.outbound=outbound;}
    public static ReplyDispatcher fromEnvironment(OneBotGateway gateway){return fromEnvironment(gateway,null);}
    public static ReplyDispatcher fromEnvironment(OneBotGateway gateway,OutboundExecutionService outbound){Map<String,String> env=System.getenv();return new ReplyDispatcher(gateway,integer(env,"XINGCHEN_REPLY_MAX_MESSAGES",8),integer(env,"XINGCHEN_REPLY_MAX_CHARS",2000),integer(env,"XINGCHEN_REPLY_MAX_PER_MINUTE",30),Duration.ofMillis(integer(env,"XINGCHEN_REPLY_GAP_MS",0)),Clock.systemUTC(),d->{try{Thread.sleep(d);}catch(InterruptedException e){Thread.currentThread().interrupt();}},outbound);}
    public List<SendReceipt> dispatch(ResolvedActorContext actor,List<String> messages){return dispatch(actor,messages,null,0);}
    public List<SendReceipt> dispatch(ResolvedActorContext actor,List<String> messages,String turnId,long generation){
        return dispatch(actor,messages,turnId,generation,null);
    }
    public List<SendReceipt> dispatch(ResolvedActorContext actor,List<String> messages,String turnId,long generation,online.wanan.xingchen.core.agent.SocialSettingsSnapshot settings){
        if(messages==null||messages.isEmpty())return List.of();
        int effectiveMax=settings==null?maxMessages:settings.maxReplyMessages(),effectiveRate=settings==null?maxPerMinute:settings.maxPerMinute();java.time.Duration effectiveGap=settings==null?gap:java.time.Duration.ofMillis(settings.gapMillis());
        List<SendReceipt> result=new ArrayList<>();int n=messages.size();
        for(int i=0;i<n;i++){
            if(i>0&&i<effectiveMax&&!effectiveGap.isZero())sleeper.accept(effectiveGap);
            String text=Objects.requireNonNullElse(messages.get(i),"");if(text.isBlank())continue;
            text=boundedText(text);
            final String sendText=text;
            String target=actor.conversation().identity().platformConversationId();
            String kind=actor.conversation().identity().type()==ConversationType.GROUP?"group":"private";
            String recipient=kind.equals("group")?target:actor.person().platformUserId();SendReceipt receipt;
            final int ordinal=i;
            java.util.function.Supplier<String> admission=()->Thread.currentThread().isInterrupted()?"send-cancelled":ordinal>=effectiveMax?"burst-limit":reserveRate(effectiveRate)?null:"rate-limited";
            if(outbound!=null&&turnId!=null&&!turnId.isBlank()){
                String executionKey=actor.conversation().id()+":"+turnId+":"+i;
                var record=outbound.dispatch(executionKey,actor.conversation().id(),turnId,generation,turnId+":"+i,recipient,admission,
                        ()->kind.equals("group")?gateway.sendGroupMessage(recipient,sendText):gateway.sendPrivateMessage(recipient,sendText));
                DeliveryStatus status=switch(record.status()){case SUCCESS->DeliveryStatus.ACCEPTED;case FAILED->DeliveryStatus.REJECTED;case UNKNOWN,PENDING->DeliveryStatus.UNKNOWN;};
                receipt=new SendReceipt(status==DeliveryStatus.ACCEPTED,"outbound",Objects.toString(record.providerMessageId(),recipient),
                        status==DeliveryStatus.ACCEPTED?sendText:Objects.toString(record.failureReason(),""),status);
            }else {
                String rejection=admission.get();
                receipt=rejection==null?(kind.equals("group")?gateway.sendGroupMessage(recipient,sendText):gateway.sendPrivateMessage(recipient,sendText))
                        :new SendReceipt(false,"outbound",recipient,rejection,DeliveryStatus.REJECTED);
            }
            result.add(receipt);
            // UNKNOWN may already have delivered remotely. Never send the remaining
            // parts automatically or reinterpret it as a safely retryable failure.
            if(receipt.status()==DeliveryStatus.UNKNOWN)break;
        }
        return List.copyOf(result);
    }
    String boundedText(String text){return text.length()>maxChars?text.substring(0,Math.max(0,maxChars-1))+"…":text;}
    /** Only this bounded accounting section is shared; no network IO or sleep under its lock.
     * Count attempts (including UNKNOWN) so failed/ambiguous sends cannot evade protection. */
    private synchronized boolean reserveRate(int limit){
        Instant now=clock.instant();while(!sent.isEmpty()&&!sent.getFirst().isAfter(now.minusSeconds(60)))sent.removeFirst();
        if(sent.size()>=limit)return false;
        sent.addLast(now);return true;
    }
    private static int integer(Map<String,String> env,String name,int fallback){try{return Integer.parseInt(env.getOrDefault(name,Integer.toString(fallback)));}catch(NumberFormatException e){return fallback;}}
}
