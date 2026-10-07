package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer;
import online.wanan.xingchen.core.model.ConversationType;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class OneBotEventNormalizerTest {
    private final OneBotEventNormalizer normalizer=new OneBotEventNormalizer(new ObjectMapper());
    private String message(String type,String id,String role,String name,String segments){return """
      {"post_type":"message","message_type":"%s","self_id":"bot","user_id":"%s","time":100000002,"message_id":"m-%s","group_id":"g1","sender":{"user_id":"%s","nickname":"%s","card":"card-%s","role":"%s"},"message":%s}
      """.formatted(type,id,id,id,name,id,role,segments);}
    @Test void evt001_normalizesPrivateMessage(){var e=normalizer.normalize(message("private","u1","member","nick","[{\"type\":\"text\",\"data\":{\"text\":\"hello\"}}]"),Set.of());assertThat(e.conversation().type()).isEqualTo(ConversationType.PRIVATE);assertThat(e.text()).isEqualTo("hello");}
    @Test void evt002_normalizesGroupMessageAndMembershipRole(){var e=normalizer.normalize(message("group","u1","member","nick","[]"),Set.of());assertThat(e.conversation().type()).isEqualTo(ConversationType.GROUP);assertThat(e.actorPlatformRole()).isEqualTo("member");}
    @Test void evt003_marksSelfOnlyByStablePlatformId(){var e=normalizer.normalize("{\"post_type\":\"message\",\"message_type\":\"private\",\"self_id\":\"bot\",\"user_id\":\"bot\",\"message_id\":\"m1\",\"sender\":{\"user_id\":\"bot\",\"nickname\":\"same\"},\"message\":[]}",Set.of());assertThat(e.actor().self()).isTrue();assertThat(e.actor().platformUserId()).isEqualTo("bot");}
    @Test void evt004_marksConfiguredOwnerByIdNotNickname(){var e=normalizer.normalize(message("group","owner1","member","BotName","[]"),Set.of("owner1"));assertThat(e.actor().owner()).isTrue();var other=normalizer.normalize(message("group","u2","member","owner1","[]"),Set.of("owner1"));assertThat(other.actor().owner()).isFalse();}
    @Test void evt005_preservesAdminAndOwnerRoles(){assertThat(normalizer.normalize(message("group","a","admin","A","[]"),Set.of()).actorPlatformRole()).isEqualTo("admin");assertThat(normalizer.normalize(message("group","o","owner","O","[]"),Set.of()).actorPlatformRole()).isEqualTo("owner");}
    @Test void evt006_preservesReplyQuoteAndAtMetadata(){var e=normalizer.normalize(message("group","u1","member","N","[{\"type\":\"reply\",\"data\":{\"id\":\"prior\"}},{\"type\":\"at\",\"data\":{\"qq\":\"u2\"}},{\"type\":\"text\",\"data\":{\"text\":\"hey\"}}]"),Set.of());assertThat(e.message().replyToMessageId()).isEqualTo("prior");assertThat(e.rawMetadata().get("mentions")).asList().containsExactly("u2");assertThat(e.text()).isEqualTo("hey");}
    @Test void evt007_preservesImageAndForwardMetadata(){var e=normalizer.normalize(message("group","u1","member","N","[{\"type\":\"image\",\"data\":{\"file\":\"f1\",\"url\":\"url1\"}},{\"type\":\"forward\",\"data\":{\"id\":\"fw1\"}}]"),Set.of());assertThat(e.rawMetadata().get("images")).asList().contains("f1");assertThat(e.rawMetadata().get("forwards")).asList().hasSize(1);}
    @Test void evt008_normalizesPokeNoticeWithoutTreatingTargetAsSender(){var e=normalizer.normalize("{\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"self_id\":\"bot\",\"user_id\":\"u1\",\"group_id\":\"g1\",\"target_id\":\"bot\",\"time\":100000002}",Set.of());assertThat(e.eventType()).isEqualTo("poke");assertThat(e.actor().platformUserId()).isEqualTo("u1");assertThat(e.rawMetadata().get("pokeTargetId")).isEqualTo("bot");}
    @Test void evt009_keepsGroupMemberLifecycleNotice(){var e=normalizer.normalize("{\"post_type\":\"notice\",\"notice_type\":\"group_increase\",\"sub_type\":\"approve\",\"user_id\":\"new-member\",\"operator_id\":\"admin\",\"group_id\":\"g1\",\"time\":100000002}",Set.of());assertThat(e.eventType()).isEqualTo("notice:approve");assertThat(e.actor().platformUserId()).isEqualTo("new-member");assertThat(e.conversation().platformConversationId()).isEqualTo("g1");}
    @Test void evt010_neverUsesDisplayNameAsUserId(){var e=normalizer.normalize(message("group","123","member","the-id-is-not-here","[]"),Set.of());assertThat(e.actor().platformUserId()).isEqualTo("123");assertThat(e.actor().displayName()).isEqualTo("the-id-is-not-here");}
}
