package online.wanan.xingchen.console;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/prompts")
public final class PromptConsoleController {
    private final PromptConsoleService service;
    public PromptConsoleController(PromptConsoleService service){this.service=service;}
    @GetMapping("/{layer}/current") public ResponseEntity<Map<String,Object>> current(@PathVariable String layer){return privateResponse(service.current(layer));}
    @GetMapping("/{layer}/history") public ResponseEntity<Map<String,Object>> history(@PathVariable String layer,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.history(layer,page,size));}
    @GetMapping("/{layer}/versions/{id}") public ResponseEntity<Map<String,Object>> version(@PathVariable String layer,@PathVariable UUID id){return privateResponse(service.version(layer,id));}
    @PostMapping("/{layer}/versions") public ResponseEntity<Map<String,Object>> create(@PathVariable String layer,@RequestBody PromptConsoleService.VersionInput input,Authentication auth){return privateResponse(service.create(layer,input.content(),input.note(),input.expectedActiveVersionId(),auth.getName()));}
    @PostMapping("/{layer}/rollback") public ResponseEntity<Map<String,Object>> rollback(@PathVariable String layer,@RequestBody PromptConsoleService.RollbackInput input,Authentication auth){return privateResponse(service.rollback(layer,input.targetVersionId(),input.expectedActiveVersionId(),auth.getName()));}
    @GetMapping("/{layer}/diff") public ResponseEntity<Map<String,Object>> diff(@PathVariable String layer,@RequestParam UUID left,@RequestParam UUID right){return privateResponse(service.diff(layer,left,right));}
    @GetMapping("/security") public ResponseEntity<Map<String,Object>> security(){return privateResponse(service.security());}
    @GetMapping("/composition") public ResponseEntity<Map<String,Object>> composition(){return privateResponse(service.composition());}
    @GetMapping("/baseline") public ResponseEntity<Map<String,Object>> baseline(){return privateResponse(service.baseline());}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,Object>> promptWriteRejected(ResponseStatusException failure){
        HttpStatus status=HttpStatus.valueOf(failure.getStatusCode().value());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma","no-cache")
                .body(Map.of("error","PROMPT_BASELINE_IMMUTABLE","message",failure.getReason()==null?"Prompt baseline is immutable.":failure.getReason()));
    }
    private static ResponseEntity<Map<String,Object>> privateResponse(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
}
