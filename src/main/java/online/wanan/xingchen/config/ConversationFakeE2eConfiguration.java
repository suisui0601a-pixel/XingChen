package online.wanan.xingchen.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

/** Deterministic console fixtures. This bean is absent unless the explicit local E2E profile is active. */
@Configuration(proxyBeanMethods=false)
@Profile("conversation-fake-e2e")
public class ConversationFakeE2eConfiguration {
    public static final String PRIVATE_OWNER="10000000-0000-0000-0000-000000000003";
    public static final List<Fixture> FIXTURES=List.of(
            new Fixture("sleeping-group","10000000-0000-0000-0000-000000000001","GROUP","group-sleeping","Sleeping group","SLEEPING",3),
            new Fixture("active-group","10000000-0000-0000-0000-000000000002","GROUP","group-active","Active group","AWAKE",4),
            new Fixture("private-owner",PRIVATE_OWNER,"PRIVATE","e2e-owner","Owner private","SLEEPING",7),
            new Fixture("private-member","10000000-0000-0000-0000-000000000007","PRIVATE","e2e-member","Private member","SLEEPING",6),
            new Fixture("waiting-group","10000000-0000-0000-0000-000000000004","GROUP","group-waiting","Waiting group","WAITING",9),
            new Fixture("unknown-group","10000000-0000-0000-0000-000000000005","GROUP","group-unknown","Unknown outbound group","AWAKE",2),
            new Fixture("interrupted-group","10000000-0000-0000-0000-000000000006","GROUP","group-interrupted","Interrupted group","IDLE",5));

