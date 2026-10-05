package online.wanan.xingchen.core;

import online.wanan.xingchen.adapter.deepseek.*;
import online.wanan.xingchen.core.agent.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Excluded from ordinary tests. The dedicated liveTest Gradle task enforces explicit opt-in. */
class DeepSeekLiveTest {
    @Test void live201_textToolToolResultMemoryCandidateAndNoReplyContracts() {
        DeepSeekConfiguration defaults=DeepSeekConfiguration.fromEnvironment(System.getenv());
        DeepSeekConfiguration config=new DeepSeekConfiguration(defaults.baseUrl(),defaults.model(),defaults.apiKeyEnvironmentVariable(),defaults.connectTimeoutMillis(),25_000,0,0,64);
        DeepSeekProvider provider=new DeepSeekProvider(config);
        var noTools=List.<AgentTool>of();

        var text=provider.complete(request(List.of(ModelMessage.system("Reply briefly in Chinese."),ModelMessage.user("Say hello in one short sentence.")),noTools));
        assertThat(text.decision().type()).isEqualTo(DecisionType.TEXT_REPLY);
        assertThat(text.decision().messages()).isNotEmpty();

        var schema=Map.<String,Object>of("type","object","properties",Map.of("conversation",Map.of("type","string"),"limit",Map.of("type","integer")),"required",List.of("conversation","limit"));
        var read=new AgentTool("qq.readRecent",AgentCapability.QQ_READ,"Read recent messages from the current conversation",schema);
        var call=provider.complete(request(List.of(ModelMessage.system("Call the requested tool and do not answer."),ModelMessage.user("Call qq.readRecent with conversation 'local-test' and limit 1.")),List.of(read)));
        assertThat(call.toolCalls()).isNotEmpty();

        var toolResult=new ModelToolResult(call.toolCalls().getFirst().id(),"qq.readRecent",true,"No messages.",Map.of("messages",List.of()));
        var followup=provider.complete(request(List.of(ModelMessage.system("Answer using the tool result only."),ModelMessage.user("What did the tool find?"),ModelMessage.assistantTools("",call.toolCalls()),ModelMessage.tool(toolResult)),List.of(read)));
        assertThat(followup.decision().type()).isEqualTo(DecisionType.TEXT_REPLY);

        var remember=new AgentTool("memory.remember",AgentCapability.MEMORY_WRITE,"Create a scoped memory candidate",Map.of("type","object","properties",Map.of("content",Map.of("type","string"),"scope",Map.of("type","string")),"required",List.of("content","scope")));
        var candidate=provider.complete(request(List.of(ModelMessage.system("Use the memory.remember tool for this explicit preference; do not invent anything."),ModelMessage.user("Remember as a person preference scoped person_global: I like cats.")),List.of(remember)));
        assertThat(candidate.toolCalls()).anySatisfy(tc->assertThat(tc.name()).isEqualTo("memory.remember"));

        var silent=provider.complete(request(List.of(ModelMessage.system("If asked to stay silent, return exactly [SILENT]."),ModelMessage.user("Do not reply; return exactly [SILENT].")),noTools));
        assertThat(silent.decision().type()).isEqualTo(DecisionType.NO_REPLY);
    }

    private static ModelRequest request(List<ModelMessage> messages,List<AgentTool> tools){return new ModelRequest(null,tools,messages,64,new CancellationToken());}
}
