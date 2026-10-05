package online.wanan.xingchen.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Makes one explicitly requested minimal request; response bodies and provider exception text are discarded. */
@Component
@Profile("!runtime-test & !model-fake-e2e")
public final class DeepSeekConnectionTester implements ModelConnectionTester {
    private final ObjectMapper mapper;
    public DeepSeekConnectionTester(ObjectMapper mapper){this.mapper=mapper;}
    @Override public TestResult test(String baseUrl,String model,String credential,int maxOutputTokens,int timeoutMillis){
        try{String endpoint=baseUrl.replaceAll("/+$","");if(!endpoint.endsWith("/chat/completions"))endpoint+="/chat/completions";String body=mapper.writeValueAsString(Map.of("model",model,"messages",List.of(Map.of("role","user","content","Reply with OK.")),"max_tokens",1,"stream",false));
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(5000,timeoutMillis))).followRedirects(HttpClient.Redirect.NEVER).build();HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofMillis(timeoutMillis)).header("Authorization","Bearer "+credential).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();int code=client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();if(code>=200&&code<300)return new TestResult("SUCCESS","Provider accepted a minimal test request.");if(code==401||code==403)return new TestResult("AUTH_FAILED","Provider rejected the configured credential.");if(code==404||code==405)return new TestResult("UNSUPPORTED","Provider endpoint does not support this test request.");return new TestResult("UNREACHABLE","Provider returned an unavailable response (HTTP "+code+").");
        }catch(java.net.http.HttpTimeoutException e){return new TestResult("TIMEOUT","Provider test timed out.");}catch(InterruptedException e){Thread.currentThread().interrupt();return new TestResult("TIMEOUT","Provider test was interrupted.");}catch(IllegalArgumentException e){return new TestResult("INVALID_CONFIG","Provider test configuration is invalid.");}catch(Exception e){return new TestResult("UNREACHABLE","Provider could not be reached.");}
    }
}
