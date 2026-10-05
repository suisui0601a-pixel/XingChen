package online.wanan.xingchen.console;

import online.wanan.xingchen.core.conversation.ConversationControlPort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public final class ConversationConsoleController {
    private final ConversationConsoleService service;
    public ConversationConsoleController(ConversationConsoleService service){this.service=service;}
    @GetMapping public ResponseEntity<Map<String,Object>> list(@RequestParam(required=false) String q,@RequestParam(required=false) String type,@RequestParam(required=false) String mode,@RequestParam(required=false) String state,@RequestParam(required=false) Boolean hasUnknown,@RequestParam(required=false) Boolean interrupted,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return privateResponse(service.list(q,type,mode,state,hasUnknown,interrupted,page,size));}
    @GetMapping("/{id}") public ResponseEntity<Map<String,Object>> detail(@PathVariable UUID id){return privateResponse(service.detail(id));}
    @GetMapping("/{id}/members") public ResponseEntity<Map<String,Object>> members(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return privateResponse(service.members(id,page,size));}
    @GetMapping("/{id}/messages") public ResponseEntity<Map<String,Object>> messages(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return privateResponse(service.messages(id,page,size));}
    @PatchMapping("/{id}/mode") public ResponseEntity<Map<String,Object>> mode(@PathVariable UUID id,@RequestBody ModeRequest body,Authentication auth){var result=service.mode(id,body.mode(),body.expectedGeneration(),auth.getName());return privateResponse(Map.of("mode",result.mode(),"wakeState",result.wakeState(),"generation",result.generation(),"updatedAt",result.updatedAt(),"result","APPLIED"));}
    @PostMapping("/{id}/wake") public ResponseEntity<Map<String,Object>> wake(@PathVariable UUID id,@RequestBody GenerationRequest body,Authentication auth){var result=service.wake(id,body.expectedGeneration(),auth.getName());return privateResponse(Map.of("mode",result.mode(),"wakeState",result.wakeState(),"generation",result.generation(),"updatedAt",result.updatedAt(),"result","APPLIED"));}
    @PostMapping("/{id}/reset") public ResponseEntity<Map<String,Object>> reset(@PathVariable UUID id,@RequestBody GenerationRequest body,Authentication auth){var result=service.reset(id,body.expectedGeneration(),auth.getName());return privateResponse(Map.of("mode",result.mode(),"wakeState",result.wakeState(),"generation",result.generation(),"updatedAt",result.updatedAt(),"result","RESET_COMPLETE"));}
    private static ResponseEntity<Map<String,Object>> privateResponse(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
    public record GenerationRequest(long expectedGeneration){}
    public record ModeRequest(String mode,long expectedGeneration){}
}
