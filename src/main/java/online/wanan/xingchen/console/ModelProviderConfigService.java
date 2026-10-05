package online.wanan.xingchen.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.deepseek.DeepSeekConfiguration;
import online.wanan.xingchen.security.XingChenProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Persistent provider configuration and safe credential facade shared by Console and runtime snapshots. */
@Service
public class ModelProviderConfigService {
    public static final String PROVIDER="deepseek";
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final SecretStore secrets;private final ModelConnectionTester tester;private final Environment environment;private final XingChenProperties properties;
    public ModelProviderConfigService(JdbcTemplate jdbc,ObjectMapper json,SecretStore secrets,ModelConnectionTester tester,Environment environment,XingChenProperties properties){this.jdbc=jdbc;this.json=json;this.secrets=secrets;this.tester=tester;this.environment=environment;this.properties=properties;}

    public Map<String,Object> get(){Config c=load();List<Map<String,Object>> tests=jdbc.query("SELECT status,safe_summary,tested_at FROM model_provider_test_status WHERE provider_id=?",(rs,n)->Map.of("status",rs.getString(1),"summary",rs.getString(2),"testedAt",rs.getString(3)),PROVIDER);Map<String,Object> out=new LinkedHashMap<>();out.put("providerId",PROVIDER);out.put("providerType","MODEL_PROVIDER");out.put("revision",c.revision);out.put("enabled",c.values.get("enabled"));out.put("configured",secrets.configured("deepseek-api-key"));out.put("baseUrl",c.values.get("baseUrl"));out.put("model",c.values.get("model"));out.put("maxOutputTokens",c.values.get("maxOutputTokens"));out.put("timeoutMillis",c.values.get("timeoutMillis"));out.put("supportedOptions",List.of("maxOutputTokens","timeoutMillis"));out.put("reasoningEffortSupported",false);out.put("reasoningEfforts",List.of());out.put("runtimeStatus",Boolean.TRUE.equals(c.values.get("enabled"))?(secrets.configured("deepseek-api-key")?"READY":"MISSING_CREDENTIAL"):"DISABLED");out.put("applyMode","HOT_APPLY");out.put("lastTest",tests.isEmpty()?Map.of("status","UNSUPPORTED","summary","No provider test has been run yet."):tests.getFirst());return Map.copyOf(out);}

    @Transactional public Map<String,Object> update(long expectedRevision,Map<String,Object> patch,String actor){Config old=load();if(old.revision!=expectedRevision)throw new ConcurrentModificationException("model configuration changed");Map<String,Object> changes=validate(patch);Map<String,Object> next=new LinkedHashMap<>(old.values);next.putAll(changes);String payload=encode(next),now=Instant.now().toString();if(old.revision==0){if(jdbc.queryForObject("SELECT COUNT(*) FROM model_provider_config WHERE provider_id=?",Integer.class,PROVIDER)==0)jdbc.update("INSERT INTO model_provider_config(provider_id,revision,values_json,updated_at,updated_by) VALUES(?,1,?,?,?)",PROVIDER,payload,now,actor);else if(jdbc.update("UPDATE model_provider_config SET revision=revision+1,values_json=?,updated_at=?,updated_by=? WHERE provider_id=? AND revision=0",payload,now,actor,PROVIDER)!=1)throw new ConcurrentModificationException("model configuration changed");}
        else if(jdbc.update("UPDATE model_provider_config SET revision=revision+1,values_json=?,updated_at=?,updated_by=? WHERE provider_id=? AND revision=?",payload,now,actor,PROVIDER,old.revision)!=1)throw new ConcurrentModificationException("model configuration changed");
        for(String key:changes.keySet())if(!Objects.equals(old.values.get(key),changes.get(key)))audit(actor,key,old.values.get(key),changes.get(key));return get();}

    @Transactional public Map<String,Object> replaceSecret(String value,String actor){boolean had=secrets.configured("deepseek-api-key");secrets.replace("deepseek-api-key",value);audit(actor,"credentialConfigured",had,true);return Map.of("configured",true,"applyMode","HOT_APPLY");}
    @Transactional public Map<String,Object> clearSecret(String actor){boolean had=secrets.configured("deepseek-api-key");secrets.clear("deepseek-api-key");audit(actor,"credentialConfigured",had,false);return Map.of("configured",false,"applyMode","HOT_APPLY");}

