package online.wanan.xingchen.console;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
public final class PeopleMemoryConsoleController {
    private final PeopleMemoryConsoleService service;
    public PeopleMemoryConsoleController(PeopleMemoryConsoleService service){this.service=service;}
    @GetMapping("/api/people") public ResponseEntity<Map<String,Object>> people(@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.people(q,page,size));}
    @GetMapping("/api/people/{id}") public ResponseEntity<Map<String,Object>> person(@PathVariable UUID id){return privateResponse(service.person(id));}
    @GetMapping("/api/relationships") public ResponseEntity<Map<String,Object>> relationships(@RequestParam(required=false)UUID personId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.relationships(personId,page,size));}
    @GetMapping("/api/relationships/preview") public ResponseEntity<Map<String,Object>> relationshipPreview(@RequestParam String subjectPersonId,@RequestParam String targetPersonId,@RequestParam String conversationId){return privateResponse(service.relationshipPreview(subjectPersonId,targetPersonId,conversationId));}
    @PostMapping("/api/relationships") public ResponseEntity<Map<String,Object>> createRelationship(@RequestBody PeopleMemoryConsoleService.RelationshipInput input,Authentication auth){return privateResponse(service.saveRelationship(null,input,auth.getName()));}
    @PutMapping("/api/relationships/{id}") public ResponseEntity<Map<String,Object>> editRelationship(@PathVariable UUID id,@RequestBody PeopleMemoryConsoleService.RelationshipInput input,Authentication auth){return privateResponse(service.saveRelationship(id,input,auth.getName()));}
    @PostMapping("/api/relationships/{id}/forget") public ResponseEntity<Map<String,Object>> forgetRelationship(@PathVariable UUID id,@RequestBody ExpectedUpdate input,Authentication auth){service.forgetRelationship(id,input.expectedUpdatedAt(),auth.getName());return privateResponse(Map.of("result","FORGOTTEN"));}
    @GetMapping("/api/memory") public ResponseEntity<Map<String,Object>> memories(@RequestParam(required=false)String q,@RequestParam(required=false)String person,@RequestParam(required=false)String conversation,@RequestParam(required=false)String project,@RequestParam(required=false)String type,@RequestParam(required=false)String scope,@RequestParam(required=false)String status,@RequestParam(required=false)String from,@RequestParam(required=false)String to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.memoryList(q,person,conversation,project,type,scope,status,from,to,page,size));}
    @GetMapping("/api/memory/{id}") public ResponseEntity<Map<String,Object>> memory(@PathVariable UUID id){return privateResponse(service.memory(id));}
    @PostMapping("/api/memory") public ResponseEntity<Map<String,Object>> createMemory(@RequestBody PeopleMemoryConsoleService.MemoryInput input,Authentication auth){return privateResponse(service.saveMemory(null,input,auth.getName()));}
    @PutMapping("/api/memory/{id}") public ResponseEntity<Map<String,Object>> editMemory(@PathVariable UUID id,@RequestBody PeopleMemoryConsoleService.MemoryInput input,Authentication auth){return privateResponse(service.saveMemory(id,input,auth.getName()));}
    @PostMapping("/api/memory/{id}/forget") public ResponseEntity<Map<String,Object>> forgetMemory(@PathVariable UUID id,@RequestBody ExpectedUpdate input,Authentication auth){service.forgetMemory(id,input.expectedUpdatedAt(),auth.getName());return privateResponse(Map.of("result","FORGOTTEN"));}
    @GetMapping("/api/memory/retrieval-preview") public ResponseEntity<Map<String,Object>> preview(@RequestParam String personId,@RequestParam String conversationId,@RequestParam(required=false)String projectKey){return privateResponse(service.preview(personId,conversationId,projectKey));}
    private static ResponseEntity<Map<String,Object>> privateResponse(Map<String,Object> body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
    public record ExpectedUpdate(String expectedUpdatedAt){}
}
