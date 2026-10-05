package online.wanan.xingchen.core;

import online.wanan.xingchen.adapter.onebot.MockOneBotGateway;
import online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer;
import online.wanan.xingchen.core.agent.ModelUsage;
import online.wanan.xingchen.core.slang.*;
import online.wanan.xingchen.core.sticker.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class StickerSlangUsageContractTest {
    private final Path temp=Path.of("build","test-stickers-"+UUID.randomUUID());

    @Test void stk301_collectsLocalImageAsMetadataAndDeduplicatesByHash() throws Exception {
        var repository=new MemoryStickers();var gateway=new MockOneBotGateway(new OneBotEventNormalizer(new com.fasterxml.jackson.databind.ObjectMapper()),"bot");var service=new StickerService(temp,repository,new StickerPolicy(1,Duration.ZERO),gateway,Clock.systemUTC());
        Files.createDirectories(temp.resolve("inbox"));Files.write(temp.resolve("inbox/cat.png"),png());
        var first=service.collect("cat.png",List.of("cat","meme"),"test");var second=service.collect("cat.png",List.of("again"),"test");
        assertThat(first.path()).startsWith("library/");assertThat(first.sha256()).hasSize(64);assertThat(second.id()).isEqualTo(first.id());assertThat(repository.list(10)).hasSize(1);
    }

    @Test void stk302_rejectsPathTraversalAndUnsupportedAssets() throws Exception {
        var service=new StickerService(temp,new MemoryStickers(),new StickerPolicy(1,Duration.ZERO),new MockOneBotGateway(new OneBotEventNormalizer(new com.fasterxml.jackson.databind.ObjectMapper()),"bot"),Clock.systemUTC());
        Files.createDirectories(temp.resolve("inbox"));Files.writeString(temp.resolve("inbox/notes.txt"),"no");Files.writeString(temp.resolve("secret.png"),"outside");
        assertThatThrownBy(()->service.collect("../secret.png",List.of(),"test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.collect("notes.txt",List.of(),"test")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void stk303_enforcesPerTurnCooldownAndRepetition() {
        var p=new StickerPolicy(2,Duration.ofSeconds(30));var id=UUID.randomUUID();var now=Instant.parse("2026-01-01T00:00:00Z");
        assertThat(p.evaluate(id,now).allowed()).isTrue();p.record(id,now);assertThat(p.evaluate(id,now.plusSeconds(1)).reason()).isEqualTo("repetition penalty");
        p.beginTurn();assertThat(p.evaluate(UUID.randomUUID(),now.plusSeconds(1)).reason()).isEqualTo("cooldown");
        p.beginTurn();p.record(UUID.randomUUID(),now.plusSeconds(60));p.record(UUID.randomUUID(),now.plusSeconds(60));assertThat(p.evaluate(UUID.randomUUID(),now.plusSeconds(60)).reason()).isEqualTo("per-turn limit");
    }

    @Test void stk304_sendRequiresExistingLocalAssetAndSuccessfulGatewayReceipt() throws Exception {
        var repository=new MemoryStickers();var gateway=new MockOneBotGateway(new OneBotEventNormalizer(new com.fasterxml.jackson.databind.ObjectMapper()),"bot");var service=new StickerService(temp,repository,new StickerPolicy(1,Duration.ZERO),gateway,Clock.systemUTC());
        Files.createDirectories(temp.resolve("inbox"));Files.write(temp.resolve("a.png"),png());var sticker=service.collectFromRoot("a.png",List.of(),"unit");
        assertThat(service.send(sticker.id(),"group","group").accepted()).isFalse();gateway.connect();assertThat(service.send(sticker.id(),"group","group").accepted()).isTrue();assertThat(repository.get(sticker.id()).orElseThrow().usageCount()).isEqualTo(1);
    }

    @Test void slg301_submissionsAreOfflineCandidatesWithBoundedProvenance() {
        var repository=new MemorySlang();var service=new SlangService(repository,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC));
        var entry=service.submit("绝绝子","非常好","称赞","这个设计绝绝子","low",List.of("chat"),List.of("msg-1"));
        assertThat(entry.status()).isEqualTo(SlangStatus.CANDIDATE);assertThat(entry.sources()).containsExactly("chat");assertThat(service.search("绝绝",10)).isEmpty();repository.updateStatus(entry.id(),SlangStatus.CONFIRMED);assertThat(service.search("绝绝",10)).hasSize(1);
    }

    @Test void slg302_rejectsInvalidCandidateAndNeverResearchesPublicSources() {
        var service=new SlangService(new MemorySlang(),Clock.systemUTC());assertThatThrownBy(()->service.submit(" ","meaning","","","",List.of("web"),List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void slg303_onlyConfirmedTermsAreExposedAndRejectedTermsAreExcluded() {
        var repo=new MemorySlang();var now=Instant.now();var rejected=new SlangEntry(UUID.randomUUID(),"term","meaning","","","",List.of(),List.of(),SlangStatus.REJECTED,now,now);repo.save(rejected);
        assertThat(new SlangService(repo,Clock.systemUTC()).search("term",10)).isEmpty();
    }

    private static byte[] png() throws Exception {var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}

    private static final class MemoryStickers implements StickerRepository {
        private final Map<UUID,StickerMetadata> values=new LinkedHashMap<>();
        public StickerMetadata save(StickerMetadata s){values.put(s.id(),s);return s;} public Optional<StickerMetadata> get(UUID id){return Optional.ofNullable(values.get(id));}
        public Optional<StickerMetadata> findByHash(String hash){return values.values().stream().filter(s->s.sha256().equals(hash)).findFirst();}
        public List<StickerMetadata> list(int limit){return values.values().stream().limit(limit).toList();}
        public List<StickerMetadata> search(String q,int limit){return values.values().stream().filter(s->s.tags().stream().anyMatch(t->t.contains(q))).limit(limit).toList();}
        public Optional<StickerMetadata> updateNote(UUID id,String note){return Optional.ofNullable(values.computeIfPresent(id,(k,s)->new StickerMetadata(s.id(),s.path(),s.sha256(),s.tags(),note,s.usageCount(),s.source(),s.createdAt(),s.lastUsedAt())));}
        public Optional<StickerMetadata> recordUse(UUID id,Instant at){return Optional.ofNullable(values.computeIfPresent(id,(k,s)->new StickerMetadata(s.id(),s.path(),s.sha256(),s.tags(),s.note(),s.usageCount()+1,s.source(),s.createdAt(),at)));}
    }
    private static final class MemorySlang implements SlangRepository {
        private final Map<UUID,SlangEntry> values=new LinkedHashMap<>();public SlangEntry save(SlangEntry e){values.put(e.id(),e);return e;}
        public List<SlangEntry> search(String q,int limit){return values.values().stream().filter(e->e.term().contains(q)||e.meaning().contains(q)).limit(limit).toList();}
        public List<SlangEntry> list(SlangStatus status,int limit){return values.values().stream().filter(e->status==null||e.status()==status).limit(limit).toList();}
        public Optional<SlangEntry> updateStatus(UUID id,SlangStatus status){return Optional.ofNullable(values.computeIfPresent(id,(k,e)->new SlangEntry(e.id(),e.term(),e.meaning(),e.usage(),e.example(),e.risk(),e.sources(),e.evidence(),status,e.createdAt(),Instant.now())));}
    }
}
