package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OneBotBoundaryRegressionTest {
    @TempDir Path temp;
    private static OneBotV11Gateway gateway(FakeOneBotServer fake){return new OneBotV11Gateway(
        new OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"TOKEN","bot",false,2000,2000,50,200,10),
        new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN","synthetic"));}
    private static void accept(OneBotV11Gateway gateway,String type,String id,String text)throws Exception{
        var json=new LinkedHashMap<String,Object>();json.put("post_type","message");json.put("message_type",type);
        json.put("self_id","bot");json.put("user_id","same-id");json.put("group_id","same-id");json.put("message_id",id);
        json.put("sender",Map.of("user_id","same-id","nickname","Synthetic"));
        json.put("message",text.startsWith("[CQ:reply")?List.of(Map.of("type","reply","data",Map.of("id","quoted-id")),Map.of("type","text","data",Map.of("text","hello"))):text);
        var method=OneBotV11Gateway.class.getDeclaredMethod("accept",String.class);method.setAccessible(true);
        method.invoke(gateway,new ObjectMapper().writeValueAsString(json));
    }

    @Test void plainTextCannotBecomeCqImageAndReplyUsesSeparateTypedSegment() throws Exception {
        try(var fake=new FakeOneBotServer("synthetic");var gateway=gateway(fake)){
            String cq="[CQ:image,file=file:///synthetic-only]";
            assertThat(gateway.sendGroupMessage("same-id",cq).accepted()).isTrue();
            var message=fake.lastRequest("send_group_msg").path("message");
            assertThat(message.isArray()).isTrue();assertThat(message.size()).isEqualTo(1);
            assertThat(message.get(0).path("type").asText()).isEqualTo("text");
            assertThat(message.get(0).path("data").path("text").asText()).isEqualTo(cq);
            assertThat(gateway.replyMessage("synthetic-id",cq).accepted()).isTrue();
            var reply=fake.lastRequest("send_msg").path("message");
            assertThat(reply.size()).isEqualTo(2);assertThat(reply.get(0).path("type").asText()).isEqualTo("reply");
            assertThat(reply.get(1).path("type").asText()).isEqualTo("text");
            assertThat(reply.get(1).path("data").path("text").asText()).isEqualTo(cq);
        }
    }

    @Test void scopedUnreadIsNonConsumingAndSeparatesPrivateFromSameNumberedGroup() throws Exception {
        try(var fake=new FakeOneBotServer("synthetic");var gateway=gateway(fake)){
            var privateId=new ConversationIdentity(Platform.QQ,ConversationType.PRIVATE,"same-id");
            var groupId=new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"same-id");
            accept(gateway,"private","private-1","private");accept(gateway,"group","group-1","group");
            assertThat(gateway.getUnreadMessages(privateId,null,10)).extracting(PlatformEvent::text).containsExactly("private");
            assertThat(gateway.getUnreadMessages(privateId,null,10)).hasSize(1);
            assertThat(gateway.getUnreadMessages(groupId,null,10)).extracting(PlatformEvent::text).containsExactly("group");
            assertThat(gateway.getUnreadMessages(privateId,"private-1",10)).isEmpty();
            accept(gateway,"private","private-2","next");
            assertThat(gateway.getUnreadMessages(privateId,"private-1",10)).extracting(e->e.message().platformMessageId()).containsExactly("private-2");
            assertThat(gateway.getUnreadMessages(privateId,"private-1",10)).hasSize(1);
            for(int i=0;i<11;i++)accept(gateway,"private","evict-"+i,"new");
            assertThatThrownBy(()->gateway.getUnreadMessages(privateId,"private-1",10)).hasMessageContaining("refusing replay");
            gateway.getRecentMessages(new ConversationIdentity(Platform.QQ,ConversationType.PRIVATE,"empty-private"),10);
            assertThat(fake.actionCount("get_group_msg_history")).isZero();
        }
    }

    @Test void quoteHydrationDoesNotImportAnotherConversationsText() throws Exception {
        try(var fake=new FakeOneBotServer("synthetic");var gateway=gateway(fake)){
            var seen=new ArrayList<PlatformEvent>();gateway.setEventListener(seen::add);
            accept(gateway,"group","quote-event","[CQ:reply,id=quoted-id]hello");
            assertThat(fake.actionCount("get_msg")).isEqualTo(1); // fake returns group g1, not same-id
            assertThat(seen).hasSize(1);
            assertThat(seen.getFirst().details().reply().quotedText()).isEmpty();
            assertThat(seen.getFirst().details().reply().quotedUserId()).isEmpty();
        }
    }

    @Test void imageBytesReachWireAsBase64AndOversizedPayloadNeverDispatches() throws Exception {
        try(var fake=new FakeOneBotServer("synthetic");var gateway=gateway(fake)){
            byte[] bytes={1,2,3,4};assertThat(gateway.sendImageBytes("same-id","group",bytes).accepted()).isTrue();
            assertThat(fake.lastRequest("send_group_msg").path("message").get(0).path("data").path("file").asText())
                .isEqualTo("base64://"+Base64.getEncoder().encodeToString(bytes));
            assertThat(gateway.sendImageBytes("same-id","group",new byte[10*1024*1024+1]).accepted()).isFalse();
            assertThat(fake.actionCount("send_group_msg")).isEqualTo(1);
        }
    }

    @Test void realSymlinkIsRejectedBeforeHttpDispatch() throws Exception {
        Path original=Files.write(temp.resolve("original.png"),new byte[]{1,2,3});
        Path link=Files.createSymbolicLink(temp.resolve("link.png"),original);
        assertThat(Files.isSymbolicLink(link)).isTrue();
        try(var fake=new FakeOneBotServer("synthetic");var gateway=gateway(fake)){
            assertThat(gateway.sendImage("same-id","group",link.toString()).status()).isEqualTo(DeliveryStatus.REJECTED);
            assertThat(fake.actionCount("send_group_msg")).isZero();
        }
    }

    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void managedLibraryRejectsWindowsJunctionEscape() throws Exception {
        Path root=Files.createDirectories(temp.resolve("stickers")),library=Files.createDirectories(root.resolve("library"));
        Path outside=Files.createDirectories(temp.resolve("outside")),redirect=library.resolve("redirect");
        byte[] bytes={1,2,3,4};Files.write(outside.resolve("fixture.png"),bytes);
        var process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",redirect.toString(),outside.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        try{
            assertThat(redirect.resolve("fixture.png").toRealPath()).isEqualTo(outside.resolve("fixture.png").toRealPath());
            var id=UUID.randomUUID();String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            var metadata=new online.wanan.xingchen.core.sticker.StickerMetadata(id,"library/redirect/fixture.png",hash,List.of(),"",0,"synthetic",java.time.Instant.now(),null);
            var repository=org.mockito.Mockito.mock(online.wanan.xingchen.core.sticker.StickerRepository.class);
            org.mockito.Mockito.when(repository.get(id)).thenReturn(Optional.of(metadata));
            var fake=new MockOneBotGateway(new OneBotEventNormalizer(new ObjectMapper()),"bot");fake.connect();
            var service=new online.wanan.xingchen.core.sticker.StickerService(root,repository,new online.wanan.xingchen.core.sticker.StickerPolicy(1,java.time.Duration.ZERO),fake,java.time.Clock.systemUTC());
            assertThat(service.send(id,"same-id","group").status()).isEqualTo(DeliveryStatus.REJECTED);
            assertThat(fake.sent()).isEmpty();
        }finally{Files.deleteIfExists(redirect);}
    }
}
