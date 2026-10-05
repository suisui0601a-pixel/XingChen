package online.wanan.xingchen.console;

import online.wanan.xingchen.config.ConversationFakeE2eConfiguration;
import online.wanan.xingchen.core.agent.SimulationStateService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/** Test-only fixture controls. Never registered in production profiles. */
@RestController
@Profile("conversation-fake-e2e")
@RequestMapping("/api/fake/conversations")
public final class ConversationFakeE2eController {
    private final JdbcTemplate jdbc;private final SimulationStateService simulation;
    public ConversationFakeE2eController(JdbcTemplate jdbc,SimulationStateService simulation){this.jdbc=jdbc;this.simulation=simulation;}
    @GetMapping("/internal-failure") public Map<String,Object> internalFailure() {
        throw new RuntimeException("synthetic-private-body /synthetic/absolute/path SELECT secret FROM fixture");
    }
    @PostMapping("/{slug}/waiting") public Map<String,Object> prepareWaiting(@PathVariable String slug){var fixture=fixture(slug);var s=simulation.waitForNextMessage(java.util.UUID.fromString(fixture.id()),"fake-origin-turn","next-message");return Map.of("generation",s.generation(),"state",s.wakeState().name());}
    @GetMapping("/{slug}/preservation") public Map<String,Integer> preservation(@PathVariable String slug){var fixture=fixture(slug);String id=fixture.id();return Map.of("identity",jdbc.queryForObject("SELECT COUNT(*) FROM persons p JOIN memberships m ON m.person_id=p.id WHERE m.conversation_id=?",Integer.class,id),"memberships",jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE conversation_id=?",Integer.class,id),"relationships",jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE scope_type='PRIVATE' AND scope_id=(SELECT platform_conversation_id FROM conversations WHERE id=?) AND value='姐姐'",Integer.class,id),"memories",jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE status='ACTIVE' AND (conversation_id=? OR (scope_type='PERSON_GLOBAL' AND scope_id='20000000-0000-0000-0000-000000000002'))",Integer.class,id));}
    private ConversationFakeE2eConfiguration.Fixture fixture(String slug){return ConversationFakeE2eConfiguration.FIXTURES.stream().filter(f->f.slug().equals(slug)).findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"fixture not found"));}
}
