package online.wanan.xingchen.adapter.onebot;

import online.wanan.xingchen.core.model.PlatformEvent;
import online.wanan.xingchen.core.model.ConversationIdentity;
import java.util.*;
import java.util.function.Consumer;

public interface OneBotGateway {
    GatewayStatus connect();
    GatewayStatus disconnect();
    GatewayStatus status();
    default java.util.Optional<OneBotAccountInfo> getLoginInfo() { return java.util.Optional.empty(); }
    /** Read-only HTTP credential/route probe (get_login_info). */
    default String probeHttpConnection() { return "UNAVAILABLE"; }
    SendReceipt sendPrivateMessage(String userId,String text);
    SendReceipt sendGroupMessage(String groupId,String text);
    SendReceipt replyMessage(String platformMessageId,String text);
    List<PlatformEvent> getRecentMessages(String conversationId,int limit);
    List<PlatformEvent> getUnreadMessages(String conversationId,int limit);
    /** Scoped, non-consuming reads. Only the domain's explicit markRead persists an acknowledgement. */
    default List<PlatformEvent> getRecentMessages(ConversationIdentity conversation,int limit){
        var matching=getRecentMessages(conversation.platformConversationId(),100).stream().filter(e->e.conversation().equals(conversation)).toList();
        return matching.stream().skip(Math.max(0,matching.size()-Math.max(0,limit))).toList();
    }
    default List<PlatformEvent> getUnreadMessages(ConversationIdentity conversation,String afterCursor,int limit){
        return afterCursor(getRecentMessages(conversation,100),afterCursor,limit);
    }
    default List<PlatformEvent> waitForMessages(ConversationIdentity conversation,String afterCursor,java.time.Duration timeout){
        return getUnreadMessages(conversation,afterCursor,20);
    }
    static List<PlatformEvent> afterCursor(List<PlatformEvent> events,String cursor,int limit){
        if(limit<=0)return List.of();
        int start=0;
        if(cursor!=null&&!cursor.isBlank()){
            int found=-1;for(int i=0;i<events.size();i++)if(events.get(i).message().platformMessageId().equals(cursor))found=i;
            if(found<0)throw new IllegalStateException("read cursor outside observed conversation window; refusing replay");
            start=found+1;
        }
        return List.copyOf(events.subList(start,Math.min(events.size(),start+limit)));
    }
    Optional<GatewayMember> getGroupMember(String groupId,String userId);
    List<GatewayMember> getGroupMembers(String groupId);
    SendReceipt sendPoke(String groupId,String userId);
    default SendReceipt sendImage(String target,String kind,String localAssetPath){return new SendReceipt(false,"image",target,"image delivery is not supported by this gateway");}
    /** Validated managed bytes, not a path in another container's filesystem. */
    default SendReceipt sendImageBytes(String target,String kind,byte[] bytes){return new SendReceipt(false,"image",target,"binary image delivery is not supported by this gateway");}
    default void setEventListener(Consumer<PlatformEvent> listener) {}
    default GatewayStatus reconnect(){disconnect();return connect();}
    default Optional<PlatformEvent> getMessage(String messageId){return Optional.empty();}
    default List<PlatformEvent> waitForMessages(String conversationId,java.time.Duration timeout){return getUnreadMessages(conversationId,20);}
}
