package online.wanan.xingchen.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.dsh.*;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.identity.IdentityResolver;
import online.wanan.xingchen.core.identity.ResolvedActorContext;
import online.wanan.xingchen.core.model.ConversationType;
import online.wanan.xingchen.core.model.Platform;
import online.wanan.xingchen.core.model.PlatformEvent;

import java.time.Clock;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routes normalized incoming messages to one identity- and conversation-bound DSH interaction. */
public final class HumanInteractionRouter {
    private static final Pattern APPROVAL=Pattern.compile("^(通过|拒绝)(?:\\s+#([a-fA-F0-9]{6}))?$");
    private static final Pattern SELECTOR=Pattern.compile("#([a-fA-F0-9]{6})");
    private final IdentityResolver identities;private final DshPendingInteractionStore pending;private final DshGateway dsh;private final OneBotGateway onebot;
    private final DshRc2InteractionOutcomeEncoder encoder;private final QuestionAnswerParser questions;private final ObjectMapper mapper;private final Clock clock;
    public HumanInteractionRouter(IdentityResolver identities,DshPendingInteractionStore pending,DshGateway dsh,OneBotGateway onebot,
                                  DshRc2InteractionOutcomeEncoder encoder,QuestionAnswerParser questions,ObjectMapper mapper,Clock clock){
        this.identities=identities;this.pending=pending;this.dsh=dsh;this.onebot=onebot;this.encoder=encoder;this.questions=questions;this.mapper=mapper;this.clock=clock;
    }

    /** @return true when an outstanding DSH interaction consumed this message. */
    public synchronized boolean route(PlatformEvent event) {
        if(event==null||event.platform()!=Platform.QQ||!"message".equals(event.eventType())||event.text().isBlank())return false;
        ResolvedActorContext actor=identities.resolve(event);String conversation=actor.conversation().id().toString();
        List<DshPendingInteractionStore.Interaction> candidates=pending.pendingFor(conversation,actor.actorId());if(candidates.isEmpty()){if(!pending.pendingInConversation(conversation).isEmpty()){sendPrivate(event.conversation().platformConversationId(),"该请求绑定给其他用户，不能由你答复。");return true;}return false;}
        String selector=selector(event.text());DshPendingInteractionStore.Interaction interaction=select(candidates,selector);
        if(interaction==null){sendPrivate(event.conversation().platformConversationId(),selectionHelp(candidates));return true;}
        if(!interaction.expiresAt().isAfter(clock.instant())){pending.expire(interaction.id());sendPrivate(event.conversation().platformConversationId(),"该请求已过期。");return true;}
        if(interaction.kind()==DshPendingInteractionStore.Kind.APPROVAL)return approval(event,actor,interaction);
        return question(event,actor,interaction);
    }

    private boolean approval(PlatformEvent event,ResolvedActorContext actor,DshPendingInteractionStore.Interaction i){
        Matcher match=APPROVAL.matcher(event.text().trim());if(!match.matches())return false;
        if(match.group(2)!=null&&!shortId(i).equalsIgnoreCase(match.group(2)))return true;
        ApprovalDecision decision=match.group(1).equals("通过")?ApprovalDecision.ALLOW_ONCE:ApprovalDecision.REJECT;
        String terminal=decision==ApprovalDecision.ALLOW_ONCE?"APPROVED":"REJECTED";
        return submit(event,actor,i,"approval-"+decision.name().toLowerCase(Locale.ROOT)+"-v1-g"+i.clientGeneration(),encoder.approval(decision),terminal);
    }

    private boolean question(PlatformEvent event,ResolvedActorContext actor,DshPendingInteractionStore.Interaction i){
        List<DshInteractionEvent.Question> defs=questionDefinitions(i);String text=event.text().trim().replaceFirst("^#[a-fA-F0-9]{6}\\s+","");
        Map<String,String> parsed=questions.parse(defs,text);if(parsed.isEmpty()){sendPrivate(event.conversation().platformConversationId(),"请按提示提供答案；多问题请逐行使用“序号:答案”。");return true;}
        if(!pending.recordQuestionAnswers(i.id(),actor.actorId(),i.clientId(),i.clientGeneration(),parsed)){sendPrivate(event.conversation().platformConversationId(),"该请求已更新或不再接受回答，请等待最新提示。");return true;}
        Map<String,String> combined=new LinkedHashMap<>(pending.questionAnswers(i.id()));
        if(defs.stream().anyMatch(q->!combined.containsKey(q.id()))){sendPrivate(event.conversation().platformConversationId(),unanswered(defs,combined));return true;}
        var outcome=encoder.question(defs,combined);return submit(event,actor,i,"question-answer-v1-g"+i.clientGeneration(),outcome,"ANSWERED");
    }

