package online.wanan.xingchen.console;

import com.jayway.jsonpath.JsonPath;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.core.TestSqliteDatabase;
import online.wanan.xingchen.config.ConversationFakeE2eConfiguration;
import online.wanan.xingchen.core.slang.SlangRepository;
import online.wanan.xingchen.core.slang.SlangService;
import online.wanan.xingchen.core.sticker.StickerAssetService;
import online.wanan.xingchen.core.sticker.StickerPolicy;
import online.wanan.xingchen.core.sticker.StickerRepository;
import online.wanan.xingchen.core.sticker.StickerService;
import online.wanan.xingchen.core.agent.AgentCapability;
import online.wanan.xingchen.core.agent.AgentRequest;
import online.wanan.xingchen.core.agent.AgentToolCatalog;
import online.wanan.xingchen.core.agent.SimulationStateService;
import online.wanan.xingchen.core.agent.SocialToolExecutor;
import online.wanan.xingchen.core.conversation.OutboundExecutionService;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.adapter.onebot.DeliveryStatus;
import online.wanan.xingchen.adapter.onebot.SendReceipt;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"runtime-test","conversation-fake-e2e"})
@Import(TestRuntimeConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class StickerSlangVoiceConsoleContractTest {
    private static final Path ROOT=createRoot();
    private static final Path DB=ROOT.resolve("db/console.db"),ASSETS=ROOT.resolve("assets/stickers"),SECRETS=ROOT.resolve("secrets");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());p.add("xingchen.secrets.directory",()->SECRETS.toString());p.add("xingchen.owner.platform-user-id",()->"e2e-owner");p.add("xingchen.sticker.root",()->ASSETS.toString());}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired StickerAssetService assets;@Autowired StickerService stickerService;@Autowired SlangService slangService;@Autowired SlangRepository slangRepository;@Autowired StickerRepository stickerRepository;@Autowired AgentToolCatalog catalog;@Autowired OutboundExecutionService outbound;@Autowired SimulationStateService simulation;@Autowired java.time.Clock clock;

    private static Path createRoot(){try{Path path=Path.of("build","test-media-console-"+UUID.randomUUID()).toAbsolutePath();Files.createDirectories(path.resolve("db"));return path;}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @BeforeEach void cleanFixtures() throws Exception {jdbc.update("DELETE FROM slang_console_audit");jdbc.update("DELETE FROM slang_entries");jdbc.update("DELETE FROM stickers");deleteTree(ASSETS);Files.createDirectories(ASSETS);assertThat(org.springframework.test.util.ReflectionTestUtils.getField(assets,"root")).isEqualTo(ASSETS.toAbsolutePath().normalize());}
    @AfterAll void cleanAll(){TestSqliteDatabase.clean(jdbc,DB);deleteTree(ROOT);}

    @Test void mediaAndVoiceApisRequireAuthentication() throws Exception {
        mvc.perform(get("/api/stickers/admin").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stickers/00000000-0000-0000-0000-000000000001/content").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/slang/admin").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/voice/capabilities").with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(username="media-admin") void recursiveScanDeduplicatesPersistsMetadataAndServesPrivatePreview() throws Exception {
        byte[] png=png();Files.createDirectories(ASSETS.resolve("nested/deeper"));Files.write(ASSETS.resolve("nested/deeper/cat.png"),png);Files.write(ASSETS.resolve("duplicate.png"),png);Files.writeString(ASSETS.resolve("not-image.txt"),"skip");
        var scan=assets.scan();assertThat(scan.imported()).isEqualTo(1);assertThat(scan.duplicates()).isEqualTo(1);assertThat(scan.skipped()).isEqualTo(1);
        var listed=mvc.perform(get("/api/stickers/admin")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].path").value(org.hamcrest.Matchers.startsWith("library/"))).andExpect(jsonPath("$.items[0].mimeType").value("image/png")).andExpect(jsonPath("$.items[0].width").value(1)).andExpect(jsonPath("$.items[0].enabled").value(true)).andReturn();
        String body=listed.getResponse().getContentAsString(),id=JsonPath.read(body,"$.items[0].id"),revision=JsonPath.read(body,"$.items[0].updatedAt");
        mvc.perform(get("/api/stickers/{id}/content",id).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stickers/{id}/content",id)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("inline"))).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("private")));
        String patch="{\"tags\":[\" 开心 \",\"开心\",\"猫猫\"],\"note\":\"夸奖回应\",\"enabled\":false,\"expectedUpdatedAt\":\""+revision+"\"}";
        mvc.perform(patch("/api/stickers/admin/{id}",id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(patch)).andExpect(status().isOk()).andExpect(jsonPath("$.tags.length()").value(2)).andExpect(jsonPath("$.note").value("夸奖回应")).andExpect(jsonPath("$.enabled").value(false));
        assertThat(stickerService.search("cat",20)).isEmpty();
        mvc.perform(get("/api/stickers/admin").param("enabled","false").param("format","png").param("q","夸奖")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(patch("/api/stickers/admin/{id}",id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(patch)).andExpect(status().isConflict());
    }

    @Test @WithMockUser(username="media-admin") void uploadValidatesContentKeepsBytesOnFilesystemAndRejectsOversize() throws Exception {
        var upload=new MockMultipartFile("file","smile.png","image/png",png());
        mvc.perform(multipart("/api/stickers/admin/upload").file(upload).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.mimeType").value("image/png"));
        try(Stream<Path> files=Files.list(ASSETS.resolve("library"))){assertThat(files.count()).isEqualTo(1);}
        assertThatThrownBy(()->assets.upload("fake.png",new java.io.ByteArrayInputStream("not an image".getBytes()),java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
        byte[] large=new byte[(int)StickerAssetService.MAX_FILE_BYTES+1];assertThatThrownBy(()->assets.upload("large.png",new java.io.ByteArrayInputStream(large),java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForList("PRAGMA table_info(stickers)").toString()).doesNotContain("BLOB");
    }

    @Test @WithMockUser(username="media-admin") void pathTraversalAndSymlinkEscapeAreRejectedAndPreviewNeverReadsArbitraryFiles() throws Exception {
        Path outside=ROOT.resolve("private.png");Files.write(outside,png());
        assertThatThrownBy(()->stickerService.collectFromRoot("../private.png",java.util.List.of(),"test")).isInstanceOf(IllegalArgumentException.class);
        boolean symlinkCreated=false;try{Files.createSymbolicLink(ASSETS.resolve("escape.png"),outside);symlinkCreated=true;}catch(UnsupportedOperationException|SecurityException|java.io.IOException ignored){}
        if(symlinkCreated)assertThat(assets.scan().skipped()).isEqualTo(1);
        UUID forged=UUID.randomUUID();jdbc.update("INSERT INTO stickers(id,path,sha256,tags_json,note,usage_count,source,created_at,last_used_at,file_name,mime_type,file_size,width,height,animated,enabled,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",forged.toString(),"../../private.png","0".repeat(64),"[]","",0,"test",Instant.now().toString(),null,"private.png","image/png",Files.size(outside),1,1,0,1,Instant.now().toString());
        mvc.perform(get("/api/stickers/{id}/content",forged)).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(username="media-admin") void slangLifecycleKeepsEvidencePrivateAndOnlyConfirmedTermsEnterAgentSearch() throws Exception {
        var candidate=slangService.submit("绝绝子","非常好","token=example-secret","private evidence snippet","",java.util.List.of("group-7"),java.util.List.of("token=chat-secret"));
        var list=mvc.perform(get("/api/slang/admin").param("status","CANDIDATE")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.items[0].status").value("CANDIDATE")).andExpect(jsonPath("$.items[0].evidencePreview.length()").value(1)).andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain("chat-secret","example-secret");assertThat(slangService.search("绝绝",20)).isEmpty();
        var before=slangRepository.get(candidate.id()).orElseThrow();
        mvc.perform(patch("/api/slang/admin/{id}",candidate.id()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CONFIRM\",\"expectedUpdatedAt\":\""+before.updatedAt()+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(slangService.search("绝绝",20)).extracting("id").contains(candidate.id());
        var manual=mvc.perform(post("/api/slang/admin").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"term\":\"手动词\",\"meaning\":\"管理员添加\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANDIDATE")).andExpect(jsonPath("$.source").value("ADMIN_MANUAL")).andReturn();
        String manualId=JsonPath.read(manual.getResponse().getContentAsString(),"$.id"),manualRevision=JsonPath.read(manual.getResponse().getContentAsString(),"$.updatedAt");
        mvc.perform(patch("/api/slang/admin/{id}",manualId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedUpdatedAt\":\""+manualRevision+"\",\"meaning\":\"更新释义\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.meaning").value("更新释义"));
        var beforeReject=slangRepository.get(UUID.fromString(manualId)).orElseThrow();
        mvc.perform(patch("/api/slang/admin/{id}",manualId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\",\"expectedUpdatedAt\":\""+beforeReject.updatedAt()+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(slangService.search("手动词",20)).isEmpty();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM slang_console_audit",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT action||COALESCE(old_status,'')||COALESCE(new_status,'') FROM slang_console_audit").toString()).doesNotContain("chat-secret","example-secret");
        mvc.perform(get("/api/slang")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chat-secret"))));
    }

    @Test @WithMockUser(username="media-admin") void voiceReportsMetadataOnlyAndUnsupportedFeatures() throws Exception {
        mvc.perform(get("/api/voice/capabilities")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.status").value("PARTIAL_METADATA_ONLY")).andExpect(jsonPath("$.receiveContentTypes[0]").value("record")).andExpect(jsonPath("$.transcription").value(false)).andExpect(jsonPath("$.tts").value(false)).andExpect(jsonPath("$.voiceSend").value(false)).andExpect(jsonPath("$.sendTest").value(false)).andExpect(jsonPath("$.rateSetting").value(false)).andExpect(jsonPath("$.providerSettings").value(false));
    }

    @Test @WithMockUser(username="media-admin") void agentStickerSendUsesDurableLedgerOnceAndNeverRetriesUnknown() throws Exception {
        OneBotGateway fakeOneBot=org.mockito.Mockito.mock(OneBotGateway.class);org.mockito.Mockito.when(fakeOneBot.sendImage(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(new SendReceipt(true,"image-group","provider-success","ok",DeliveryStatus.ACCEPTED),new SendReceipt(false,"image-group","group-active","uncertain",DeliveryStatus.UNKNOWN));
        var safeRuntime=new SocialToolExecutor(fakeOneBot,catalog,null,null,null,null,null,"e2e-bot",new StickerService(ASSETS,stickerRepository,new StickerPolicy(10,java.time.Duration.ZERO),fakeOneBot,clock),null,clock,simulation,outbound);
        var actor=actor();var request=new AgentRequest(actor,null,java.util.Set.of(AgentCapability.STICKER_SEND),"event-sticker-safe","turn-sticker-safe");
        Files.write(ASSETS.resolve("safe-send.png"),png(0xff00ff00));var successSticker=stickerService.collectFromRoot("safe-send.png",java.util.List.of(),"test");
        var send=new online.wanan.xingchen.core.agent.ToolCall("sticker.send",java.util.Map.of("stickerId",successSticker.id().toString(),"target","group-active","kind","group"),"sticker-success-call");
        assertThat(safeRuntime.execute(send,request,java.util.Set.of(AgentCapability.STICKER_SEND)).data()).containsEntry("deliveryStatus","ACCEPTED");
        assertThat(safeRuntime.execute(send,request,java.util.Set.of(AgentCapability.STICKER_SEND)).data()).containsEntry("deliveryStatus","ACCEPTED");org.mockito.Mockito.verify(fakeOneBot,org.mockito.Mockito.times(1)).sendImage("group-active","group",ASSETS.resolve(successSticker.path()).toString());
        Files.write(ASSETS.resolve("unknown-send.png"),png(0xffff0000));var unknownSticker=stickerService.collectFromRoot("unknown-send.png",java.util.List.of(),"test");var unknownRequest=new AgentRequest(actor,null,java.util.Set.of(AgentCapability.STICKER_SEND),"event-sticker-unknown","turn-sticker-unknown");var uncertain=new online.wanan.xingchen.core.agent.ToolCall("sticker.send",java.util.Map.of("stickerId",unknownSticker.id().toString(),"target","group-active","kind","group"),"sticker-unknown-call");
        assertThat(safeRuntime.execute(uncertain,unknownRequest,java.util.Set.of(AgentCapability.STICKER_SEND)).data()).containsEntry("deliveryStatus","UNKNOWN");
        assertThat(safeRuntime.execute(uncertain,unknownRequest,java.util.Set.of(AgentCapability.STICKER_SEND)).data()).containsEntry("deliveryStatus","UNKNOWN");org.mockito.Mockito.verify(fakeOneBot,org.mockito.Mockito.times(1)).sendImage("group-active","group",ASSETS.resolve(unknownSticker.path()).toString());
    }

    private static online.wanan.xingchen.core.identity.ResolvedActorContext actor(){var person=new online.wanan.xingchen.core.identity.Person(UUID.fromString("20000000-0000-0000-0000-000000000002"),online.wanan.xingchen.core.model.Platform.QQ,"e2e-owner",false,true);var conversation=new online.wanan.xingchen.core.identity.Conversation(UUID.fromString(ConversationFakeE2eConfiguration.FIXTURES.get(1).id()),new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.GROUP,"group-active"));var now=Instant.parse("2026-10-01T12:00:00Z");var membership=new online.wanan.xingchen.core.identity.Membership(UUID.fromString("30000000-0000-0000-0000-000000000101"),person.id(),conversation.id(),"Console Owner","Owner card",online.wanan.xingchen.core.identity.IdentityRole.MEMBER,now,now);return new online.wanan.xingchen.core.identity.ResolvedActorContext(person,conversation,membership,new online.wanan.xingchen.core.identity.ActorFlags(false,true,false,false),"Console Owner",new online.wanan.xingchen.core.relationship.AddressResolution("Owner","test",null,false,0));}

    private static byte[] png() throws Exception {return png(0xff000000);}
    private static byte[] png(int color) throws Exception {BufferedImage image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,color);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    private static void deleteTree(Path path){if(path==null||!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return;try(Stream<Path> paths=Files.walk(path)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}}
}
