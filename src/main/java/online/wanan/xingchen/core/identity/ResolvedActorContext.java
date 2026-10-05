package online.wanan.xingchen.core.identity;

import online.wanan.xingchen.core.relationship.AddressResolution;

public record ResolvedActorContext(Person person,Conversation conversation,Membership membership,
                                   ActorFlags flags,String displayIdentity,AddressResolution assistantAddress) {
    public String actorId(){return person.platform().name().toLowerCase()+":"+person.platformUserId();}
    public String address(){return assistantAddress==null?displayIdentity:assistantAddress.address();}
}
