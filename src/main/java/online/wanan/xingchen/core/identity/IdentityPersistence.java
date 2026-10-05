package online.wanan.xingchen.core.identity;

import online.wanan.xingchen.core.model.ConversationIdentity;
import online.wanan.xingchen.core.model.Platform;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for identity snapshots used by the identity cache. */
public interface IdentityPersistence {
    Optional<Person> findPerson(Platform platform, String platformUserId);
    Optional<Person> findPerson(UUID id);
    Person save(Person person, Instant at);
    Conversation save(Conversation conversation, Instant at);
    Optional<Membership> findMembership(UUID personId, UUID conversationId);
    Membership save(Membership membership);
    void saveAlias(PersonAlias alias);
    List<PersonAlias> aliases(UUID personId);
    List<Membership> memberships(UUID personId);
}
