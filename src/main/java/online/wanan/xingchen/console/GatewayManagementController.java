package online.wanan.xingchen.console;

import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gateway")
public final class GatewayManagementController {
    private final GatewayManagementPort gateway;
    private final ConfigService config;
    private final org.springframework.beans.factory.ObjectProvider<online.wanan.xingchen.adapter.onebot.OneBotConfigurationApplier> gatewayApplier;
    public GatewayManagementController(GatewayManagementPort gateway,ConfigService config,org.springframework.beans.factory.ObjectProvider<online.wanan.xingchen.adapter.onebot.OneBotConfigurationApplier> gatewayApplier){this.gateway=gateway;this.config=config;this.gatewayApplier=gatewayApplier;}
    @GetMapping("/status") public ResponseEntity<Map<String,Object>> status(){
        var snapshot=gateway.getStatus();var values=new LinkedHashMap<String,Object>();
        values.put("state",snapshot.state());values.put("enabled",snapshot.enabled());values.put("transportConnected",snapshot.transportConnected());
        values.put("detail",snapshot.detail());gateway.getAccountInfo().ifPresent(account->values.put("account",account));values.put("capabilities",gateway.capabilities());
        values.put("config",config.gatewayConfiguration());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.copyOf(values));
    }
    @PostMapping("/login") public ResponseEntity<GatewayManagementPort.OperationResult> beginLogin(){return unsupported(gateway.beginLogin());}
    @PostMapping("/login/refresh") public ResponseEntity<GatewayManagementPort.OperationResult> refreshChallenge(){return unsupported(gateway.refreshLoginChallenge());}
    @PostMapping("/logout") public ResponseEntity<GatewayManagementPort.OperationResult> logout(){return unsupported(gateway.logout());}
    @PostMapping("/reconnect") public ResponseEntity<GatewayManagementPort.OperationResult> reconnect(){return unsupported(gateway.reconnect());}
    @org.springframework.web.bind.annotation.PatchMapping("/config") public Map<String,Object> updateConfig(@org.springframework.web.bind.annotation.RequestBody Map<String,Object> updates,org.springframework.security.core.Authentication authentication){Map<String,Object> result=config.updateGatewaySettings(updates,authentication.getName());var applier=gatewayApplier.getIfAvailable();if(applier!=null)applier.apply();return result;}
    @PostMapping("/secret") public Map<String,Object> secret(@org.springframework.web.bind.annotation.RequestBody SecretUpdate body,org.springframework.security.core.Authentication authentication){Map<String,Object> result=config.updateGatewaySecret(body.action(),body.token(),authentication.getName());var applier=gatewayApplier.getIfAvailable();if(applier!=null)applier.apply();return result;}
    public record SecretUpdate(String action,String token){}
    private ResponseEntity<GatewayManagementPort.OperationResult> unsupported(GatewayManagementPort.OperationResult result){return ResponseEntity.status(result.supported()?HttpStatus.OK:HttpStatus.NOT_IMPLEMENTED).cacheControl(CacheControl.noStore()).body(result);}
}
