package online.wanan.xingchen.core;

import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.relationship.*;
import online.wanan.xingchen.core.context.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IdentityRelationshipMemoryContractTest {
    private final Instant now = Instant.parse("2026-09-30T00:00:00Z");
    private ConversationIdentity group(String id) { return new ConversationIdentity(Platform.QQ, ConversationType.GROUP, id); }

    @Test void id001_sameDisplayNameDoesNotMergeDifferentPlatformIds() {
        var r = new IdentityRegistry("bot", Set.of("owner"));
        var a = r.observe(Platform.QQ,"101",group("g1"),"Alex",null,null,now);
        var b = r.observe(Platform.QQ,"202",group("g1"),"Alex",null,null,now);
        assertThat(a.personId()).isNotEqualTo(b.personId());
    }
    @Test void id002_renameKeepsIdentityByStablePlatformId() {
        var r = new IdentityRegistry("bot", Set.of());
        var first = r.observe(Platform.QQ,"101",group("g1"),"old name",null,null,now);
        var second = r.observe(Platform.QQ,"101",group("g1"),"new name",null,null,now.plusSeconds(1));
        assertThat(first.personId()).isEqualTo(second.personId());
        assertThat(r.aliases(first.personId())).extracting(PersonAlias::alias).contains("old name","new name");
    }
    @Test void id003_membershipIsPerPersonAndConversation() {
        var r = new IdentityRegistry("bot", Set.of());
        var one = r.observe(Platform.QQ,"101",group("g1"),"A",null,null,now);
        r.observe(Platform.QQ,"101",group("g2"),"A",null,null,now);
        assertThat(r.memberships(r.find(Platform.QQ,"101").orElseThrow().id())).hasSize(2);
        assertThat(one.conversationId()).isNotEqualTo(r.identifyConversation(group("g2")).id());
    }
    @Test void id004_selfFlagUsesBotPlatformIdentity() {
        assertThat(new IdentityRegistry("bot", Set.of()).identify(Platform.QQ,"bot").self()).isTrue();
        assertThat(new IdentityRegistry("bot", Set.of()).identify(Platform.QQ,"other").self()).isFalse();
    }
    @Test void id005_ownerAndMemberRolesRemainDistinct() {
        var r = new IdentityRegistry("bot", Set.of("owner"));
        assertThat(r.identify(Platform.QQ,"owner").role()).isEqualTo(IdentityRole.OWNER);
        assertThat(r.identify(Platform.QQ,"member").role()).isEqualTo(IdentityRole.MEMBER);
    }
    @Test void rel001_explicitAddressTermResolves() {
        UUID speaker=UUID.randomUUID(), target=UUID.randomUUID();
        var t = term(speaker,target,"姐姐",ScopeType.GLOBAL,"",true);
        assertThat(new AddressResolver().resolveAddress(speaker,target,"g1","display",List.of(t)).address()).isEqualTo("姐姐");
    }
    @Test void rel002_addressIsAttachedToStableIdentityAcrossRename() {
        UUID speaker=UUID.randomUUID(), target=UUID.randomUUID();
        var t=term(speaker,target,"姐姐",ScopeType.GLOBAL,"",true);
        assertThat(new AddressResolver().resolveAddress(speaker,target,"g1","new name",List.of(t)).address()).isEqualTo("姐姐");
    }
    @Test void rel003_conversationTermDoesNotLeakToAnotherGroup() {
        UUID speaker=UUID.randomUUID(), target=UUID.randomUUID();
        var t=term(speaker,target,"姐姐",ScopeType.CONVERSATION,"g1",true);
        assertThat(new AddressResolver().resolveAddress(speaker,target,"g2","display",List.of(t)).address()).isEqualTo("display");
    }
    @Test void rel004_globalTermAppliesAcrossConversations() {
        UUID speaker=UUID.randomUUID(), target=UUID.randomUUID();
        var t=term(speaker,target,"姐姐",ScopeType.GLOBAL,"",true);
        assertThat(new AddressResolver().resolveAddress(speaker,target,"g2","display",List.of(t)).address()).isEqualTo("姐姐");
    }
    @Test void rel005_localExplicitAddressWinsGlobalExplicitAndLocalInferredCandidates() {
        UUID speaker=UUID.randomUUID(),target=UUID.randomUUID();
        var local=term(speaker,target,"姐姐",ScopeType.CONVERSATION,"g1",true);
        var global=term(speaker,target,"朋友",ScopeType.GLOBAL,"",true);
        var inferred=term(speaker,target,"同学",ScopeType.CONVERSATION,"g1",false);
        assertThat(new AddressResolver().resolveAddress(speaker,target,"g1","Alex",List.of(global,inferred,local)).address()).isEqualTo("姐姐");
        assertThat(new AddressResolver().resolveAddress(speaker,target,"g2","Alex",List.of(global,inferred,local)).address()).isEqualTo("朋友");
    }
    @Test void mem001_factSubjectIsNotImplicitlyOwner() {
        UUID owner=UUID.randomUUID(), friend=UUID.randomUUID();
        var m=memory(MemoryScope.PERSON_GLOBAL,friend,null,"likes tea");
        assertThat(m.subjectPersonId()).isEqualTo(friend).isNotEqualTo(owner);
    }
    @Test void mem002_longTermMemoryRequiresMessageActorAndConversationProvenance() {
        var repo=new InMemoryMemoryRepository(); var m=memory(MemoryScope.GLOBAL,null,null,"fact");
        assertThatThrownBy(() -> repo.save(m,List.of())).isInstanceOf(IllegalArgumentException.class);
        UUID conversation=UUID.randomUUID(), actor=UUID.randomUUID();
        var source=new MemorySource(m.id(),"QQ",conversation,"msg-1",actor,now);
        assertThat(repo.save(m,List.of(source))).isEqualTo(m);
        assertThat(repo.sources(m.id())).containsExactly(source);
    }
    @Test void mem003_conversationMemoryIsInvisibleInAnotherGroup() {
        UUID c1=UUID.randomUUID(), c2=UUID.randomUUID(), person=UUID.randomUUID();
        var m=memory(MemoryScope.CONVERSATION,null,c1,"group-only"); var repo=new InMemoryMemoryRepository();
        repo.save(m,List.of(source(m)));
        assertThat(repo.searchText("group-only",new RequestContext(person,c2,false,null),10)).isEmpty();
    }
    @Test void mem004_personMemoryVisibleOnlyToSubjectOrOwner() {
        UUID subject=UUID.randomUUID(), other=UUID.randomUUID(), conversation=UUID.randomUUID();
        var m=memory(MemoryScope.PERSON_GLOBAL,subject,null,"likes tea"); var repo=new InMemoryMemoryRepository(); repo.save(m,List.of(source(m)));
        assertThat(repo.findBySubject(subject,new RequestContext(subject,conversation,false,null))).containsExactly(m);
        assertThat(repo.findBySubject(subject,new RequestContext(other,conversation,false,null))).isEmpty();
    }
    @Test void mem005_006_007_contextBuilderOnlyReceivesScopeAuthorizedMemory() {
        UUID personA=UUID.randomUUID(),personB=UUID.randomUUID(),conversationA=UUID.randomUUID(),conversationB=UUID.randomUUID();
        var repo=new InMemoryMemoryRepository();
        var privateA=memory(MemoryScope.PRIVATE,personA,null,"private-a");
        var personGlobalA=memory(MemoryScope.PERSON_GLOBAL,personA,null,"person-a");
        var conversationOnlyA=memory(MemoryScope.CONVERSATION,null,conversationA,"conversation-a");
        var projectX=projectMemory("project-x","project-x-data");
        var ownerOnly=memory(MemoryScope.OWNER_GLOBAL,null,null,"owner-only");
        for(var m:List.of(privateA,personGlobalA,conversationOnlyA,projectX,ownerOnly))repo.save(m,List.of(source(m)));
        var retriever=new MemoryRetriever(repo,new ApproximateTokenEstimator());var builder=new ContextBuilder(ContextBudget.defaults());

        var a=packageFor(builder,retriever,new RequestContext(personA,conversationA,false,"project-x"),personA,conversationA);
        assertThat(contents(a)).contains("private-a","person-a","conversation-a","project-x-data").doesNotContain("owner-only");
        var b=packageFor(builder,retriever,new RequestContext(personB,conversationB,false,"project-y"),personB,conversationB);
        assertThat(contents(b)).doesNotContain("private-a","person-a","conversation-a","project-x-data","owner-only");
        var owner=packageFor(builder,retriever,new RequestContext(personB,conversationB,true,"project-x"),personB,conversationB);
        assertThat(contents(owner)).contains("owner-only","project-x-data","private-a").doesNotContain("person-a","conversation-a");
    }
    private ContextPackage packageFor(ContextBuilder builder,MemoryRetriever retriever,RequestContext request,UUID actor,UUID conversation){
        var selected=retriever.retrieve(request,"memory context",conversation,actor,10000).memories();
        return builder.build(new ContextBuildInput("","","qq:"+actor,"Member","","conversation",selected,"",List.of(),Map.of(),"memory context","",request));
    }
    private Set<String> contents(ContextPackage context){return context.memories().stream().map(Memory::content).collect(java.util.stream.Collectors.toSet());}
    private Memory projectMemory(String key,String content){return new Memory(UUID.randomUUID(),MemoryType.PROJECT,null,null,key,content,MemoryScope.PROJECT,key,.9,.9,true,now,now,null,null,MemoryStatus.ACTIVE);}
    private RelationshipTerm term(UUID s, UUID t, String value, ScopeType scope, String scopeId, boolean explicit) {
        return new RelationshipTerm(UUID.randomUUID(),s,t,RelationshipType.ADDRESS_AS,value,scope,scopeId,explicit,.9,"m1",now,now);
    }
    private Memory memory(MemoryScope scope, UUID subject, UUID conversation, String content) {
        return new Memory(UUID.randomUUID(),MemoryType.PERSON,subject,conversation,null,content,scope,
                scope==MemoryScope.CONVERSATION?conversation.toString():null,.9,.8,true,now,now,null,null,MemoryStatus.ACTIVE);
    }
    private MemorySource source(Memory m) { return new MemorySource(m.id(),"QQ",UUID.randomUUID(),"msg",UUID.randomUUID(),now); }
}
