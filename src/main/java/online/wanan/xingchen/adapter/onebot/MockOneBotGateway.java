package online.wanan.xingchen.adapter.onebot;

import online.wanan.xingchen.core.model.PlatformEvent;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Fixture-only gateway; it has no socket/HTTP implementation and can never reach a real account. */
public final class MockOneBotGateway implements OneBotGateway {
    private final OneBotEventNormalizer normalizer;
    private final String accountId;
    private final List<PlatformEvent> events=new CopyOnWriteArrayList<>();
    private final List<SendReceipt> sent=new CopyOnWriteArrayList<>();
    private final Map<String,Map<String,GatewayMember>> members=new HashMap<>();
    private boolean connected;
    public MockOneBotGateway(OneBotEventNormalizer normalizer,String accountId){this.normalizer=normalizer;this.accountId=accountId;}
    public PlatformEvent emit(String json,Collection<String> owners,Consumer<PlatformEvent> listener){PlatformEvent e=normalizer.normalize(json,owners);events.add(e);if(listener!=null)listener.accept(e);return e;}
    public void setMembers(String group,List<GatewayMember> roster){Map<String,GatewayMember> map=new HashMap<>();roster.forEach(m->map.put(m.userId(),m));members.put(group,map);}
    public List<SendReceipt> sent(){return List.copyOf(sent);}
    @Override public GatewayStatus connect(){connected=true;return status();}
    @Override public GatewayStatus disconnect(){connected=false;return status();}
    @Override public GatewayStatus status(){return new GatewayStatus(connected,accountId,connected?"mock connected":"mock disconnected");}
    @Override public SendReceipt sendPrivateMessage(String userId,String text){return record("private",userId,text);}
    @Override public SendReceipt sendGroupMessage(String groupId,String text){return record("group",groupId,text);}
    @Override public SendReceipt replyMessage(String id,String text){return record("reply",id,text);}
    @Override public List<PlatformEvent> getRecentMessages(String id,int limit){var matching=events.stream().filter(e->e.conversation().platformConversationId().equals(id)).toList();return matching.stream().skip(Math.max(0,matching.size()-Math.max(0,limit))).toList();}
    @Override public List<PlatformEvent> getUnreadMessages(String id,int limit){return getRecentMessages(id,limit);}
    @Override public Optional<GatewayMember> getGroupMember(String group,String user){return Optional.ofNullable(members.getOrDefault(group,Map.of()).get(user));}
    @Override public List<GatewayMember> getGroupMembers(String group){return List.copyOf(members.getOrDefault(group,Map.of()).values());}
    @Override public SendReceipt sendPoke(String group,String user){return record("poke",group+":"+user,"");}
    @Override public SendReceipt sendImage(String target,String kind,String path){return record("image-"+kind,target,path);}
    private SendReceipt record(String op,String target,String content){var receipt=new SendReceipt(connected,op,target,content);sent.add(receipt);return receipt;}
}
