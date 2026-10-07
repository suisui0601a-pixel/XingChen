package online.wanan.xingchen.core;

import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.adapter.deepseek.ModelProviderException;
import online.wanan.xingchen.core.context.ContextPackage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Keeps only non-reversible structural evidence; never stores request or prompt text. */
final class PromptShapeAuditProvider implements ModelProvider {
    record Shape(int requestNumber,boolean personaPresent,boolean simulationPresent,boolean identityPresent,
                 boolean runtimeContextPresent,boolean currentMessagePresent,
                 boolean personaDuplicate,boolean simulationDuplicate,int chars,String sha256,
                 int personaChars,String personaSha256,int simulationChars,String simulationSha256,
                 String currentMessageSha256,String conversationSha256) {}
    private final ModelProvider delegate;
    private final AtomicInteger attempts=new AtomicInteger(),successes=new AtomicInteger(),failures=new AtomicInteger();
    private final AtomicReference<String> lastFailure=new AtomicReference<>("");
    private final List<Shape> shapes=new CopyOnWriteArrayList<>();
    PromptShapeAuditProvider(ModelProvider delegate){this.delegate=Objects.requireNonNull(delegate);}
    int attempts(){return attempts.get();} int successes(){return successes.get();} int failures(){return failures.get();} String lastFailure(){return lastFailure.get();}
    List<Shape> shapes(){return List.copyOf(shapes);}
    @Override public ModelProvider snapshot(){return this;}
    @Override public ModelResponse complete(ModelRequest request){audit(request);attempts.incrementAndGet();try{var result=delegate.complete(request);successes.incrementAndGet();return result;}catch(RuntimeException e){throw safeFailure(e);}}
    @Override public ModelResponse stream(ModelRequest request,Consumer<ModelStreamEvent> onEvent){audit(request);attempts.incrementAndGet();try{var result=delegate.stream(request,onEvent);successes.incrementAndGet();return result;}catch(RuntimeException e){throw safeFailure(e);}}
    private RuntimeException safeFailure(RuntimeException e){
        // Avoid relaying any third-party exception/cause into the acceptance log or artifact.
        failures.incrementAndGet();
        String category=e.getClass().getSimpleName();
        if(e instanceof ModelProviderException m){
            String fixed=Objects.toString(m.getMessage(),"");
            category=switch(fixed){
                case "malformed DeepSeek response"->"PROVIDER_RESPONSE_MALFORMED";
                case "DeepSeek returned an unknown or unoffered tool name"->"UNMAPPED_TOOL_NAME";
                case "DeepSeek tool name mapping is invalid or colliding"->"INVALID_TOOL_MAPPING";
                case "DeepSeek request timed out"->"REQUEST_TIMEOUT";
                case "DeepSeek transport failed"->m.getCause()==null?"TRANSPORT_ERROR": "TRANSPORT_"+m.getCause().getClass().getSimpleName();
                case "DeepSeek stream interrupted"->m.getCause()==null?"STREAM_INTERRUPTED":"STREAM_"+m.getCause().getClass().getSimpleName();
                default->m.statusCode()>0?"HTTP_"+m.statusCode():"PROVIDER_PROTOCOL_ERROR";
            };
            String message="real DeepSeek call failed; "+category;lastFailure.set(message);return new ModelProviderException(message,m.statusCode(),m.retryable(),null);
        }
        String message="real DeepSeek call failed ("+category+")";lastFailure.set(message);return new IllegalStateException(message);
    }
    private void audit(ModelRequest request){if(shapes.size()>=128)return;ContextPackage c=request.context();String rendered=c.renderSections();
        boolean runtime=!c.taskState().isEmpty()||!c.recentMessages().isEmpty()||!c.memories().isEmpty()||!c.conversationSummary().isBlank();
        String wire=request.messages().stream().map(ModelMessage::content).reduce((a,b)->a+"\n"+b).orElse("");
        shapes.add(new Shape(shapes.size()+1,!c.persona().isBlank()&&wire.contains(c.persona()),!c.simulationPrompt().isBlank()&&wire.contains(c.simulationPrompt()),
                !c.identityBlock().isBlank()&&wire.contains(c.identityBlock()),runtime,wire.contains(c.currentMessage()),
                occurrences(wire,c.persona())>1,occurrences(wire,c.simulationPrompt())>1,rendered.length(),sha256(rendered),
                c.persona().length(),sha256(c.persona()),c.simulationPrompt().length(),sha256(c.simulationPrompt()),
                sha256(c.currentMessage()),sha256(c.conversationId())));
    }
    private static int occurrences(String text,String part){if(part==null||part.isBlank())return 0;int n=0,at=0;while((at=text.indexOf(part,at))>=0){n++;at+=Math.max(1,part.length());}return n;}
    private static String sha256(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("prompt audit hash unavailable");}}
}
