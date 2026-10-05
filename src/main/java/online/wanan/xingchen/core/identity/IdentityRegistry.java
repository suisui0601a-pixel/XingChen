package online.wanan.xingchen.core.identity;

import online.wanan.xingchen.core.model.*;
import java.time.Instant;
import java.util.*;

/** Per-process identity cache with deterministic IDs; observed identity snapshots are persisted by IncomingMessageStore. */
public final class IdentityRegistry {
    private record ExternalPersonKey(Platform platform, String userId) {}
    private record ExternalConversationKey(Platform platform, ConversationType type, String conversationId) {}
    private record MembershipKey(UUID personId, UUID conversationId) {}
    private final Map<ExternalPersonKey, Person> persons = new HashMap<>();
    private final Map<ExternalConversationKey, Conversation> conversations = new HashMap<>();
    private final Map<MembershipKey, Membership> memberships = new HashMap<>();
    private final List<PersonAlias> aliases = new ArrayList<>();
    private final String selfId;
    private final Set<String> ownerIds;
    private final IdentityPersistence persistence;

    public IdentityRegistry(String selfId, Collection<String> ownerIds) { this(selfId,ownerIds,null); }
    public IdentityRegistry(String selfId, Collection<String> ownerIds, IdentityPersistence persistence) {
        this.selfId = Objects.requireNonNullElse(selfId,""); this.ownerIds = Set.copyOf(ownerIds==null?Set.of():ownerIds); this.persistence=persistence;
    }

    public synchronized Person identify(Platform platform, String platformUserId) {
        return persons.computeIfAbsent(new ExternalPersonKey(platform, platformUserId), key -> {
            Person resolved=new Person(stableId("person", platform+":"+platformUserId), platform, platformUserId, platformUserId.equals(selfId), ownerIds.contains(platformUserId));
            return persistence==null?resolved:persistence.save(resolved,Instant.now());
        });
    }

    public synchronized Conversation identifyConversation(ConversationIdentity identity) {
        return conversations.computeIfAbsent(new ExternalConversationKey(identity.platform(), identity.type(), identity.platformConversationId()),key -> {
            Conversation resolved=new Conversation(stableId("conversation",identity.platform()+":"+identity.type()+":"+identity.platformConversationId()),identity);
            return persistence==null?resolved:persistence.save(resolved,Instant.now());
        });
    }

    public synchronized Membership observe(Platform platform, String userId, ConversationIdentity conversationIdentity,
                              String displayName, String card, IdentityRole platformRole, Instant at) {
        Person person = identify(platform, userId);
        Conversation conversation = identifyConversation(conversationIdentity);
        MembershipKey key = new MembershipKey(person.id(), conversation.id());
        Membership prior = memberships.get(key);
        if(prior==null&&persistence!=null)prior=persistence.findMembership(person.id(),conversation.id()).orElse(null);
        Instant first = prior == null ? at : prior.firstSeenAt();
        Membership updated = new Membership(prior == null ? stableId("membership",person.id()+":"+conversation.id()) : prior.id(), person.id(), conversation.id(),
                displayName, card, platformRole == null ? IdentityRole.MEMBER : platformRole, first, at);
        if(persistence!=null)updated=persistence.save(updated);
        memberships.put(key,updated);
        if (displayName != null && !displayName.isBlank()) addAlias(person.id(), displayName, conversation.id(), at);
        if (card != null && !card.isBlank()) addAlias(person.id(), card, conversation.id(), at);
        return memberships.get(key);
    }

    private void addAlias(UUID personId, String value, UUID sourceConversationId, Instant at) {
        Optional<PersonAlias> prior=aliases.stream().filter(a -> a.personId().equals(personId) && a.alias().equals(value) && Objects.equals(a.sourceConversationId(), sourceConversationId)).findFirst();
        PersonAlias updated=prior.map(a->new PersonAlias(a.id(),personId,value,sourceConversationId,a.firstSeenAt(),at))
                .orElseGet(()->new PersonAlias(stableId("alias",personId+":"+sourceConversationId+":"+value),personId,value,sourceConversationId,at,at));
        if(persistence!=null)persistence.saveAlias(updated);
        aliases.removeIf(a->a.personId().equals(personId)&&a.alias().equals(value)&&Objects.equals(a.sourceConversationId(),sourceConversationId));
        aliases.add(updated);
    }

    private static UUID stableId(String kind,String value) { return UUID.nameUUIDFromBytes(("xingchen:"+kind+":"+value).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    public synchronized Optional<Person> find(Platform platform, String userId) { Person p=persons.get(new ExternalPersonKey(platform,userId));return p==null&&persistence!=null?persistence.findPerson(platform,userId):Optional.ofNullable(p); }
    public synchronized List<PersonAlias> aliases(UUID personId) { return persistence==null?aliases.stream().filter(a -> a.personId().equals(personId)).toList():persistence.aliases(personId); }
    public synchronized List<Membership> memberships(UUID personId) { return persistence==null?memberships.values().stream().filter(m -> m.personId().equals(personId)).toList():persistence.memberships(personId); }
    public synchronized Optional<Person> findById(UUID id){return persons.values().stream().filter(p->p.id().equals(id)).findFirst().or(()->persistence==null?Optional.empty():persistence.findPerson(id));}
}
