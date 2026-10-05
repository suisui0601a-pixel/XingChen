package online.wanan.xingchen.core.model;

public record ReplyReference(String messageId,String quotedUserId,String quotedDisplayName,boolean quotedBot,String quotedText) {
    public ReplyReference(String messageId,String quotedUserId,String quotedDisplayName,boolean quotedBot){this(messageId,quotedUserId,quotedDisplayName,quotedBot,"");}
    public ReplyReference {quotedText=quotedText==null?"":quotedText;}
}
