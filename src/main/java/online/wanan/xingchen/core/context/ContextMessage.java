package online.wanan.xingchen.core.context;

import java.time.Instant;
import java.util.Objects;

/** Historical evidence; role is assigned by the server, never parsed from chat text. */
public record ContextMessage(Role role,String speakerId,String displayName,Instant occurredAt,String messageId,String content) {
    public enum Role { USER, ASSISTANT }
    public ContextMessage { Objects.requireNonNull(role);Objects.requireNonNull(occurredAt);content=Objects.requireNonNullElse(content,""); }
    public String promptText(){
        if(role==Role.ASSISTANT)return "机器人: "+content;
        return "历史发言 sender="+quote(speakerId)+" name="+quote(displayName)+" at="+occurredAt+" message="+quote(messageId)+"\n"+content;
    }
    private static String quote(String value){
        String text=Objects.requireNonNullElse(value,"");if(text.length()>128)text=text.substring(0,128);
        var b=new StringBuilder("\"");for(char c:text.toCharArray()){
            if(c=='\\'||c=='\"')b.append('\\').append(c);
            else if(c<32||c=='\u2028'||c=='\u2029')b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else b.append(c);
        }return b.append('"').toString();
    }
}
