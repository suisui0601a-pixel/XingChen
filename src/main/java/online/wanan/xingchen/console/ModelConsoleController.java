package online.wanan.xingchen.console;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public final class ModelConsoleController {
    private final ModelProviderConfigService service;
    public ModelConsoleController(ModelProviderConfigService service){this.service=service;}
    @GetMapping("/providers") public ResponseEntity<Map<String,Object>> providers(){return noStore(Map.of("providers",List.of(service.get())));}
    @GetMapping("/runtime") public ResponseEntity<Map<String,Object>> runtime(){Map<String,Object> p=service.get();return noStore(Map.of("providerId",p.get("providerId"),"enabled",p.get("enabled"),"configured",p.get("configured"),"status",p.get("runtimeStatus"),"model",p.get("model"),"applyMode","HOT_APPLY"));}
    @PatchMapping("/providers/deepseek/config") public ResponseEntity<Map<String,Object>> update(@RequestBody ConfigPatch body,Authentication auth){return noStore(service.update(body.expectedRevision(),body.values(),auth.getName()));}
    @PostMapping("/providers/deepseek/credential") public ResponseEntity<Map<String,Object>> replace(@RequestBody CredentialInput body,Authentication auth){return noStore(service.replaceSecret(body.value(),auth.getName()));}
    @DeleteMapping("/providers/deepseek/credential") public ResponseEntity<Map<String,Object>> clear(Authentication auth){return noStore(service.clearSecret(auth.getName()));}
    @PostMapping("/providers/deepseek/test") public ResponseEntity<Map<String,Object>> test(){var result=service.testConnection();return noStore(Map.of("status",result.status(),"summary",result.safeSummary()));}
    private static ResponseEntity<Map<String,Object>> noStore(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
    public record ConfigPatch(long expectedRevision,Map<String,Object> values){}
    public record CredentialInput(String value){}
}
