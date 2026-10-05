package online.wanan.xingchen.core;

import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.relationship.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityResolverContractTest {
    private static final Instant NOW=Instant.parse("2026-09-30T00:00:00Z");
    private final IdentityRegistry registry=new IdentityRegistry("bot",Set.of("owner"));
    private final RelationshipTermRegistry terms=new RelationshipTermRegistry();
    private final IdentityResolver resolver=new IdentityResolver(registry,new AddressResolver(),terms,"bot");
    private PlatformEvent event(String user,String group,String name,String card,String role){return new PlatformEvent(Platform.QQ,new ConversationIdentity(Platform.QQ,ConversationType.GROUP,group),new ActorIdentity(Platform.QQ,user,name,false,user.equals("owner")),new MessageIdentity(UUID.randomUUID().toString(),null),"message",role,"hi",NOW,Map.of("senderCard",card));}
    @Test void id101_sameNicknameDifferentUsersDoNotMerge(){var a=resolver.resolve(event("a","g","same","", "member"));var b=resolver.resolve(event("b","g","same","", "member"));assertThat(a.person().id()).isNotEqualTo(b.person().id());}
    @Test void id102_samePersonGetsDifferentMembershipsAcrossGroups(){var a=resolver.resolve(event("a","g1","nick","card1","member"));var b=resolver.resolve(event("a","g2","nick","card2","member"));assertThat(a.person().id()).isEqualTo(b.person().id());assertThat(a.membership().id()).isNotEqualTo(b.membership().id());}
    @Test void id103_renameUpdatesDisplayAndRetainsAliases(){var a=resolver.resolve(event("a","g1","old","", "member"));var b=resolver.resolve(event("a","g1","new","", "member"));assertThat(a.person().id()).isEqualTo(b.person().id());assertThat(b.displayIdentity()).isEqualTo("new");assertThat(registry.aliases(a.person().id())).extracting(PersonAlias::alias).contains("old","new");}
    @Test void id104_ownerFlagComesFromConfiguredStableId(){var a=resolver.resolve(event("owner","g1","ordinary label","","member"));assertThat(a.flags().owner()).isTrue();assertThat(a.person().role()).isEqualTo(IdentityRole.OWNER);}
    @Test void id105_selfFlagUsesBotIdNotDisplayName(){var e=new PlatformEvent(Platform.QQ,new ConversationIdentity(Platform.QQ,ConversationType.PRIVATE,"u"),new ActorIdentity(Platform.QQ,"bot","ordinary",false,false),new MessageIdentity("self-msg",null),"message","member","x",NOW,Map.of());assertThat(resolver.resolve(e).flags().self()).isTrue();}
    @Test void id106_groupAdminRoleRemainsExplicit(){assertThat(resolver.resolve(event("admin","g","A","","admin")).flags().groupAdmin()).isTrue();}
    @Test void id107_groupOwnerRoleRemainsExplicit(){assertThat(resolver.resolve(event("group-owner","g","A","","owner")).flags().groupOwner()).isTrue();}
    @Test void rel101_relationshipAddressIsAttachedToIdentityAndAppearsInActorContext(){var person=registry.identify(Platform.QQ,"a");var bot=registry.identify(Platform.QQ,"bot");terms.save(new RelationshipTerm(UUID.randomUUID(),bot.id(),person.id(),RelationshipType.ADDRESS_AS,"姐姐",ScopeType.GLOBAL,"",true,.99,"m0",NOW,NOW));var resolved=resolver.resolve(event("a","g1","伊蕾娜","", "member"));assertThat(resolved.actorId()).isEqualTo("qq:a");assertThat(resolved.displayIdentity()).isEqualTo("伊蕾娜");assertThat(resolved.address()).isEqualTo("姐姐");}
}
