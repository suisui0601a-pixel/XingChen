package online.wanan.xingchen.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.dsh.*;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.storage.SqliteDshSessionMappingRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Receives DSH waterfall events, persists first, then presents safe owner-only QQ messages. */
public final class DshInteractionCoordinator implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger(DshInteractionCoordinator.class.getName());
    private final DshGateway dsh;private final SqliteDshSessionMappingRepository mappings;private final JdbcTemplate jdbc;
    private final DshPendingInteractionStore pending;private final OneBotGateway onebot;private final ObjectMapper mapper;private final Clock clock;private final String ownerQq;
    private final AtomicBoolean started=new AtomicBoolean();private volatile DshInteractionSubscription subscription;
    public DshInteractionCoordinator(DshGateway dsh,SqliteDshSessionMappingRepository mappings,JdbcTemplate jdbc,DshPendingInteractionStore pending,
                                    OneBotGateway onebot,ObjectMapper mapper,Clock clock,String ownerQq){
        this.dsh=dsh;this.mappings=mappings;this.jdbc=jdbc;this.pending=pending;this.onebot=onebot;this.mapper=mapper;this.clock=clock;this.ownerQq=ownerQq;
    }
    public synchronized void start(){if(!started.compareAndSet(false,true))return;try{pending.expireDue();subscription=dsh.interactionEvents(this::accept,e->LOG.log(System.Logger.Level.WARNING,"DSH interaction stream failed; awaiting reconnect"));}catch(RuntimeException e){started.set(false);throw e;}}
    public boolean isRunning(){return started.get()&&subscription!=null&&subscription.isOpen();}
    private void accept(DshInteractionEvent event){
        try{
            if(!(event instanceof DshApprovalRequest)&&!(event instanceof DshUserQuestionRequest))return;
            if(ownerQq==null||ownerQq.isBlank()||event.agentId().isBlank())return;
            var mapping=mappings.currentBySession(event.agentId());if(mapping==null||!"CLOSED_AGENT".equals(mapping.mode()))return;
            var conversation=jdbc.query("SELECT platform,type,platform_conversation_id FROM conversations WHERE id=?",(rs,n)->new ConversationRow(rs.getString(1),rs.getString(2),rs.getString(3)),mapping.conversationId().toString()).stream().findFirst().orElse(null);
            // High-privilege decisions are never delivered into groups or a non-owner private chat.
            if(conversation==null||!"QQ".equals(conversation.platform())||!"PRIVATE".equals(conversation.type())||!ownerQq.equals(conversation.platformConversationId()))return;
            var now=clock.instant();String id=UUID.randomUUID().toString();DshPendingInteractionStore.Interaction item;
            if(event instanceof DshApprovalRequest a)item=new DshPendingInteractionStore.Interaction(id,DshPendingInteractionStore.Kind.APPROVAL,event.agentId(),mapping.conversationId().toString(),event.clientId(),event.clientGeneration(),event.eventId(),"qq:"+ownerQq,now.plus(Duration.ofMinutes(10)),"PENDING",null,a.toolName(),a.callId(),a.reason(),now,now);
            else {var q=(DshUserQuestionRequest)event;var data=mapper.createObjectNode();data.set("questions",mapper.valueToTree(q.questions()));item=new DshPendingInteractionStore.Interaction(id,DshPendingInteractionStore.Kind.QUESTION,event.agentId(),mapping.conversationId().toString(),event.clientId(),event.clientGeneration(),event.eventId(),"qq:"+ownerQq,now.plus(Duration.ofMinutes(10)),"PENDING",data,null,null,null,now,now);}
            var replay=pending.replay(item);if(replay.terminal()){LOG.log(System.Logger.Level.WARNING,"terminal DSH interaction replay ignored; event="+shortEvent(event.eventId()));return;}dsh.bindInteractionGeneration(replay.interaction().clientId(),replay.interaction().clientGeneration());if(!replay.created())return;
            var receipt=onebot.sendPrivateMessage(ownerQq,presentation(replay.interaction()));if(!receipt.accepted())pending.interrupt(replay.interaction().id());
        }catch(RuntimeException e){LOG.log(System.Logger.Level.WARNING,"DSH interaction event was rejected safely");}
    }
    private String presentation(DshPendingInteractionStore.Interaction interaction){
        if(interaction.kind()==DshPendingInteractionStore.Kind.APPROVAL){String reason=interaction.reason()==null||interaction.reason().isBlank()?"（未提供）":interaction.reason();return "DSH 工具审批 #"+shortId(interaction)+"\n工具："+interaction.toolName()+"\n原因："+reason+"\n仅限你本人回复“通过”或“拒绝”。有效期 10 分钟。";}
        try{
            List<DshInteractionEvent.Question> list=mapper.convertValue(interaction.questions().path("questions"),mapper.getTypeFactory().constructCollectionType(List.class,DshInteractionEvent.Question.class));List<String> lines=new ArrayList<>();lines.add("DSH 有问题需要回复 #"+shortId(interaction)+"（有效期 10 分钟）：");
            for(int i=0;i<list.size();i++){var q=list.get(i);lines.add((i+1)+". "+q.question());if(!q.options().isEmpty())lines.add("   可选："+String.join(" / ",q.options()));}
            lines.add(list.size()==1?"直接回复答案，或使用 1:答案。":"请逐项回复，格式如 1:production\\n2:否；不要省略序号。");return String.join("\n",lines);
        }catch(RuntimeException e){return "DSH 问题格式无效，请在控制台检查。";}
    }
    private static String shortId(DshPendingInteractionStore.Interaction i){return i.id().replace("-","").substring(0,6);}
    private static String shortEvent(String eventId){return eventId==null?"unknown":eventId.substring(0,Math.min(8,eventId.length()));}
    private record ConversationRow(String platform,String type,String platformConversationId){}
    @Override public synchronized void close(){if(!started.compareAndSet(true,false))return;var s=subscription;subscription=null;if(s!=null)s.close();}
}
