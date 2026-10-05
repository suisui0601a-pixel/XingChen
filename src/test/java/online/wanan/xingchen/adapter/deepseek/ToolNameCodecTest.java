package online.wanan.xingchen.adapter.deepseek;

import online.wanan.xingchen.core.agent.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ToolNameCodecTest {
    @Test void entireCanonicalCatalogIsLegalUniqueAndRoundTrips() {
        List<String> names=AgentToolCatalog.all().stream().map(AgentTool::name).toList();
        ToolNameCodec codec=new ToolNameCodec(names);
        Set<String> wires=new HashSet<>();
        for(String name:names) {
            String wire=codec.toWire(name);
            assertThat(wire).matches("[A-Za-z0-9_-]{1,128}");
            assertThat(wires.add(wire)).isTrue();
            assertThat(codec.toInternal(wire)).isEqualTo(name);
        }
        assertThat(codec.toWire("qq.readRecent")).isEqualTo("qq_readRecent");
    }
    @Test void collisionFailsFastInBothOrdersWithoutEchoingNames() {
        for(List<String> names:List.of(List.of("abc.def","abc_def"),List.of("abc_def","abc.def")))
            assertThatThrownBy(()->new ToolNameCodec(names)).isInstanceOf(ModelProviderException.class)
                    .hasMessageContaining("colliding").hasMessageNotContaining("abc");
    }
    @Test void invalidOrOversizedNamesFailFast() {
        for(String name:List.of("", "qq/send", "a".repeat(129),"秘密"))
            assertThatThrownBy(()->new ToolNameCodec(List.of(name))).isInstanceOf(ModelProviderException.class);
        assertThat(new ToolNameCodec(List.of("a".repeat(128))).toWire("a".repeat(128))).hasSize(128);
    }
    @Test void underscoreIsNotGuessedToBeADotAndUnknownWireIsRejected() {
        ToolNameCodec codec=new ToolNameCodec(List.of("abc_def","memory.search"));
        assertThat(codec.toInternal("abc_def")).isEqualTo("abc_def");
        assertThat(codec.toInternal("memory_search")).isEqualTo("memory.search");
        assertThatThrownBy(()->codec.toInternal("qq_nonexistent")).isInstanceOf(ModelProviderException.class)
                .hasMessageNotContaining("qq_nonexistent");
        assertThatThrownBy(()->codec.toWire("memory_search")).isInstanceOf(ModelProviderException.class);
    }
    @Test void snapshotsAreIndependentAndDoNotFollowMutableInput() {
        List<String> input=new ArrayList<>(List.of("qq.send"));ToolNameCodec first=new ToolNameCodec(input);
        input.clear();input.add("memory.search");ToolNameCodec second=new ToolNameCodec(input);
        assertThat(first.toInternal("qq_send")).isEqualTo("qq.send");
        assertThatThrownBy(()->second.toInternal("qq_send")).isInstanceOf(ModelProviderException.class);
        assertThatThrownBy(()->first.toInternal("memory_search")).isInstanceOf(ModelProviderException.class);
    }
    @Test void historyCanBeEncodedButCannotExpandOfferedPermissions() {
        ModelRequest request=new ModelRequest(null,List.of(),List.of(
                ModelMessage.assistantTools("",List.of(new ModelToolCall("id","memory.search",Map.of()))),
                new ModelMessage(ModelRole.TOOL,"result","id","memory.search",List.of())));
        ToolNameCodec codec=ToolNameCodec.forRequest(request);
        assertThat(codec.toWire("memory.search")).isEqualTo("memory_search");
        assertThatThrownBy(()->codec.toInternal("memory_search")).isInstanceOf(ModelProviderException.class);
    }
}