    @Bean ApplicationRunner seedConversationFixtures(JdbcTemplate jdbc){return args->{
        String ownerId=System.getenv().getOrDefault("XINGCHEN_OWNER_ID","e2e-owner");String bot="e2e-bot",member="e2e-member",at=Instant.parse("2026-10-01T12:00:00Z").toString();
        insertPerson(jdbc,"20000000-0000-0000-0000-000000000001",bot,false,false,at);insertPerson(jdbc,"20000000-0000-0000-0000-000000000002",ownerId,false,true,at);insertPerson(jdbc,"20000000-0000-0000-0000-000000000003",member,false,false,at);
        for(Fixture f:FIXTURES){jdbc.update("INSERT OR IGNORE INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,'QQ',?,?,?)",f.id(),f.type(),f.platformId(),at);boolean configuredOwner=f.slug().equals("private-owner");String person=f.type().equals("PRIVATE")?(configuredOwner?"20000000-0000-0000-0000-000000000002":"20000000-0000-0000-0000-000000000003"):"20000000-0000-0000-0000-000000000003";String display=configuredOwner?"Console Owner":"Fixture member";jdbc.update("INSERT OR IGNORE INTO memberships(id,person_id,conversation_id,current_display_name,card,platform_role,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,'MEMBER',?,?)","30000000-0000-0000-0000-"+String.format("%012d",FIXTURES.indexOf(f)+1),person,f.id(),display,"",at,at);if(f.type().equals("PRIVATE"))jdbc.update("INSERT OR IGNORE INTO memberships(id,person_id,conversation_id,current_display_name,card,platform_role,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,'MEMBER',?,?)","30000000-0000-0000-0000-"+String.format("%012d",100+FIXTURES.indexOf(f)),"20000000-0000-0000-0000-000000000001",f.id(),"Fixture bot","",at,at);
            String wait=f.state().equals("WAITING")?"WAITING":f.state().equals("SLEEPING")?"SLEEPING":"AWAKE";jdbc.update("INSERT OR IGNORE INTO simulation_conversation_state(conversation_id,mode,wake_state,generation,updated_at,origin_turn_id,waiting_since,wait_reason) VALUES(?,'SOCIAL',?,?,?, ?,?,?)",f.id(),wait,f.generation(),at,f.state().equals("WAITING")?"fixture-wait-turn":null,f.state().equals("WAITING")?at:null,f.state().equals("WAITING")?"next-message":null);
            String msgId="900000000000"+(FIXTURES.indexOf(f)+1);jdbc.update("INSERT OR IGNORE INTO messages(id,platform,conversation_id,actor_person_id,platform_message_id,reply_to_message_id,text,is_self,is_owner,created_at,raw_metadata_json,message_type) VALUES(?,?,?,?,?,NULL,?,0,0,?,?,'message')","fixture-message-"+f.slug(),"QQ",f.id(),person,msgId,"fixture message for "+f.slug(),at,"{\"segments\":[{\"type\":\"text\",\"data\":{\"text\":\"fixture\"}},{\"type\":\"image\",\"data\":{\"url\":\"https://private.invalid/not-returned\"}}]}");
        }
        createTurn(jdbc,FIXTURES.get(1),"RUNNING","STREAMING_NO_EFFECT",at);createTurn(jdbc,FIXTURES.get(6),"INTERRUPTED","TOOL_EXECUTED",at);
        jdbc.update("INSERT OR IGNORE INTO onebot_outbound_executions(execution_key,conversation_id,turn_id,generation,logical_message_id,status,created_at,updated_at) VALUES('fixture-unknown',?,'fixture-unknown-turn',2,'fixture-logical-message','UNKNOWN',?,?)",FIXTURES.get(5).id(),at,at);
        jdbc.update("INSERT OR IGNORE INTO relationship_terms(id,subject_person_id,target_person_id,type,value,scope_type,scope_id,explicit,confidence,source_message_id,created_at,updated_at) VALUES('60000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003','ADDRESS_AS','姐姐','PRIVATE','e2e-owner',1,1.0,'90000000-0000-0000-0000-000000000001',?,?)",at,at);
        jdbc.update("INSERT OR IGNORE INTO person_aliases(id,person_id,alias,source_conversation_id,first_seen_at,last_seen_at) VALUES('40000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','Owner alias',?, ?,?)",PRIVATE_OWNER,at,at);
        jdbc.update("INSERT OR IGNORE INTO memberships(id,person_id,conversation_id,current_display_name,card,platform_role,first_seen_at,last_seen_at) VALUES('30000000-0000-0000-0000-000000000101','20000000-0000-0000-0000-000000000002',?,'Console Owner','Owner card','MEMBER',?,?)",FIXTURES.get(1).id(),at,at);
        insertMemory(jdbc,"70000000-0000-0000-0000-000000000001","SEMANTIC","20000000-0000-0000-0000-000000000002",null,null,"long-term fixture memory","PERSON_GLOBAL","20000000-0000-0000-0000-000000000002",at);
        insertMemory(jdbc,"70000000-0000-0000-0000-000000000002","PREFERENCE","20000000-0000-0000-0000-000000000003",null,null,"private fixture memory","PRIVATE","20000000-0000-0000-0000-000000000003",at);
        insertMemory(jdbc,"70000000-0000-0000-0000-000000000003","EPISODIC","20000000-0000-0000-0000-000000000003",FIXTURES.get(1).id(),null,"conversation fixture memory","CONVERSATION",FIXTURES.get(1).id(),at);
        insertMemory(jdbc,"70000000-0000-0000-0000-000000000004","PROJECT","20000000-0000-0000-0000-000000000003",null,"fixture-project","project fixture memory","PROJECT","fixture-project",at);
        insertMemory(jdbc,"70000000-0000-0000-0000-000000000005","SEMANTIC","20000000-0000-0000-0000-000000000002",null,null,"forgotten fixture memory","PERSON_GLOBAL","20000000-0000-0000-0000-000000000002",at);
        insertMemory(jdbc,"70000000-0000-0000-0000-000000000006","PREFERENCE","20000000-0000-0000-0000-000000000002",null,null,"owner global fixture memory","OWNER_GLOBAL","20000000-0000-0000-0000-000000000002",at);
        jdbc.update("UPDATE memories SET status='SOFT_DELETED' WHERE id='70000000-0000-0000-0000-000000000005'");
        jdbc.update("INSERT OR IGNORE INTO context_diagnostics(conversation_id,total_tokens,persona_tokens,simulation_tokens,memory_tokens,recent_tokens,summary_tokens,included_memory_count,excluded_memory_count,lifecycle_state,tool_result_tokens,updated_at) VALUES(?,123,20,30,10,40,23,1,0,'NORMAL',0,?)",PRIVATE_OWNER,at);
        for(int i=0;i<4;i++){String id="e2e-usage-"+i,conversation=FIXTURES.get(i%FIXTURES.size()).id(),stamp=Instant.now().minus(java.time.Duration.ofHours(1+i)).toString();jdbc.update("INSERT OR IGNORE INTO token_usage(id,provider,conversation_id,turn_id,model,usage_time,input_tokens,cache_hit_tokens,cache_miss_tokens,output_tokens,reasoning_tokens,cost,duration_ms,reasoning_available) VALUES(?,'deepseek',?,?,? ,?,100,20,80,40,5,0,200,?)",id,conversation,"fixture-turn-"+i,"fixture-model-"+(i%3),stamp,i==0?0:1);}
        jdbc.update("INSERT OR IGNORE INTO console_pricing(provider,model,input,cache_hit,cache_miss,output,reasoning,currency,revision,updated_at,updated_by) VALUES('deepseek','fixture-model-0',1.0,0.2,1.0,2.0,2.0,'USD',1,?,'fake-profile')",at);
        jdbc.update("INSERT OR IGNORE INTO console_pricing(provider,model,input,cache_hit,cache_miss,output,reasoning,currency,revision,updated_at,updated_by) VALUES('deepseek','fixture-model-1',1.2,0.2,1.0,2.2,2.2,'CNY',1,?,'fake-profile')",at);
        jdbc.update("INSERT OR IGNORE INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES('PRIVATE','QQ','e2e-member','ALLOW',?,'fake-profile')",at);
        jdbc.update("INSERT OR IGNORE INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES('GROUP','QQ','group-allowed','ALLOW',?,'fake-profile')",at);
        jdbc.update("INSERT OR IGNORE INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES('GROUP','QQ','group-denied','DENY',?,'fake-profile')",at);
        jdbc.update("INSERT OR IGNORE INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,'fake-admin','Pricing','rates','UPDATE_NUMERIC')",at);
    };}
    private static void insertPerson(JdbcTemplate jdbc,String id,String qq,boolean self,boolean owner,String at){jdbc.update("INSERT OR IGNORE INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,'QQ',?,?,?,?,?)",id,qq,self?1:0,owner?1:0,at,at);}
    private static void insertMemory(JdbcTemplate jdbc,String id,String type,String person,String conversation,String project,String content,String scope,String scopeId,String at){jdbc.update("INSERT OR IGNORE INTO memories(id,type,subject_person_id,conversation_id,project_key,content,scope_type,scope_id,confidence,importance,explicit,created_at,updated_at,status) VALUES(?,?,?,?,?,?,?,?,1.0,0.8,1,?,?,'ACTIVE')",id,type,person,conversation,project,content,scope,scopeId,at,at);}
    private static void createTurn(JdbcTemplate jdbc,Fixture f,String status,String state,String at){String key="fixture-inbound-"+f.slug(),turn="fixture-turn-"+f.slug();jdbc.update("INSERT OR IGNORE INTO inbound_turn_executions(execution_key,platform,conversation_id,incoming_event_id,turn_id,generation,status,event_json,created_at,updated_at) VALUES(?,'QQ',?,?,?,?,'COMPLETED','{}',?,?)",key,f.id(),"fixture-event-"+f.slug(),turn,f.generation(),at,at);jdbc.update("INSERT OR IGNORE INTO durable_turn_executions(turn_id,conversation_id,inbound_execution_key,incoming_event_id,generation,model_turn_state,status,claimed_at,updated_at) VALUES(?,?,?,?,?,?,?, ?,?)",turn,f.id(),key,"fixture-event-"+f.slug(),f.generation(),state,status,at,at);}
    public record Fixture(String slug,String id,String type,String platformId,String name,String state,long generation){}
}
