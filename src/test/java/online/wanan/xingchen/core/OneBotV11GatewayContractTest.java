package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.onebot.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class OneBotV11GatewayContractTest {
    @Test void ob115_httpAndWebSocketUseIndependentCredentials() throws Exception {
        try(var f=new FakeOneBotServer("http-secret","ws-secret");var g=new OneBotV11Gateway(
                new OneBotV11Configuration(f.httpUri(),f.wsUri(),"HTTP_TOKEN","WS_TOKEN","bot",false,1000,1500,50,200,20,90_000),
                new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("HTTP_TOKEN","http-secret","WS_TOKEN","ws-secret"))) {
            assertThat(g.connect().connected()).isTrue();await(()->f.wsConnections()==1);
            assertThat(f.lastWsAuthorization()).isEqualTo("Bearer ws-secret");
            assertThat(g.getLoginInfo()).isPresent();
            assertThat(f.lastHttpAuthorization()).isEqualTo("Bearer http-secret");
        }
    }
    @Test void ob112_loginInfoReadsOnlyDocumentedOneBotAccountIdentity() throws Exception{try(var f=new FakeOneBotServer("secret");var g=new OneBotV11Gateway(new OneBotV11Configuration(f.httpUri(),f.wsUri(),"TOKEN","bot",false,1000,1500,50,200,20),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN","secret"))){assertThat(g.connect().connected()).isTrue();var account=g.getLoginInfo().orElseThrow();assertThat(account.userId()).isEqualTo("100000001");assertThat(account.nickname()).isEqualTo("Fake Bot");assertThat(f.actionCount("get_login_info")).isEqualTo(1);}}
    @Test void ob113_configChangesReconnectTheSameTransportWithoutRestartingTheJvm() throws Exception{try(var first=new FakeOneBotServer("one");var second=new FakeOneBotServer("two");var g=new OneBotV11Gateway(config(first,false,"TOKEN"),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN","one"))){assertThat(g.connect().connected()).isTrue();await(()->first.wsConnections()>0);var next=new OneBotV11Configuration(second.httpUri(),second.wsUri(),"TOKEN","bot",false,1000,1500,50,200,20);assertThat(g.reconfigure(next,Map.of("TOKEN","two"),()->true).connected()).isTrue();await(()->second.wsConnections()>0);assertThat(g.getLoginInfo()).get().extracting("userId").isEqualTo("100000001");assertThat(second.actionCount("get_login_info")).isEqualTo(1);assertThat(first.wsConnections()).isEqualTo(1);}}
    private OneBotV11Configuration config(FakeOneBotServer f,boolean allow,String envName){return new OneBotV11Configuration(f.httpUri(),f.wsUri(),envName,"bot",allow,1000,1500,50,200,20);}
    private OneBotV11Gateway gateway(FakeOneBotServer f,String secret){return new OneBotV11Gateway(config(f,false,"TOKEN"),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN",secret));}
    private void await(java.util.function.BooleanSupplier condition){long until=System.nanoTime()+Duration.ofSeconds(3).toNanos();while(System.nanoTime()<until){if(condition.getAsBoolean())return;try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("readiness wait interrupted",e);}}throw new AssertionError("readiness condition was not reached");}
    @Test void ob101_wsConnectAndNormalizedEventDelivery() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){List<online.wanan.xingchen.core.model.PlatformEvent> received=new CopyOnWriteArrayList<>();g.setEventListener(received::add);assertThat(g.connect().connected()).isTrue();await(()->f.wsConnections()==1);String event="{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"bot\",\"user_id\":\"u1\",\"group_id\":\"g1\",\"message_id\":\"m1\",\"sender\":{\"user_id\":\"u1\",\"nickname\":\"N\"},\"message\":[{\"type\":\"at\",\"data\":{\"qq\":\"bot\"}},{\"type\":\"reply\",\"data\":{\"id\":\"m0\",\"user_id\":\"bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"hi\"}}]}";try{f.emit(event);}catch(Exception e){throw new AssertionError(e);}await(()->received.size()==1);var parsed=received.getFirst();assertThat(parsed.text()).isEqualTo("hi");assertThat(parsed.details().mentionIds()).containsExactly("bot");assertThat(parsed.details().reply().quotedBot()).isTrue();assertThat(parsed.platform()).hasToString("QQ");}}
    @Test void ob102_httpActionsSendPrivateGroupReplyAndPokeWithBearerAuth() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){g.sendPrivateMessage("u1","hi");g.sendGroupMessage("g1","hello");g.replyMessage("m1","reply");g.sendPoke("g1","u2");assertThat(f.actionCount("send_private_msg")).isEqualTo(1);assertThat(f.actionCount("send_group_msg")).isEqualTo(1);assertThat(f.actionCount("send_msg")).isEqualTo(1);assertThat(f.actionCount("send_poke")).isEqualTo(1);assertThat(f.lastHttpAuthorization()).isEqualTo("Bearer secret");}}
    @Test void ob103_memberAndMessageQueriesNormalizeApiResponses() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){assertThat(g.getGroupMember("g1","u1")).get().satisfies(m->assertThat(m.displayName()).isEqualTo("Fake User"));assertThat(g.getGroupMembers("g1")).hasSize(1);assertThat(g.getMessage("m1")).get().extracting(e->e.text()).isEqualTo("quoted");assertThat(g.getRecentMessages("g1",10)).isEmpty();assertThat(f.actionCount("get_msg")).isEqualTo(1);}}
    @Test void ob104_reconnectsAfterFakeServerDropsWebSocket() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){assertThat(g.connect().connected()).isTrue();await(()->f.wsConnections()==1);try{f.disconnectWebSocket();}catch(Exception e){throw new AssertionError(e);}await(()->f.wsConnections()>=2&&g.status().connected());await(()->g.status().reconnectCount()==0);assertThat(g.status().reconnectCount()).isZero();}}
    @Test void ob105_httpUnauthorizedFailsClosedWithoutLeakingToken() throws Exception{try(var f=new FakeOneBotServer("correct");var g=gateway(f,"wrong")){assertThat(g.sendGroupMessage("g1","hi").accepted()).isFalse();assertThat(f.actionCount("send_group_msg")).isEqualTo(1);assertThat(f.lastHttpAuthorization()).isEqualTo("Bearer wrong");}}
    @Test void ob106_httpServerErrorReturnsUnknownReceipt() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){f.failNext("send_private_msg",503);var receipt=g.sendPrivateMessage("u","hi");assertThat(receipt.accepted()).isFalse();assertThat(receipt.status()).isEqualTo(DeliveryStatus.UNKNOWN);assertThat(f.actionCount("send_private_msg")).isEqualTo(1);}}
    @Test void audit_httpTimeoutIsUnknownAndMustNotBeRetriedBlindly() throws Exception{try(var f=new FakeOneBotServer("secret");var g=new OneBotV11Gateway(new OneBotV11Configuration(f.httpUri(),f.wsUri(),"TOKEN","bot",false,1000,100,50,200,20),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN","secret"))){f.delayNext("send_group_msg",300);var receipt=g.sendGroupMessage("g1","hi");assertThat(receipt.accepted()).isFalse();assertThat(receipt.status()).isEqualTo(DeliveryStatus.UNKNOWN);}}
    @Test void ob107_nonLoopbackEndpointNeedsExplicitOptIn(){var c=new OneBotV11Configuration(URI.create("http://192.0.2.1:3000"),URI.create("ws://127.0.0.1:3001"),"TOKEN","bot",false,1000,1000,50,100,10);assertThatThrownBy(()->new OneBotV11Gateway(c,new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of())).hasMessageContaining("non-loopback");}
    @Test void ob108_disconnectPreventsAutomaticReconnect() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){g.connect();await(()->f.wsConnections()==1);g.disconnect();try{f.disconnectWebSocket();}catch(Exception ignored){}assertThat(f.wsConnections()).isEqualTo(1);assertThat(g.status().connected()).isFalse();}}
    @Test void ob109_onebotHeartbeatAndAccountStatusAreReported() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){var s=g.connect();assertThat(s.accountId()).isEqualTo("bot");assertThat(s.connected()).isTrue();assertThat(s.lastHeartbeat()).isNotNull();}}
    @Test void ob110_unreadCursorReturnsEachEventOnlyOnce() throws Exception{try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")){g.connect();await(()->f.wsConnections()==1);f.emit("{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"bot\",\"user_id\":\"u1\",\"group_id\":\"g1\",\"message_id\":\"unread-1\",\"sender\":{\"user_id\":\"u1\",\"nickname\":\"N\"},\"message\":\"hi\"}");await(()->g.getRecentMessages("g1",10).size()==1);assertThat(g.getUnreadMessages("g1",10)).hasSize(1);assertThat(g.getUnreadMessages("g1",10)).isEmpty();}}
    @Test void ob111_waitForMessagesUnblocksOnIncomingWebSocketEvent() throws Exception {
        try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")) {
            g.connect();await(()->f.wsConnections()==1);
            var waiting=new java.util.concurrent.CompletableFuture<List<online.wanan.xingchen.core.model.PlatformEvent>>();
            Thread waiter=Thread.ofPlatform().name("ob111-waiter").start(()->{try{waiting.complete(g.waitForMessages("g1",Duration.ofSeconds(30)));}catch(Throwable error){waiting.completeExceptionally(error);}});
            try {
                // Emit only once the dedicated waiter is actually inside the condition wait.
                await(()->waiter.getState()==Thread.State.TIMED_WAITING);
                f.emit("{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"bot\",\"user_id\":\"u1\",\"group_id\":\"g1\",\"message_id\":\"wait-1\",\"sender\":{\"user_id\":\"u1\",\"nickname\":\"N\"},\"message\":\"wake\"}");
                // Still require a prompt signal; passing cannot depend on the 30s wait expiring.
                assertThat(waiting.get(2,java.util.concurrent.TimeUnit.SECONDS)).singleElement().extracting(e->e.text()).isEqualTo("wake");
            } finally { waiter.interrupt();waiter.join(2000);assertThat(waiter.isAlive()).isFalse(); }
        }
    }
    @Test void ob114_eventBeforeWaitRegistrationCannotLoseTheWakeSignal() throws Exception {
        try(var f=new FakeOneBotServer("secret");var g=gateway(f,"secret")) {
            var field=OneBotV11Gateway.class.getDeclaredField("eventLock");field.setAccessible(true);
            var lock=(java.util.concurrent.locks.ReentrantLock)field.get(g);
            var accept=OneBotV11Gateway.class.getDeclaredMethod("accept",String.class);accept.setAccessible(true);
            var result=new java.util.concurrent.CompletableFuture<List<online.wanan.xingchen.core.model.PlatformEvent>>();
            Thread waiter=null;
            lock.lock();
            try {
                waiter=Thread.ofPlatform().start(()->result.complete(g.waitForMessages("g1",Duration.ofSeconds(30))));
                Thread registered=waiter;await(()->registered.getState()==Thread.State.WAITING);
                // Normalize/store/signal before this waiter can acquire the condition lock.
                accept.invoke(g,"{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"bot\",\"user_id\":\"u1\",\"group_id\":\"g1\",\"message_id\":\"before-wait\",\"sender\":{\"user_id\":\"u1\",\"nickname\":\"N\"},\"message\":\"early\"}");
            } finally { lock.unlock(); }
            try { assertThat(result.get(2,java.util.concurrent.TimeUnit.SECONDS)).singleElement().extracting(e->e.text()).isEqualTo("early"); }
            finally { if(waiter!=null){waiter.interrupt();waiter.join(2000);assertThat(waiter.isAlive()).isFalse();} }
        }
    }
    @Test void audit_missingWebSocketHeartbeatTriggersReconnect() throws Exception{try(var f=new FakeOneBotServer("secret");var g=new OneBotV11Gateway(new OneBotV11Configuration(f.httpUri(),f.wsUri(),"TOKEN","bot",false,1000,1000,50,200,20,120),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN","secret"))){assertThat(g.connect().connected()).isTrue();await(()->f.wsConnections()>=2);assertThat(f.wsConnections()).isGreaterThanOrEqualTo(2);}}
}
