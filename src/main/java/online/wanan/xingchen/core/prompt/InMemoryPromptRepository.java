package online.wanan.xingchen.core.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public final class InMemoryPromptRepository implements PromptRepository {
    private final Map<UUID,PromptProfile> profiles=new HashMap<>();
    private final Map<String,List<PromptVersion>> versions=new HashMap<>();
    public PromptProfile save(PromptProfile p){profiles.put(p.id(),p);return p;}
    public Optional<PromptProfile> get(UUID id){return Optional.ofNullable(profiles.get(id));}
    public PromptVersion saveVersion(UUID id,PromptLayer layer,String content,String by){if(!profiles.containsKey(id))throw new NoSuchElementException("profile not found");var h=versions.computeIfAbsent(key(id,layer),k->new ArrayList<>());var v=new PromptVersion(UUID.randomUUID(),id,layer,content,h.size()+1,Instant.now(),by,sha(content));h.add(v);return v;}
    public List<PromptVersion> history(UUID id,PromptLayer layer){return List.copyOf(versions.getOrDefault(key(id,layer),List.of()));}
    public PromptProfile rollback(UUID id,PromptLayer layer,int version){var p=get(id).orElseThrow();var v=history(id,layer).stream().filter(x->x.version()==version).findFirst().orElseThrow();var result=layer==PromptLayer.SIMULATION?new PromptProfile(p.id(),p.name(),v.content(),p.personaPrompt()):new PromptProfile(p.id(),p.name(),p.simulationPrompt(),v.content());save(result);saveVersion(id,layer,v.content(),"rollback");return result;}
    public static String diff(String before,String after){String[] a=before.split("\\R",-1),b=after.split("\\R",-1);StringBuilder out=new StringBuilder();for(String line:a)out.append("- ").append(line).append('\n');for(String line:b)out.append("+ ").append(line).append('\n');return out.toString();}
    private static String key(UUID id,PromptLayer l){return id+":"+l;}
    private static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
