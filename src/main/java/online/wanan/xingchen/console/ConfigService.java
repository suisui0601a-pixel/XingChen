package online.wanan.xingchen.console;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@Service
public class ConfigService {
    private static final List<String> CATEGORIES = List.of("Console", "Runtime", "Social", "Model", "Prompt", "Access", "Gateway");
    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final GatewaySecretStore gatewaySecrets;
    private final SecretStore secrets;
    private final org.springframework.core.env.Environment environment;

    public ConfigService(JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper json,GatewaySecretStore gatewaySecrets,SecretStore secrets,org.springframework.core.env.Environment environment) { this.jdbc = jdbc; this.json = json; this.gatewaySecrets=gatewaySecrets;this.secrets=secrets;this.environment=environment; }
    @jakarta.annotation.PostConstruct public void bootstrapCredentials() {
        secrets.bootstrapEnvironment("deepseek-api-key", "XINGCHEN_DEEPSEEK_API_KEY");
        secrets.bootstrapEnvironment("deepseek-api-key", "DEEPSEEK_API_KEY");
        secrets.bootstrapEnvironment("dsh-launch-token", "DSH_LAUNCH_TOKEN");
    }
    public String dshLaunchToken() { return secrets.read("dsh-launch-token").orElse(""); }

    public List<String> categories() { return CATEGORIES; }

    public Map<String,Object> gatewayConfiguration() {
        var values=new LinkedHashMap<String,Object>();
        jdbc.query("SELECT config_key,value_json FROM console_configuration WHERE category='Gateway' ORDER BY config_key",rs->{try{values.put(rs.getString(1),json.readValue(rs.getString(2),Object.class));}catch(Exception e){throw new IllegalStateException("Stored Gateway configuration is invalid");}});
        values.putIfAbsent("httpUrl",environment.getProperty("xingchen.onebot.http-url","http://127.0.0.1:3000"));
        values.putIfAbsent("wsUrl",environment.getProperty("xingchen.onebot.ws-url","ws://127.0.0.1:3001"));
        values.put("httpTokenConfigured",gatewaySecrets.configured(GatewaySecretStore.Transport.HTTP));
        values.put("wsTokenConfigured",gatewaySecrets.configured(GatewaySecretStore.Transport.WS));
        values.put("httpTokenExplicit",gatewaySecrets.transportInitialized(GatewaySecretStore.Transport.HTTP));
        values.put("wsTokenExplicit",gatewaySecrets.transportInitialized(GatewaySecretStore.Transport.WS));
        values.put("legacyTokenConfigured",gatewaySecrets.legacyConfigured());
        values.putIfAbsent("enabled",environment.getProperty("xingchen.integrations.onebot-enabled",Boolean.class,false));
        return Map.copyOf(values);
    }
    public boolean gatewayEnabled(boolean fallback){Object value=storedGatewayValue("enabled");return value instanceof Boolean b?b:fallback;}
    public String gatewayEndpoint(String key,String fallback){Object value=storedGatewayValue(key);return value instanceof String s?s:fallback;}
    public String gatewayToken(GatewaySecretStore.Transport transport){return gatewaySecrets.read(transport).orElse("");}
    @Transactional public Map<String,Object> updateGatewaySecret(String transportName,String action,String token,String actor){GatewaySecretStore.Transport transport=parseTransport(transportName);if(action==null||!Set.of("set","replace","clear").contains(action))throw new IllegalArgumentException("Unsupported secret action");if(action.equals("clear"))gatewaySecrets.clear(transport);else gatewaySecrets.replace(transport,token);String now=Instant.now().toString();String key=transport==GatewaySecretStore.Transport.HTTP?"httpAccessToken":"wsAccessToken";jdbc.update("INSERT INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,?,?,?,?)",now,actor,"Gateway",key,action.equals("clear")?"CLEAR":"REPLACE");return Map.of("configured",!action.equals("clear"),"transport",transport.name().toLowerCase(Locale.ROOT),"applyMode",ApplyMode.HOT_APPLY.name());}
    private GatewaySecretStore.Transport parseTransport(String value){if("http".equals(value))return GatewaySecretStore.Transport.HTTP;if("ws".equals(value))return GatewaySecretStore.Transport.WS;throw new IllegalArgumentException("Transport must be http or ws");}
    private Object storedGatewayValue(String key){return jdbc.query("SELECT value_json FROM console_configuration WHERE category='Gateway' AND config_key=?",rs->{if(!rs.next())return null;try{return json.readValue(rs.getString(1),Object.class);}catch(Exception e){throw new IllegalStateException("Stored Gateway configuration is invalid");}},key);}

    public Map<String, Object> describe(String category) {
        requireCategory(category);
        var values = new LinkedHashMap<String, Object>();
        jdbc.query("SELECT config_key,value_json FROM console_configuration WHERE category=? ORDER BY config_key", rs -> {
            try { values.put(rs.getString(1), json.readValue(rs.getString(2), Object.class)); }
            catch (Exception exception) { throw new IllegalStateException("Stored Console configuration is invalid"); }
        }, category);
        if (category.equals("Console")) values.putIfAbsent("publicBaseUrl", environment.getProperty("xingchen.console.public-base-url", ""));
        return Map.of("category", category, "values", values, "availableKeys", category.equals("Console") ? List.of("publicBaseUrl") : category.equals("Gateway") ? List.of("enabled","httpUrl","wsUrl","managementUrl") : List.of());
    }

