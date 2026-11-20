package online.wanan.xingchen.console;

import online.wanan.xingchen.core.conversation.ProactiveParticipationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Covered by the same authenticated /api/** management boundary; no chat or prompt text. */
@RestController
public final class ProactiveParticipationController {
    private final ProactiveParticipationService service;
    public ProactiveParticipationController(ProactiveParticipationService service){this.service=service;}
    @GetMapping("/api/social-settings/proactive-status") public ResponseEntity<Map<String,Object>> status(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.diagnostics());}
}
