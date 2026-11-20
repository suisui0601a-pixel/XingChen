package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import online.wanan.xingchen.adapter.deepseek.*;
import online.wanan.xingchen.core.agent.*;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class DeepSeekImageInputContractTest {
    @Test void actualCompleteAndStreamingRequestsContainBoundedImageBlocksAndKeepTextRequestsPlain()throws Exception{
        var json=new ObjectMapper();var requests=new CopyOnWriteArrayList<JsonNode>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",x->{var r=json.readTree(x.getRequestBody());requests.add(r);String reply;
            if(r.path("stream").asBoolean()){x.getResponseHeaders().add("Content-Type","text/event-stream");reply="data: {\"choices\":[{\"delta\":{\"content\":\"fixture vision answer\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";}
            else reply="{\"choices\":[{\"message\":{\"content\":\"fixture vision answer\"},\"finish_reason\":\"stop\"}]}";
            byte[] b=reply.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
        try{
            var p=new DeepSeekProvider(new DeepSeekConfiguration("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-vision-model","TEST_KEY",1000,3000,0),json,Map.of("TEST_KEY","synthetic-key"));
            var image=new ModelImage("image/png",OnDemandImageRuntimeContractTest.PNG);
            var messages=List.of(ModelMessage.system(HardSecurityPolicy.TEXT),new ModelMessage(ModelRole.USER,"describe attached fixture",null,null,List.of(),List.of(image)));
            assertThat(p.complete(new ModelRequest(null,List.of(),messages)).decision().messages()).containsExactly("fixture vision answer");
            assertThat(p.stream(new ModelRequest(null,List.of(),messages),e->{}).usage().model()).isEqualTo("fixture-vision-model");
            p.complete(new ModelRequest(null,List.of(),List.of(ModelMessage.user("plain text"))));
            for(int i=0;i<2;i++){var content=requests.get(i).path("messages").get(1).path("content");assertThat(content.isArray()).isTrue();assertThat(content.get(0).path("type").asText()).isEqualTo("text");assertThat(content.get(1).path("type").asText()).isEqualTo("image_url");assertThat(content.get(1).path("image_url").path("url").asText()).isEqualTo(image.dataUrl());assertThat(content.get(1).path("image_url").path("detail").asText()).isEqualTo("low");}
            assertThat(requests.get(2).path("messages").get(0).path("content").asText()).isEqualTo("plain text");
            assertThat(messages.toString()).doesNotContain("iVBOR");
        }finally{server.stop(0);}
    }
    @Test void imagesCannotBePlacedOnAssistantMessagesOrExceedTransportBudget(){
        var image=new ModelImage("image/png",OnDemandImageRuntimeContractTest.PNG);
        assertThatThrownBy(()->new ModelMessage(ModelRole.ASSISTANT,"",null,null,List.of(),List.of(image))).isInstanceOf(IllegalArgumentException.class);
        var p=new DeepSeekProvider(new DeepSeekConfiguration("http://127.0.0.1:1","fixture","TEST_KEY",100,100,0),new ObjectMapper(),Map.of("TEST_KEY","synthetic-key"));
        var m=new ModelMessage(ModelRole.USER,"fixture",null,null,List.of(),List.of(image,image));
        assertThatThrownBy(()->p.complete(new ModelRequest(null,List.of(),List.of(m,m)))).isInstanceOf(ModelProviderException.class).hasMessage("image input limit");
    }
}
