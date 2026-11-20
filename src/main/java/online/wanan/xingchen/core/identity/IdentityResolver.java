package online.wanan.xingchen.core.identity;

import online.wanan.xingchen.core.model.PlatformEvent;
import online.wanan.xingchen.core.relationship.*;
import java.time.Instant;
import java.util.*;

public final class IdentityResolver {
    private final IdentityRegistry registry;
    private final AddressResolver addressResolver;
    private final RelationshipTermRegistry terms;
    private final String botPlatformId;
    public IdentityResolver(IdentityRegistry registry,AddressResolver addressResolver,RelationshipTermRegistry terms,String botPlatformId){
        this.registry=registry;this.addressResolver=addressResolver;this.terms=terms;this.botPlatformId=botPlatformId;
    }
    public ResolvedActorContext resolve(PlatformEvent event){
        var role=role(event.actorPlatformRole());
        var c=event.conversation();
        String card=Objects.toString(event.rawMetadata().get("senderCard"),"");
        var membership=registry.observe(event.platform(),event.actor().platformUserId(),c,event.actor().displayName(),
                card,role,event.timestamp());
        var person=registry.find(event.platform(),event.actor().platformUserId()).orElseThrow();
        var conversation=registry.identifyConversation(c);
        String display=first(membership.card(),membership.currentDisplayName(),person.platformUserId());
        var flags=new ActorFlags(person.self()||event.actor().self(),person.owner()||event.actor().owner(),role==IdentityRole.GROUP_ADMIN,role==IdentityRole.GROUP_OWNER);
        var bot=registry.identify(event.platform(),botPlatformId);
        String conversationId=c.platformConversationId();
        AddressResolution address=addressResolver.resolveAddress(bot.id(),person.id(),conversationId,display,terms.all());
        return new ResolvedActorContext(person,conversation,membership,flags,display,address);
    }
    /** Timer context reads existing identities without observing a new human interaction. */
    public ResolvedActorContext resolveExisting(PlatformEvent anchor,UUID conversationId){
        var person=registry.find(anchor.platform(),anchor.actor().platformUserId()).orElseThrow();
        var membership=registry.memberships(person.id()).stream().filter(m->m.conversationId().equals(conversationId)).findFirst().orElseThrow();
        var conversation=new Conversation(conversationId,anchor.conversation());
        String display=first(membership.card(),membership.currentDisplayName(),person.platformUserId());
        if(anchor.conversation().type()==online.wanan.xingchen.core.model.ConversationType.GROUP)
            return new ResolvedActorContext(person,conversation,membership,new ActorFlags(false,false,false,false),"本群会话",null);
        var bot=registry.find(anchor.platform(),botPlatformId).orElseThrow();
        var address=addressResolver.resolveAddress(bot.id(),person.id(),anchor.conversation().platformConversationId(),display,terms.all());
        return new ResolvedActorContext(person,conversation,membership,new ActorFlags(false,false,false,false),display,address);
    }
    private static IdentityRole role(String value){return switch(value==null?"member":value.toLowerCase(Locale.ROOT)){case "owner","group_owner"->IdentityRole.GROUP_OWNER;case "admin","group_admin"->IdentityRole.GROUP_ADMIN;default->IdentityRole.MEMBER;};}
    private static String first(String... values){for(String s:values)if(s!=null&&!s.isBlank())return s;return "";}
}
