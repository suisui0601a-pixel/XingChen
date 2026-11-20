package online.wanan.xingchen.core.memory;

import online.wanan.xingchen.core.model.ConversationType;
import java.util.UUID;

/** Identity and audience are server-derived, never model tool arguments. */
public record RequestContext(UUID requestingPerson,UUID conversationId,boolean ownerStatus,String projectKey,ConversationType conversationType) {
    /** Compatibility for non-chat/admin callers; live Runtime and preview always bind the real audience. */
    public RequestContext(UUID requestingPerson,UUID conversationId,boolean ownerStatus,String projectKey){this(requestingPerson,conversationId,ownerStatus,projectKey,null);}
    public RequestContext {if(requestingPerson==null||conversationId==null)throw new IllegalArgumentException("request identity and conversation are required");}
    public RequestContext forAudience(ConversationType trustedType){
        if(conversationType!=null&&conversationType!=trustedType)throw new SecurityException("memory audience mismatch");
        return new RequestContext(requestingPerson,conversationId,ownerStatus,projectKey,trustedType);
    }
}
