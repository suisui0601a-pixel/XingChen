package online.wanan.xingchen.core.conversation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationWindow {
    private static final int LIMIT=128;
    private final Map<String,List<String>> recent=new ConcurrentHashMap<>();
    public List<String> appendAndGet(String key,String message){return recent.compute(key,(k,prior)->{var next=new ArrayList<String>(prior==null?List.of():prior);next.add(message);return List.copyOf(next.subList(Math.max(0,next.size()-LIMIT),next.size()));});}
    public List<String> get(String key){return recent.getOrDefault(key,List.of());}
    public void clear(String key){recent.remove(key);}
}