    @Transactional public Map<String, Object> update(String category, String key, Object value, String actor) {
        requireCategory(category);
        if (category.equals("Gateway")) return updateGateway(key,value,actor);
        if (!category.equals("Console") || !key.equals("publicBaseUrl")) throw new IllegalArgumentException("This setting is not editable");
        if (!(value instanceof String url) || url.length() > 512) throw new IllegalArgumentException("Public base URL is invalid");
        validatePublicUrl(url);
        String serialized;
        try { serialized = json.writeValueAsString(url); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize Console configuration"); }
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO console_configuration(category,config_key,value_json,updated_at,updated_by) VALUES(?,?,?,?,?) ON CONFLICT(category,config_key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at,updated_by=excluded.updated_by",
                category, key, serialized, now, actor);
        jdbc.update("INSERT INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,?,?,?,?)",
                now, actor, category, key, "UPDATE");
        return Map.of("category", category, "key", key, "value", url, "applyMode", ApplyMode.RESTART_REQUIRED.name());
    }

    @Transactional private Map<String,Object> updateGateway(String key,Object value,String actor) {
        Object safeValue=value;
        switch(key) {
            case "httpUrl" -> safeValue=validateEndpoint(value,"http","https");
            case "wsUrl" -> safeValue=validateEndpoint(value,"ws","wss");
            case "managementUrl" -> safeValue=validateManagementUrl(value);
            case "enabled" -> { if(!(value instanceof Boolean)) throw new IllegalArgumentException("Gateway enabled must be boolean"); }
            case "allowRemote" -> { if(!(value instanceof Boolean b)||b) throw new IllegalArgumentException("Remote gateway opt-in is not enabled in this release"); }
            default -> throw new IllegalArgumentException("Unsupported Gateway setting");
        }
        String serialized;try{serialized=json.writeValueAsString(safeValue);}catch(Exception e){throw new IllegalStateException("Unable to serialize Gateway configuration");}
        String now=Instant.now().toString();jdbc.update("INSERT INTO console_configuration(category,config_key,value_json,updated_at,updated_by) VALUES('Gateway',?,?,?,?) ON CONFLICT(category,config_key) DO UPDATE SET value_json=excluded.value_json,updated_at=excluded.updated_at,updated_by=excluded.updated_by",key,serialized,now,actor);
        jdbc.update("INSERT INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,?,?,?,?)",now,actor,"Gateway",key,"UPDATE");
        return Map.of("category","Gateway","key",key,"value",safeValue,"applyMode",ApplyMode.HOT_APPLY.name());
    }
    @Transactional public Map<String,Object> updateGatewaySettings(Map<String,Object> updates,String actor){if(updates==null||updates.isEmpty()||!Set.of("httpUrl","wsUrl","managementUrl","enabled").containsAll(updates.keySet()))throw new IllegalArgumentException("Gateway configuration is invalid");for(var entry:updates.entrySet())updateGateway(entry.getKey(),entry.getValue(),actor);return Map.of("applyMode",ApplyMode.HOT_APPLY.name());}

    private static String validateEndpoint(Object value,String... schemes){
        if(!(value instanceof String text)||text.length()>512)throw new IllegalArgumentException("Gateway endpoint is invalid");
        try{URI uri=URI.create(text);if(!Set.of(schemes).contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();
            String host=uri.getHost();if(!Set.of("127.0.0.1","::1").contains(host))throw new IllegalArgumentException("Only numeric loopback Gateway endpoints are accepted in this release");
            for(java.net.InetAddress address:java.net.InetAddress.getAllByName(host))if(!address.isLoopbackAddress())throw new IllegalArgumentException("Remote Gateway endpoints require an explicit opt-in unavailable in this release");
            if(uri.getPort()==0||uri.getPort()>65535)throw new IllegalArgumentException();return uri.toString();
        }catch(Exception e){if(e instanceof IllegalArgumentException ia&&ia.getMessage()!=null&&ia.getMessage().startsWith("Remote"))throw ia;throw new IllegalArgumentException("Gateway endpoint must use an allowed scheme and resolve to loopback");}
    }
    private static String validateManagementUrl(Object value){if(!(value instanceof String s)||s.length()>512)throw new IllegalArgumentException("Management URL is invalid");if(s.isBlank())return "";try{URI u=URI.create(s);if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException();return u.toString();}catch(Exception e){throw new IllegalArgumentException("Management URL must be an http(s) origin");}}

    public Map<String, Boolean> secretStatus() {
        return Map.of("deepSeekApiKeyConfigured", secrets.configured("deepseek-api-key"),
                "oneBotHttpTokenConfigured", gatewaySecrets.configured(GatewaySecretStore.Transport.HTTP),
                "oneBotWsTokenConfigured", gatewaySecrets.configured(GatewaySecretStore.Transport.WS), "dshCredentialConfigured", secrets.configured("dsh-launch-token"));
    }

    private static void requireCategory(String category) { if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("Unknown configuration category"); }
    private static void validatePublicUrl(String url) {
        if (url.isBlank()) return;
        try {
            URI uri = URI.create(url);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Public base URL must be an http(s) origin without credentials, query, or fragment");
        } catch (RuntimeException exception) { throw new IllegalArgumentException("Public base URL must be a valid http(s) origin"); }
    }
}