    public ModelConnectionTester.TestResult testConnection(){Config c=load();String target=Objects.toString(c.values.get("baseUrl"),""),model=Objects.toString(c.values.get("model"),"");ModelConnectionTester.TestResult result;
        try{ProviderUrlPolicy.validate(target,allowLoopbackHttp());if(model.isBlank()||!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){result=new ModelConnectionTester.TestResult("INVALID_CONFIG","Provider URL or model identifier is invalid.");return recordTest(result);}
        Optional<String> credential=secrets.read("deepseek-api-key");if(credential.isEmpty()){result=new ModelConnectionTester.TestResult("INVALID_CONFIG","No provider credential is configured.");return recordTest(result);}
        result=tester.test(target,model,credential.get(),(Integer)c.values.get("maxOutputTokens"),(Integer)c.values.get("timeoutMillis"));return recordTest(result);}
    private ModelConnectionTester.TestResult recordTest(ModelConnectionTester.TestResult result){String at=Instant.now().toString();jdbc.update("INSERT INTO model_provider_test_status(provider_id,status,safe_summary,tested_at) VALUES(?,?,?,?) ON CONFLICT(provider_id) DO UPDATE SET status=excluded.status,safe_summary=excluded.safe_summary,tested_at=excluded.tested_at",PROVIDER,result.status(),result.safeSummary(),at);audit("system","connectionTest",null,result.status());return result;}

    /** Called at turn start. Secret lives only in this ephemeral immutable snapshot/provider. */
    public RuntimeSnapshot runtimeSnapshot(){Config c=load();boolean enabled=Boolean.TRUE.equals(c.values.get("enabled"));if(!enabled)return new RuntimeSnapshot(false,null,null,"DISABLED");Optional<String> credential=secrets.read("deepseek-api-key");String base=Objects.toString(c.values.get("baseUrl"),""),model=Objects.toString(c.values.get("model"),"");try{ProviderUrlPolicy.validate(base,allowLoopbackHttp());}catch(IllegalArgumentException invalid){return new RuntimeSnapshot(false,null,null,"INVALID_CONFIG");}if(!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))return new RuntimeSnapshot(false,null,null,"INVALID_CONFIG");if(credential.isEmpty())return new RuntimeSnapshot(false,null,null,"MISSING_CREDENTIAL");int max=(Integer)c.values.get("maxOutputTokens"),timeout=(Integer)c.values.get("timeoutMillis");DeepSeekConfiguration config=new DeepSeekConfiguration(base,model,"XINGCHEN_DEEPSEEK_API_KEY",Math.min(5000,timeout),timeout,0,0,max);return new RuntimeSnapshot(true,config,credential.get(),"READY");}
    private Map<String,Object> validate(Map<String,Object> values){if(values==null||values.isEmpty()||!Set.of("enabled","baseUrl","model","maxOutputTokens","timeoutMillis").containsAll(values.keySet()))throw new IllegalArgumentException("Provider configuration contains unsupported fields");Map<String,Object> out=new LinkedHashMap<>();for(var e:values.entrySet())switch(e.getKey()){
        case "enabled"->{if(!(e.getValue() instanceof Boolean))throw new IllegalArgumentException("enabled must be boolean");out.put(e.getKey(),e.getValue());}
        case "baseUrl"->{if(!(e.getValue() instanceof String s))throw new IllegalArgumentException("baseUrl must be text");out.put(e.getKey(),ProviderUrlPolicy.validate(s,allowLoopbackHttp()));}
        case "model"->{if(!(e.getValue() instanceof String s)||!s.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))throw new IllegalArgumentException("model ID is invalid");out.put(e.getKey(),s);}
        case "maxOutputTokens"->{int n=integer(e.getValue(),e.getKey());if(n<1||n>8192)throw new IllegalArgumentException("maxOutputTokens must be 1..8192");out.put(e.getKey(),n);}
        case "timeoutMillis"->{int n=integer(e.getValue(),e.getKey());if(n<1000||n>120000)throw new IllegalArgumentException("timeoutMillis must be 1000..120000");out.put(e.getKey(),n);}
        default->throw new IllegalArgumentException("unsupported model setting");}return Map.copyOf(out);}
    private int integer(Object value,String key){if(!(value instanceof Number n)||n.doubleValue()!=n.intValue())throw new IllegalArgumentException(key+" must be an integer");return n.intValue();}
    private boolean allowLoopbackHttp(){return environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev","test","runtime-test","model-fake-e2e"));}
    private Config load(){List<Config> rows=jdbc.query("SELECT revision,values_json FROM model_provider_config WHERE provider_id=?",(rs,n)->new Config(rs.getLong(1),readValues(rs.getString(2))),PROVIDER);return rows.isEmpty()?new Config(0,defaults()):rows.getFirst();}
    private Map<String,Object> defaults(){String model=properties.model()==null?"":Objects.toString(properties.model().model(),"");if(model.isBlank())model=System.getenv().getOrDefault("XINGCHEN_DEEPSEEK_MODEL","deepseek-chat");String base=System.getenv().getOrDefault("XINGCHEN_DEEPSEEK_BASE_URL","https://api.deepseek.com");Map<String,Object> m=new LinkedHashMap<>();m.put("enabled",environment.getProperty("xingchen.integrations.model-enabled",Boolean.class,false));m.put("baseUrl",base);m.put("model",model);m.put("maxOutputTokens",1024);m.put("timeoutMillis",60_000);return Map.copyOf(m);}
    private Map<String,Object> readValues(String text){try{return json.readValue(text,new com.fasterxml.jackson.core.type.TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("stored provider configuration is invalid");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("provider configuration could not be encoded");}}
    private void audit(String actor,String key,Object before,Object after){jdbc.update("INSERT INTO model_provider_audit(occurred_at,actor,provider_id,setting_key,old_value_json,new_value_json) VALUES(?,?,?,?,?,?)",Instant.now().toString(),actor,PROVIDER,key,before==null?null:encode(before),after==null?null:encode(after));}
    public record RuntimeSnapshot(boolean enabled,DeepSeekConfiguration configuration,String credential,String status){}
    private record Config(long revision,Map<String,Object> values){}
}
