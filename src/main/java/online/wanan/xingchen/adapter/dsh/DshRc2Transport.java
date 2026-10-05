package online.wanan.xingchen.adapter.dsh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.wanan.xingchen.core.agent.CancellationToken;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/** Exact RC.2 unary HTTP envelope and auth boundary. JSON never escapes this adapter. */
final class DshRc2Transport {
    private final URI base;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final DshRc2AuthClient auth;
    private final Duration timeout;
    private final DshHttpDispatcher dispatcher;
    DshRc2Transport(URI base,HttpClient http,ObjectMapper mapper,DshRc2AuthClient auth,Duration timeout,DshHttpDispatcher dispatcher){this.base=base;this.http=http;this.mapper=mapper;this.auth=auth;this.timeout=timeout;this.dispatcher=dispatcher;}

    JsonNode call(String endpoint,JsonNode args,CancellationToken cancellation){
        return call(endpoint,args,cancellation,true,true);
    }
    JsonNode callEventResult(String clientId,String eventId,JsonNode outcome){
        if(clientId==null||clientId.isBlank()||eventId==null||eventId.isBlank()||outcome==null||!outcome.isObject())throw new IllegalArgumentException("DSH event result correlation and object outcome are required");
        ObjectNode args=mapper.createObjectNode().put("clientId",clientId).put("eventId",eventId);args.set("outcome",outcome.deepCopy());
        return call("$events/result",args,null,false,false);
    }
    private JsonNode call(String endpoint,JsonNode args,CancellationToken cancellation,boolean wrapRequest,boolean requireValue){
        if(endpoint==null||!endpoint.matches("[A-Za-z0-9_$.-]+(/[A-Za-z0-9_$.-]+)*"))throw new IllegalArgumentException("invalid DSH RPC endpoint");
        CancellationToken token=cancellation==null?new CancellationToken():cancellation;token.throwIfCancelled();
        String rpcId=UUID.randomUUID().toString();ObjectNode envelope=mapper.createObjectNode().put("type","client-request").put("rpcId",rpcId).put("method",endpoint);ObjectNode payload=mapper.createObjectNode();
        if(wrapRequest){ObjectNode wrapped=mapper.createObjectNode();wrapped.set("request",args==null?mapper.createObjectNode():args);payload.set("args",wrapped);}else payload.set("args",args.deepCopy());envelope.set("payload",payload);
        String body;try{body=mapper.writeValueAsString(envelope);}catch(Exception e){throw new DshRc2Exception("protocol/encode","DSH request could not be encoded");}
        for(int attempt=0;attempt<2;attempt++){
            token.throwIfCancelled();String usedCookie=auth.cookie();HttpRequest request=HttpRequest.newBuilder(base.resolve("/api/"+endpoint)).timeout(timeout).header("Content-Type","application/json").header("Cookie",usedCookie).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            try{dispatcher.beforeDispatch(endpoint,request);}catch(DshRc2Exception e){throw e;}catch(RuntimeException e){throw new DshRc2Exception("transport/pre-dispatch-failed","DSH request was not dispatched",e);}
            CompletableFuture<HttpResponse<String>> pending=dispatcher.dispatch(http,request);
            try(AutoCloseable registration=token.onCancel(()->pending.cancel(true))){
                HttpResponse<String> response=pending.get(timeout.toMillis()+250,TimeUnit.MILLISECONDS);
                if(response.statusCode()==401&&attempt==0){auth.invalidate(usedCookie);continue;}
                if(response.statusCode()==401||response.statusCode()==403)throw new DshRc2Exception("auth/rejected","DSH rejected authentication");
                if(response.statusCode()<200||response.statusCode()>=300)throw new DshRc2Exception("transport/http","DSH HTTP request failed with status "+response.statusCode());
                return parse(response.body(),rpcId,requireValue);
            }catch(CancellationException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new DshRc2Exception("transport/interrupted","DSH request interrupted");}catch(TimeoutException e){pending.cancel(true);throw new DshRc2Exception("transport/timeout","DSH request timed out");}catch(ExecutionException e){if(token.isCancelled())throw new CancellationException("DSH request cancelled");Throwable cause=e.getCause();if(cause instanceof HttpTimeoutException)throw new DshRc2Exception("transport/timeout","DSH request timed out");throw new DshRc2Exception("transport/unavailable","DSH request unavailable");}catch(DshRc2Exception e){throw e;}catch(Exception e){throw new DshRc2Exception("transport/unavailable","DSH request unavailable");}
        }
        throw new DshRc2Exception("auth/rejected","DSH rejected authentication");
    }
    private JsonNode parse(String text,String expectedId,boolean requireValue){try{JsonNode root=mapper.readTree(text);if(root==null||!root.isObject()||!"server-response".equals(root.path("type").asText())||!expectedId.equals(root.path("rpcId").asText())||!root.path("result").isObject()||!root.path("result").path("ok").isBoolean())throw malformed();JsonNode result=root.path("result");if(!result.path("ok").asBoolean()){JsonNode error=result.path("error");String code=error.path("code").asText();if(code.isBlank())throw malformed();throw new DshRc2Exception(code,"DSH remote operation failed");}if(requireValue&&!result.has("value"))throw malformed();return result.path("value");}catch(DshRc2Exception e){throw e;}catch(Exception e){throw malformed();}}
    private static DshRc2Exception malformed(){return new DshRc2Exception("protocol/malformed-response","DSH returned a malformed RPC response");}
}
