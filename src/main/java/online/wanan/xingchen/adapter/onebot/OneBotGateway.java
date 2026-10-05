package online.wanan.xingchen.adapter.onebot;

import online.wanan.xingchen.core.model.PlatformEvent;
import java.util.*;
import java.util.function.Consumer;

public interface OneBotGateway {
    GatewayStatus connect();
    GatewayStatus disconnect();
    GatewayStatus status();
    default java.util.Optional<OneBotAccountInfo> getLoginInfo() { return java.util.Optional.empty(); }
    SendReceipt sendPrivateMessage(String userId,String text);
    SendReceipt sendGroupMessage(String groupId,String text);
    SendReceipt replyMessage(String platformMessageId,String text);
    List<PlatformEvent> getRecentMessages(String conversationId,int limit);
    List<PlatformEvent> getUnreadMessages(String conversationId,int limit);
    Optional<GatewayMember> getGroupMember(String groupId,String userId);
    List<GatewayMember> getGroupMembers(String groupId);
    SendReceipt sendPoke(String groupId,String userId);
    default SendReceipt sendImage(String target,String kind,String localAssetPath){return new SendReceipt(false,"image",target,"image delivery is not supported by this gateway");}
    default void setEventListener(Consumer<PlatformEvent> listener) {}
    default GatewayStatus reconnect(){disconnect();return connect();}
    default Optional<PlatformEvent> getMessage(String messageId){return Optional.empty();}
    default List<PlatformEvent> waitForMessages(String conversationId,java.time.Duration timeout){return getUnreadMessages(conversationId,20);}
}
