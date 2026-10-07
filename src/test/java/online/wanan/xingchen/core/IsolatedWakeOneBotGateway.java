package online.wanan.xingchen.core;

import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.model.PlatformEvent;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Delegates to loopback Fake OneBot; only the unrelated polling wait is made immediate for bounded acceptance. */
final class IsolatedWakeOneBotGateway implements OneBotGateway {
    private final OneBotGateway delegate;
    IsolatedWakeOneBotGateway(OneBotGateway delegate){this.delegate=Objects.requireNonNull(delegate);}
    @Override public GatewayStatus connect(){return delegate.connect();}
    @Override public GatewayStatus disconnect(){return delegate.disconnect();}
    @Override public GatewayStatus status(){return delegate.status();}
    @Override public Optional<OneBotAccountInfo> getLoginInfo(){return delegate.getLoginInfo();}
    @Override public String probeHttpConnection(){return delegate.probeHttpConnection();}
    @Override public SendReceipt sendPrivateMessage(String id,String text){return delegate.sendPrivateMessage(id,text);}
    @Override public SendReceipt sendGroupMessage(String id,String text){return delegate.sendGroupMessage(id,text);}
    @Override public SendReceipt replyMessage(String id,String text){return delegate.replyMessage(id,text);}
    @Override public List<PlatformEvent> getRecentMessages(String id,int limit){return delegate.getRecentMessages(id,limit);}
    @Override public List<PlatformEvent> getUnreadMessages(String id,int limit){return delegate.getUnreadMessages(id,limit);}
    @Override public Optional<GatewayMember> getGroupMember(String group,String user){return delegate.getGroupMember(group,user);}
    @Override public List<GatewayMember> getGroupMembers(String group){return delegate.getGroupMembers(group);}
    @Override public SendReceipt sendPoke(String group,String user){return delegate.sendPoke(group,user);}
    @Override public SendReceipt sendImage(String target,String kind,String asset){return delegate.sendImage(target,kind,asset);}
    @Override public void setEventListener(Consumer<PlatformEvent> listener){delegate.setEventListener(listener);}
    @Override public GatewayStatus reconnect(){return delegate.reconnect();}
    @Override public Optional<PlatformEvent> getMessage(String id){return delegate.getMessage(id);}
    @Override public List<PlatformEvent> waitForMessages(String id,Duration timeout){return List.of();}
}
