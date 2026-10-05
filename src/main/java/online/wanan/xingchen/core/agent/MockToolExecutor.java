package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import java.util.*;

public final class MockToolExecutor implements ToolExecutor {
    private final OneBotGateway gateway; private final AgentToolCatalog catalog;
    public MockToolExecutor(OneBotGateway gateway,AgentToolCatalog catalog){this.gateway=gateway;this.catalog=catalog;}
    @Override public ToolResult execute(ToolCall call,AgentRequest request,Set<AgentCapability> capabilities){return execute(call,capabilities);}
    public ToolResult execute(ToolCall call,Set<AgentCapability> capabilities){
        if(call==null||!catalog.allows(call.name(),capabilities))return new ToolResult(false,"capability denied",Map.of());
        return switch(call.name()){
            case "qq.send"->{String text=arg(call,"text"),target=arg(call,"target"),kind=arg(call,"kind");var r=kind.equals("private")?gateway.sendPrivateMessage(target,text):gateway.sendGroupMessage(target,text);yield new ToolResult(r.accepted(),"mock send",Map.of("target",target));}
            case "qq.reply"->{var r=gateway.replyMessage(arg(call,"messageId"),arg(call,"text"));yield new ToolResult(r.accepted(),"mock reply",Map.of("messageId",r.target()));}
            case "qq.readRecent"->new ToolResult(true,"mock recent",Map.of("messages",gateway.getRecentMessages(arg(call,"conversation"),intArg(call,"limit",20))));
            case "qq.readUnread"->new ToolResult(true,"mock unread",Map.of("messages",gateway.getUnreadMessages(arg(call,"conversation"),intArg(call,"limit",20))));
            case "qq.wait"->new ToolResult(true,"mock wait complete",Map.of("messages",gateway.getUnreadMessages(arg(call,"conversation"),intArg(call,"limit",20))));
            case "qq.getMember"->gateway.getGroupMember(arg(call,"groupId"),arg(call,"userId")).<ToolResult>map(m->new ToolResult(true,"mock member",Map.of("member",m))).orElseGet(()->new ToolResult(false,"member not found",Map.of()));
            case "qq.getMembers"->new ToolResult(true,"mock members",Map.of("members",gateway.getGroupMembers(arg(call,"groupId")).stream().limit(intArg(call,"limit",100)).toList()));
            case "qq.poke"->{var r=gateway.sendPoke(arg(call,"groupId"),arg(call,"userId"));yield new ToolResult(r.accepted(),"mock poke",Map.of("target",arg(call,"userId")));}
            default->new ToolResult(true,"mock fixture accepted",Map.of("tool",call.name(),"arguments",call.arguments()));
        };
    }
    private static String arg(ToolCall c,String k){return Objects.toString(c.arguments().get(k),"");}
    private static int intArg(ToolCall c,String k,int fallback){try{return Integer.parseInt(arg(c,k));}catch(NumberFormatException e){return fallback;}}
}
