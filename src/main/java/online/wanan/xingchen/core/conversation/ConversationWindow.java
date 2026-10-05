package online.wanan.xingchen.core.conversation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationWindow {
    private final Map<String,List<String>> recent=new ConcurrentHashMap<>();
    public List<String> appendAndGet(String key,String message){List<String> values=recent.computeIfAbsent(key,k->Collections.synchronizedList(new ArrayList<>()));values.add(message);return List.copyOf(values);}
    public List<String> get(String key){return List.copyOf(recent.getOrDefault(key,List.of()));}
    public void clear(String key){recent.remove(key);}
}
