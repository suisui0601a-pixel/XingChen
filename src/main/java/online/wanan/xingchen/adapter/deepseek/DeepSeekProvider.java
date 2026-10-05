package online.wanan.xingchen.adapter.deepseek;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.agent.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** DeepSeek Chat Completions transport. Secret resolution and all provider-specific JSON stay in this adapter. */
public final class DeepSeekProvider implements ModelProvider {
    private final DeepSeekConfiguration configuration;private final ObjectMapper mapper;private final Map<String,String> environment;private final HttpClient client;private final BooleanSupplier enabled;
    public DeepSeekProvider(DeepSeekConfiguration configuration){this(configuration,new ObjectMapper(),System.getenv(),()->true);}
    public DeepSeekProvider(DeepSeekConfiguration configuration,ObjectMapper mapper,Map<String,String> environment){
        this(configuration,mapper,environment,()->true);
    }
    public DeepSeekProvider(DeepSeekConfiguration configuration,ObjectMapper mapper,Map<String,String> environment,BooleanSupplier enabled){
        this.configuration=Objects.requireNonNull(configuration);this.mapper=Objects.requireNonNull(mapper);this.environment=Map.copyOf(environment);
        this.enabled=Objects.requireNonNull(enabled);
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(configuration.connectTimeoutMillis())).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public DeepSeekConfiguration configuration(){return configuration;}
    @Override public ModelResponse complete(ModelRequest request){
        long start=System.nanoTime();HttpResponse<String> response=post(request,false,HttpResponse.BodyHandlers.ofString());
        return parseResponse(response.body(),elapsed(start));
    }
    @Override public ModelResponse stream(ModelRequest request,Consumer<ModelStreamEvent> onEvent){
        Objects.requireNonNull(onEvent);long start=System.nanoTime();int retries=0;
        while(true){ModelTurnExecutionState turn=request.turnState();if(turn.state()==ModelTurnExecutionState.State.NOT_STARTED)turn.begin();try{return streamOnce(request,onEvent,start);}catch(IOException e){boolean safe=turn.interrupted();if(safe&&retries<configuration.maxRetries()){turn.resetForSafeRetry();retries++;continue;}throw new ModelProviderException("DeepSeek stream interrupted",0,safe,e);}}
    }
    private ModelResponse streamOnce(ModelRequest request,Consumer<ModelStreamEvent> onEvent,long start)throws IOException{
        HttpResponse<InputStream> response=post(request,true,HttpResponse.BodyHandlers.ofInputStream());StringBuilder content=new StringBuilder();Map<Integer,MutableCall> calls=new TreeMap<>();ModelUsage usage=null;String finish="";boolean done=false;
        try(InputStream in=response.body();BufferedReader reader=new BufferedReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){request.cancellation().throwIfCancelled();if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.equals("[DONE]")){done=true;break;}if(data.isEmpty())continue;JsonNode root;try{root=mapper.readTree(data);}catch(IOException e){throw malformed();}JsonNode choice=root.path("choices").path(0);JsonNode delta=choice.path("delta");if(delta.hasNonNull("content")){String piece=delta.path("content").asText();content.append(piece);if(!piece.isEmpty())request.turnState().modelOutputReceived();onEvent.accept(new ModelStreamEvent(piece,false,null));}JsonNode toolDeltas=delta.path("tool_calls");if(toolDeltas.isArray())for(JsonNode d:toolDeltas){int index=d.path("index").asInt(0);MutableCall call=calls.computeIfAbsent(index,k->new MutableCall());if(d.has("id"))call.id=d.path("id").asText();JsonNode fn=d.path("function");if(fn.has("name"))call.name+=fn.path("name").asText();if(fn.has("arguments"))call.arguments.append(fn.path("arguments").asText());}if(choice.hasNonNull("finish_reason"))finish=choice.path("finish_reason").asText();if(root.has("usage"))usage=parseUsage(root.path("usage"),elapsed(start));}}
        if(!done)throw new IOException("stream ended before completion marker");request.cancellation().throwIfCancelled();List<ModelToolCall> parsed=parseCalls(calls.values());if(!parsed.isEmpty())request.turnState().toolProposed();ModelResponse result=response(content.toString(),parsed,finish,usage==null?new ModelUsage(0,0,0,0,0,"deepseek",configuration.model(),elapsed(start)):usage.withDuration(elapsed(start)));onEvent.accept(new ModelStreamEvent("",true,result.usage()));return result;
    }
    private <T> HttpResponse<T> post(ModelRequest request,boolean stream,HttpResponse.BodyHandler<T> handler){
        if(!enabled.getAsBoolean())throw new IllegalStateException("model integration is disabled");
        request.cancellation().throwIfCancelled();String key=environment.get(configuration.apiKeyEnvironmentVariable());if(key==null||key.isBlank())throw new IllegalStateException("DeepSeek API key is not configured");
        String body;try{body=mapper.writeValueAsString(payload(request,stream));}catch(Exception e){throw new IllegalStateException("could not encode model request",e);}
        HttpRequest http=HttpRequest.newBuilder(URI.create(endpoint())).timeout(Duration.ofMillis(configuration.requestTimeoutMillis())).header("Authorization","Bearer "+key).header("Content-Type","application/json").header("Accept",stream?"text/event-stream":"application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        for(int attempt=0;;attempt++){
            request.cancellation().throwIfCancelled();
            try{HttpResponse<T> r=client.send(http,handler);int status=r.statusCode();if(status>=200&&status<300)return r;boolean retry=status==429||status>=500;if(retry&&attempt<configuration.maxRetries()){pause(request,attempt);continue;}throw new ModelProviderException("DeepSeek HTTP status "+status,status,retry,null);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("model request interrupted");}
            catch(java.net.http.HttpTimeoutException e){if(attempt<configuration.maxRetries()){pause(request,attempt);continue;}throw new ModelProviderException("DeepSeek request timed out",0,true,e);}
            catch(IOException e){if(attempt<configuration.maxRetries()){pause(request,attempt);continue;}throw new ModelProviderException("DeepSeek transport failed",0,true,e);}
        }
    }
    private void pause(ModelRequest request,int attempt){request.cancellation().throwIfCancelled();try{Thread.sleep(Math.min(3_000L,(long)configuration.retryDelayMillis()*(1L<<Math.min(attempt,4))));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("retry interrupted");}}
    private Map<String,Object> payload(ModelRequest request,boolean stream){
        List<Map<String,Object>> messages=new ArrayList<>();for(ModelMessage m:request.messages()){
            Map<String,Object> item=new LinkedHashMap<>();item.put("role",m.role().name().toLowerCase(Locale.ROOT));item.put("content",m.content());if(m.name()!=null)item.put("name",m.name());if(m.toolCallId()!=null)item.put("tool_call_id",m.toolCallId());
            if(!m.toolCalls().isEmpty())item.put("tool_calls",m.toolCalls().stream().map(c->Map.of("id",c.id(),"type","function","function",Map.of("name",c.name(),"arguments",json(c.arguments())))).toList());messages.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("model",configuration.model());result.put("messages",messages);result.put("max_tokens",Math.min(configuration.maxOutputTokens(),request.maxOutputTokens()));result.put("stream",stream);
        if(!request.tools().isEmpty())result.put("tools",request.toolDefinitions().stream().map(t->Map.of("type","function","function",Map.of("name",t.name(),"description",t.description(),"parameters",t.inputSchema()))).toList());
        if(stream)result.put("stream_options",Map.of("include_usage",true));return result;
    }
    private ModelResponse parseResponse(String json,long elapsed){try{JsonNode root=mapper.readTree(json);JsonNode choice=root.path("choices").path(0);if(choice.isMissingNode())throw malformed();JsonNode message=choice.path("message");String content=message.path("content").isNull()?"":message.path("content").asText("");List<ModelToolCall> calls=new ArrayList<>();JsonNode tc=message.path("tool_calls");if(tc.isArray())for(JsonNode c:tc){JsonNode f=c.path("function");Map<String,Object> args=mapper.readValue(f.path("arguments").asText("{}"),new TypeReference<>(){});calls.add(new ModelToolCall(c.path("id").asText("call-"+calls.size()),f.path("name").asText(),args));}return response(content,calls,choice.path("finish_reason").asText(""),parseUsage(root.path("usage"),elapsed));}catch(ModelProviderException e){throw e;}catch(Exception e){throw malformed();}}
    private ModelResponse response(String content,List<ModelToolCall> calls,String finish,ModelUsage usage){ModelFinishReason reason=finishReason(finish);AgentDecision decision;if(!calls.isEmpty())decision=AgentDecision.tool(calls.getFirst().toToolCall());else if(content.strip().equalsIgnoreCase("[SILENT]")||content.strip().equalsIgnoreCase("NO_REPLY"))decision=AgentDecision.noReply();else if(content.isBlank())decision=AgentDecision.noReply();else decision=AgentDecision.text(content);return new ModelResponse(decision,usage,null,calls,reason);}
    private ModelUsage parseUsage(JsonNode u,long elapsed){if(u==null||u.isMissingNode()||u.isNull())return new ModelUsage(0,0,0,0,0,"deepseek",configuration.model(),elapsed,false);int input=u.path("prompt_tokens").asInt(0),hit=u.path("prompt_cache_hit_tokens").asInt(0),miss=u.path("prompt_cache_miss_tokens").asInt(Math.max(0,input-hit)),output=u.path("completion_tokens").asInt(0);JsonNode details=u.path("completion_tokens_details"),reasonNode=details.path("reasoning_tokens");boolean hasReasoning=reasonNode.isIntegralNumber()&&!reasonNode.isMissingNode();int reason=hasReasoning?reasonNode.asInt():0;return new ModelUsage(input,hit,miss,output,reason,"deepseek",configuration.model(),elapsed,hasReasoning);}
    private List<ModelToolCall> parseCalls(Collection<MutableCall> calls){List<ModelToolCall> result=new ArrayList<>();for(MutableCall c:calls){try{Map<String,Object> args=mapper.readValue(c.arguments.toString(),new TypeReference<>(){});result.add(new ModelToolCall(c.id==null?"call-"+result.size():c.id,c.name,args));}catch(Exception e){throw malformed();}}return List.copyOf(result);}
    private String endpoint(){String base=configuration.baseUrl().replaceAll("/+$","");return base.endsWith("/chat/completions")?base:base+"/chat/completions";}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private static long elapsed(long start){return Math.max(0,(System.nanoTime()-start)/1_000_000);}
    private static ModelFinishReason finishReason(String value){return switch(value){case "stop"->ModelFinishReason.STOP;case "tool_calls"->ModelFinishReason.TOOL_CALLS;case "length"->ModelFinishReason.LENGTH;case "content_filter"->ModelFinishReason.CONTENT_FILTER;default->ModelFinishReason.UNKNOWN;};}
    private static ModelProviderException malformed(){return new ModelProviderException("malformed DeepSeek response",0,false,null);}
    private static final class MutableCall{String id;String name="";final StringBuilder arguments=new StringBuilder();}
}
