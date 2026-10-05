package online.wanan.xingchen.core.conversation;

import java.util.*;

/** Small explicit allowlist. Unknown slash commands are never forwarded to DSH. */
public final class SlashCommandRouter {
    private final Set<String> allowedModels;
    public SlashCommandRouter(Collection<String> allowedModels){this.allowedModels=Set.copyOf(allowedModels==null?Set.of():allowedModels);}
    public Result parse(String message){if(message==null||!message.startsWith("/"))return new Result(Kind.NOT_COMMAND,null,null);String[] parts=message.trim().split("\\s+",2);return switch(parts[0].toLowerCase(Locale.ROOT)){
        case "/reset"->parts.length==1?new Result(Kind.RESET,null,null):new Result(Kind.REJECTED,null,"/reset takes no arguments");
        case "/model"->{if(parts.length==1)yield new Result(Kind.MODEL_LIST,null,null);String model=parts[1].trim();yield allowedModels.contains(model)?new Result(Kind.MODEL_SELECT,model,null):new Result(Kind.REJECTED,null,"model is not in the local allowlist");}
        default->new Result(Kind.REJECTED,null,"unknown command");};}
    public enum Kind { NOT_COMMAND, RESET, MODEL_LIST, MODEL_SELECT, REJECTED }
    public record Result(Kind kind,String value,String safeMessage){}
}
