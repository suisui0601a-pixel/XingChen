package online.wanan.xingchen.console;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/social-settings")
public final class SocialSettingsController {
    private final SocialSettingsService service;
    public SocialSettingsController(SocialSettingsService service){this.service=service;}
    @GetMapping("/global") public ResponseEntity<Map<String,Object>> global(){return noStore(service.global());}
    @PatchMapping("/global") public ResponseEntity<Map<String,Object>> updateGlobal(@RequestBody Patch body,Authentication auth){return noStore(service.updateGlobal(body.expectedRevision(),body.values(),auth.getName()));}
    @GetMapping("/conversations/{id}") public ResponseEntity<Map<String,Object>> conversation(@PathVariable UUID id){return noStore(service.conversation(id));}
    @PatchMapping("/conversations/{id}") public ResponseEntity<Map<String,Object>> updateConversation(@PathVariable UUID id,@RequestBody Patch body,Authentication auth){return noStore(service.updateConversation(id,body.expectedRevision(),body.values(),auth.getName()));}
    @DeleteMapping("/conversations/{id}/override") public ResponseEntity<Map<String,Object>> clear(@PathVariable UUID id,@RequestParam long expectedRevision,Authentication auth){return noStore(service.clearConversation(id,expectedRevision,auth.getName()));}
    private static ResponseEntity<Map<String,Object>> noStore(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
    public record Patch(long expectedRevision,Map<String,Object> values){}
}
