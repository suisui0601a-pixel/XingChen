package online.wanan.xingchen.adapter.deepseek;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import online.wanan.xingchen.core.agent.*;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class DeepSeekToolMappingTest {
    private final ObjectMapper json=new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<JsonNode> captured=new AtomicReference<>();
    private final AtomicInteger requests=new AtomicInteger();
    @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();}
    @AfterEach void stop(){server.stop(0);}
    private DeepSeekProvider provider(){return new DeepSeekProvider(new DeepSeekConfiguration(
            "http://127.0.0.1:"+server.getAddress().getPort(),"deepseek-v4-flash","TEST_KEY",1000,3000,0,0,1024),
            json,Map.of("TEST_KEY","fixture-not-a-real-key"));}
    private ModelRequest request(){return new ModelRequest(null,AgentToolCatalog.all(),List.of(ModelMessage.user("fixture hello")));}
    private void respond(int code,String body,boolean stream) {
        server.createContext("/chat/completions",exchange->{
            requests.incrementAndGet();captured.set(json.readTree(exchange.getRequestBody()));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type",stream?"text/event-stream":"application/json");
            exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
    }
    private String responseTool(String name) throws Exception {
        return json.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("tool_calls",List.of(
                Map.of("id","call-1","type","function","function",Map.of("name",name,"arguments","{}")))),"finish_reason","tool_calls"))));
    }
    private String chunk(String name,String arguments,boolean first) throws Exception {
        Map<String,Object> call=new LinkedHashMap<>();call.put("index",0);if(first)call.put("id","call-fragment");
        call.put("function",Map.of("name",name,"arguments",arguments));
        return "data: "+json.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of("tool_calls",List.of(call))))))+"\n\n";
    }
    @Test void definitionsUseWireNamesAndNonStreamingResultIsCanonical() throws Exception {
        respond(200,responseTool("qq_send"),false);
        ModelResponse result=provider().complete(request());
        assertThat(result.toolCalls()).singleElement().satisfies(c->assertThat(c.name()).isEqualTo("qq.send"));
        List<String> names=new ArrayList<>();captured.get().path("tools").forEach(t->names.add(t.path("function").path("name").asText()));
        assertThat(names).contains("qq_readRecent","qq_send","memory_search").doesNotContain("qq.readRecent");
        assertThat(names).allMatch(n->n.matches("[A-Za-z0-9_-]{1,128}"));
    }
    @Test void streamingFragmentsDecodeOnlyAfterAssembly() throws Exception {
        respond(200,chunk("qq_","{\"text\":",true)+chunk("reply","\"ok\"}",false)
                +"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n",true);
        ModelResponse result=provider().stream(request(),e->{});
        assertThat(result.toolCalls()).singleElement().satisfies(c->{
            assertThat(c.name()).isEqualTo("qq.reply");assertThat(c.arguments()).containsEntry("text","ok");
        });
    }
    @Test void historicalAssistantCallsAndToolResultsUseWireNames() {
        respond(200,"{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",false);
        ModelRequest request=new ModelRequest(null,AgentToolCatalog.all(),List.of(
                ModelMessage.assistantTools("",List.of(new ModelToolCall("history","memory.search",Map.of("query","fixture")))),
                new ModelMessage(ModelRole.TOOL,"fixture result","history","memory.search",List.of()),
                ModelMessage.user("fixture hello")));
        provider().complete(request);
        JsonNode messages=captured.get().path("messages");
        assertThat(messages.get(0).path("tool_calls").get(0).path("function").path("name").asText()).isEqualTo("memory_search");
        assertThat(messages.get(1).path("name").asText()).isEqualTo("memory_search");
        assertThat(messages.get(1).path("tool_call_id").asText()).isEqualTo("history");
        assertThat(request.messages().get(0).toolCalls().getFirst().name()).isEqualTo("memory.search");
    }
    @Test void unknownNonStreamingToolDoesNotReachDomain() throws Exception {
        respond(200,responseTool("qq_nonexistent"),false);
        assertThatThrownBy(()->provider().complete(request())).isInstanceOf(ModelProviderException.class)
                .hasMessageContaining("unknown or unoffered").hasMessageNotContaining("qq_nonexistent");
    }
    @Test void unknownStreamingToolDoesNotReachDomain() throws Exception {
        respond(200,chunk("qq_nonexistent","{}",true)+"data: [DONE]\n\n",true);
        assertThatThrownBy(()->provider().stream(request(),e->{})).isInstanceOf(ModelProviderException.class)
                .hasMessageContaining("unknown or unoffered");
    }
    @Test void historyOnlyToolCannotBeReturnedAsNewProposal() throws Exception {
        respond(200,responseTool("memory_search"),false);
        ModelRequest request=new ModelRequest(null,List.of(),List.of(ModelMessage.assistantTools("",
                List.of(new ModelToolCall("old","memory.search",Map.of()))),ModelMessage.user("fixture")));
        assertThatThrownBy(()->provider().complete(request)).isInstanceOf(ModelProviderException.class);
    }
    @Test void collisionFailsBeforeNetwork() {
        var tools=List.of(new AgentTool("abc.def",AgentCapability.QQ_READ,"fixture",Map.of()),
                new AgentTool("abc_def",AgentCapability.QQ_READ,"fixture",Map.of()));
        assertThatThrownBy(()->provider().complete(new ModelRequest(null,tools,List.of(ModelMessage.user("fixture")))))
                .isInstanceOf(ModelProviderException.class).hasMessageContaining("colliding");
        assertThat(requests.get()).isZero();
    }
    @Test void safeHttpErrorLabelsAreAvailableButMessageIsNeverRelayed() {
        respond(400,"{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"invalid_parameter\",\"message\":\"Authorization fixture-not-a-real-key private prompt arguments\"}}",false);
        assertThatThrownBy(()->provider().complete(request())).isInstanceOf(ModelProviderException.class).satisfies(e->{
            var error=(ModelProviderException)e;assertThat(error.statusCode()).isEqualTo(400);
            assertThat(error.providerErrorType()).isEqualTo("invalid_request_error");
            assertThat(error.providerErrorCode()).isEqualTo("invalid_parameter");
            assertThat(error.getMessage()).doesNotContain("Authorization","fixture-not-a-real-key","private prompt","arguments");
            assertThat(error.getCause()).isNull();
        });
    }
    @Test void streamedHttpErrorDropsUnrecognizedDiagnosticValues() {
        respond(400,"{\"error\":{\"type\":\"private_user_identifier\",\"code\":\"fixture-not-a-real-key\",\"message\":\"prompt\"}}",true);
        assertThatThrownBy(()->provider().stream(request(),e->{})).isInstanceOf(ModelProviderException.class).satisfies(e->{
            var error=(ModelProviderException)e;assertThat(error.providerErrorType()).isNull();assertThat(error.providerErrorCode()).isNull();
            assertThat(error.getMessage()).isEqualTo("DeepSeek HTTP status 400");assertThat(error.getCause()).isNull();
        });
    }
    @Test void strictIsolatedEndpointAcceptsEntireWireCatalog() {
        server.createContext("/chat/completions",exchange->{
            requests.incrementAndGet();JsonNode body=json.readTree(exchange.getRequestBody());captured.set(body);
            Set<String> unique=new HashSet<>();boolean valid=true;
            for(JsonNode tool:body.path("tools")) {String name=tool.path("function").path("name").asText();valid&=name.matches("[A-Za-z0-9_-]{1,128}")&&unique.add(name);}
            String response=valid?"{\"choices\":[{\"message\":{\"content\":\"fixture accepted\"},\"finish_reason\":\"stop\"}]}":"{\"error\":{\"type\":\"invalid_request_error\"}}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(valid?200:400,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        assertThat(provider().complete(request()).decision().messages()).containsExactly("fixture accepted");
    }
}