    private boolean submit(PlatformEvent event,ResolvedActorContext actor,DshPendingInteractionStore.Interaction i,String outcomeVersion,
                           com.fasterxml.jackson.databind.JsonNode outcome,String terminal){
        if(!pending.claimAnswer(i.id(),actor.actorId(),i.clientId(),i.clientGeneration())){sendPrivate(event.conversation().platformConversationId(),"该请求已处理、过期或等待 DSH 重连。");return true;}
        if(!pending.reserveResult(i.dshSessionId(),i.eventId(),outcomeVersion)){pending.finishAnswer(i.id(),"UNKNOWN");sendPrivate(event.conversation().platformConversationId(),"该请求的提交结果不确定，为避免重复执行，系统不会自动重发。");return true;}
        try{
            dsh.respondToInteraction(i.clientId(),i.clientGeneration(),i.eventId(),outcome);
            pending.completeResult(i.dshSessionId(),i.eventId(),outcomeVersion,DshPendingInteractionStore.ResultStatus.SUCCESS);
            pending.finishAnswer(i.id(),terminal);return true;
        }catch(DshRc2Exception failure){
            if("interaction/stale-client".equals(failure.code())){pending.releaseAnswer(i.id());pending.completeResult(i.dshSessionId(),i.eventId(),outcomeVersion,DshPendingInteractionStore.ResultStatus.FAILED);sendPrivate(event.conversation().platformConversationId(),"DSH 连接已更新，请等待该请求重新同步后再答复。");return true;}
            boolean notDispatched=failure.code().equals("transport/pre-dispatch-timeout");
            boolean unknown=!notDispatched&&failure.code().startsWith("transport/")&& !failure.code().equals("transport/http");
            pending.completeResult(i.dshSessionId(),i.eventId(),outcomeVersion,unknown?DshPendingInteractionStore.ResultStatus.UNKNOWN:DshPendingInteractionStore.ResultStatus.FAILED,notDispatched?"PRE_DISPATCH_TIMEOUT":failure.code());
            pending.finishAnswer(i.id(),unknown?"UNKNOWN":"INTERRUPTED");String notice=unknown?"提交结果暂时无法确认；为避免重复执行，系统不会自动重发。":notDispatched?"请求在 HTTP dispatch 前超时，未发送给 DSH；本次请求已停止。":"DSH 明确拒绝了本次提交，该请求已停止。";sendPrivate(event.conversation().platformConversationId(),notice);return true;
        }catch(RuntimeException failure){
            pending.completeResult(i.dshSessionId(),i.eventId(),outcomeVersion,DshPendingInteractionStore.ResultStatus.UNKNOWN);pending.finishAnswer(i.id(),"UNKNOWN");
            sendPrivate(event.conversation().platformConversationId(),"提交结果无法确认；为避免重复执行，系统不会自动重发。");return true;
        }
    }

    private List<DshInteractionEvent.Question> questionDefinitions(DshPendingInteractionStore.Interaction i){
        try{return mapper.convertValue(i.questions().path("questions"),mapper.getTypeFactory().constructCollectionType(List.class,DshInteractionEvent.Question.class));}
        catch(IllegalArgumentException e){throw new IllegalStateException("persisted DSH question payload is malformed",e);}
    }
    private static String selector(String text){Matcher m=SELECTOR.matcher(text);return m.find()?m.group(1):null;}
    private static DshPendingInteractionStore.Interaction select(List<DshPendingInteractionStore.Interaction> candidates,String selector){
        if(candidates.size()==1)return selector==null||shortId(candidates.getFirst()).equalsIgnoreCase(selector)?candidates.getFirst():null;
        if(selector==null)return null;List<DshPendingInteractionStore.Interaction> matches=candidates.stream().filter(i->shortId(i).equalsIgnoreCase(selector)).toList();return matches.size()==1?matches.getFirst():null;
    }
    private static String shortId(DshPendingInteractionStore.Interaction i){return i.id().replace("-","").substring(0,6);}
    private static String selectionHelp(List<DshPendingInteractionStore.Interaction> items){return "当前有多个待处理请求，请在答复前附上对应编号："+items.stream().map(i->"#"+shortId(i)).distinct().reduce((a,b)->a+"、"+b).orElse("");}
    private static String unanswered(List<DshInteractionEvent.Question> defs,Map<String,String> answers){List<String> lines=new ArrayList<>();lines.add("答案已记录，请继续回答未完成的问题：");for(int n=0;n<defs.size();n++){var q=defs.get(n);if(!answers.containsKey(q.id()))lines.add((n+1)+". "+q.question());}lines.add("格式：序号:答案");return String.join("\n",lines);}
    private void sendPrivate(String userId,String text){onebot.sendPrivateMessage(userId,text);}
}
