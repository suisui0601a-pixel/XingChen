package online.wanan.xingchen.core;

import online.wanan.xingchen.core.conversation.SlashCommandRouter;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandRouterTest {
    private final SlashCommandRouter router=new SlashCommandRouter(Set.of("deepseek-chat","deepseek-reasoner"));
    @Test void slash101_nonSlashIsOrdinaryChat(){assertThat(router.parse("hello").kind()).isEqualTo(SlashCommandRouter.Kind.NOT_COMMAND);}
    @Test void slash102_onlyExplicitCommandsAreAccepted(){assertThat(router.parse("/reset").kind()).isEqualTo(SlashCommandRouter.Kind.RESET);assertThat(router.parse("/model deepseek-chat").value()).isEqualTo("deepseek-chat");assertThat(router.parse("/model provider/admin").kind()).isEqualTo(SlashCommandRouter.Kind.REJECTED);assertThat(router.parse("/shell whoami").kind()).isEqualTo(SlashCommandRouter.Kind.REJECTED);}
}
