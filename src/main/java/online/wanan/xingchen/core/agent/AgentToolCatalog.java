package online.wanan.xingchen.core.agent;

import java.util.*;

/** Closed list of model-callable operations. The provider receives only JSON-schema contracts from this list. */
public final class AgentToolCatalog {
    private static final List<AgentTool> TOOLS=List.of(
        tool("qq.readRecent",AgentCapability.QQ_READ,"Read recent messages in one conversation",props("conversation",str(),"limit",integer()),"conversation"),
        tool("qq.readUnread",AgentCapability.QQ_READ,"Read unread messages in one conversation",props("conversation",str(),"limit",integer()),"conversation"),
        tool("qq.markRead",AgentCapability.QQ_READ,"Advance the persisted read cursor to a message already observed in this conversation",props("conversation",str(),"cursor",str()),"conversation","cursor"),
        tool("qq.send",AgentCapability.QQ_SEND,"Send one private or group text message",props("target",str(),"text",str(),"kind",enumeration("private","group")),"target","text","kind"),
        tool("qq.sendMultiple",AgentCapability.QQ_SEND,"Send a bounded ordered batch of messages to the current conversation",props("target",str(),"kind",enumeration("private","group"),"messages",arrayOfString()),"target","kind","messages"),
        tool("qq.reply",AgentCapability.QQ_REPLY,"Reply to a source platform message",props("messageId",str(),"text",str()),"messageId","text"),
        tool("qq.wait",AgentCapability.QQ_WAIT,"Wait for a bounded number of unread messages",props("conversation",str(),"limit",integer(),"timeoutSeconds",Map.of("type","integer","minimum",1,"maximum",30)),"conversation"),
        tool("qq.setWakeConfig",AgentCapability.QQ_WAIT,"Set persisted simulation wake triggers for this conversation",props("conversation",str(),"mention",Map.of("type","boolean"),"name",Map.of("type","boolean"),"question",Map.of("type","boolean"),"poke",Map.of("type","boolean"),"speakers",arrayOfString()),"conversation"),
        tool("qq.getMember",AgentCapability.QQ_READ,"Read one group member by platform ID",props("groupId",str(),"userId",str()),"groupId","userId"),
        tool("qq.getMembers",AgentCapability.QQ_READ,"Read a bounded group member list",props("groupId",str(),"limit",integer()),"groupId"),
        tool("qq.poke",AgentCapability.QQ_POKE,"Send a poke to an explicit group member ID",props("groupId",str(),"userId",str()),"groupId","userId"),
        tool("memory.search",AgentCapability.MEMORY_READ,"Search memories visible to the current request",props("query",str(),"limit",integer()),"query"),
        tool("memory.remember",AgentCapability.MEMORY_WRITE,"Submit a memory candidate for policy review",props("type",enumeration("EPISODIC","SEMANTIC","PERSON","RELATIONSHIP","PROJECT","TASK","PREFERENCE"),"content",str(),"scope",enumeration("GLOBAL","OWNER_GLOBAL","PERSON_GLOBAL","CONVERSATION","PROJECT","PRIVATE"),"importance",number()),"type","content","scope"),
        tool("memory.update",AgentCapability.MEMORY_WRITE,"Request a validated update to an existing memory",props("memoryId",str(),"content",str()),"memoryId","content"),
        tool("memory.setAddress",AgentCapability.MEMORY_ADDRESS,"Set a relationship address using a stable person ID",props("targetPersonId",str(),"address",str(),"scope",enumeration("GLOBAL","CONVERSATION","PRIVATE"),"scopeId",str()),"targetPersonId","address","scope"),
        tool("memory.getPerson",AgentCapability.MEMORY_READ,"Read a person identity by stable platform identity",props("platform",str(),"platformUserId",str()),"platform","platformUserId"),
        tool("sticker.list",AgentCapability.STICKER_READ,"List a bounded selection of local sticker metadata",props("limit",integer()),"limit"),
        tool("sticker.search",AgentCapability.STICKER_READ,"Search local sticker metadata by tags or note",props("query",str(),"limit",integer()),"query"),
        tool("sticker.send",AgentCapability.STICKER_SEND,"Send a policy-approved local sticker by ID",props("stickerId",str(),"target",str(),"kind",enumeration("private","group")),"stickerId","target","kind"),
        tool("sticker.collect",AgentCapability.STICKER_WRITE,"Collect a sticker from an approved local inbox asset ID",props("assetId",str(),"tags",arrayOfString()),"assetId"),
        tool("sticker.note",AgentCapability.STICKER_WRITE,"Update a local sticker note by ID",props("stickerId",str(),"note",str()),"stickerId","note"),
        tool("slang.search",AgentCapability.SLANG_READ,"Search local slang entries",props("query",str(),"limit",integer()),"query"),
        tool("slang.submit",AgentCapability.SLANG_WRITE,"Submit a slang candidate for review; no public research",props("term",str(),"meaning",str(),"usage",str(),"example",str(),"risk",str()),"term","meaning"));
    public AgentToolCatalog() {}
    public List<AgentTool> available(Set<AgentCapability> caps){return allAllowed(caps);}
    public boolean allows(String name,Set<AgentCapability> caps){return TOOLS.stream().anyMatch(t->t.name().equals(name)&&caps.contains(t.capability()));}
    public static List<AgentTool> all(){return TOOLS;}
    public static List<AgentTool> allAllowed(Set<AgentCapability> caps){return TOOLS.stream().filter(t->caps.contains(t.capability())).toList();}
    private static AgentTool tool(String name,AgentCapability capability,String desc,Map<String,Object> properties,String... required){return new AgentTool(name,capability,desc,Map.of("type","object","properties",properties,"required",List.of(required),"additionalProperties",false));}
    private static Map<String,Object> props(Object... entries){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)result.put((String)entries[i],entries[i+1]);return result;}
    private static Map<String,Object> str(){return Map.of("type","string");}
    private static Map<String,Object> integer(){return Map.of("type","integer","minimum",1,"maximum",100);}
    private static Map<String,Object> number(){return Map.of("type","number","minimum",0,"maximum",1);}
    private static Map<String,Object> enumeration(String... values){return Map.of("type","string","enum",List.of(values));}
    private static Map<String,Object> arrayOfString(){return Map.of("type","array","items",str(),"maxItems",16);}
}
