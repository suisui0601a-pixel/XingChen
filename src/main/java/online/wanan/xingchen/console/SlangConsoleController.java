package online.wanan.xingchen.console;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/slang/admin")
public class SlangConsoleController {
    private final SlangConsoleService service;
    public SlangConsoleController(SlangConsoleService service){this.service=service;}
    @GetMapping public ResponseEntity<Map<String,Object>> list(@RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return noStore(service.list(q,status,from,to,page,size));}
    @PostMapping public ResponseEntity<Map<String,Object>> create(@RequestBody ManualInput input,java.security.Principal principal){return noStore(service.manual(input.term(),input.meaning(),principal.getName()));}
    @PatchMapping("/{id}") public ResponseEntity<Map<String,Object>> update(@PathVariable UUID id,@RequestBody UpdateInput input,java.security.Principal principal){Map<String,Object> result=input.action()==null||input.action().isBlank()?service.meaning(id,input.meaning(),input.expectedUpdatedAt(),principal.getName()):service.transition(id,input.action(),input.expectedUpdatedAt(),principal.getName());return noStore(result);}
    private static ResponseEntity<Map<String,Object>> noStore(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
    public record ManualInput(String term,String meaning){}
    public record UpdateInput(String expectedUpdatedAt,String action,String meaning){}
}
