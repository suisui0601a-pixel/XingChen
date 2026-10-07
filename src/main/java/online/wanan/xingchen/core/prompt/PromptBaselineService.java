package online.wanan.xingchen.core.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Loads and verifies immutable Persona and Simulation resources exactly once at Core startup. */
@Component
public final class PromptBaselineService {
    public static final String PERSONA_SHA256 = "01a694ed6be58e2c8c92a7db5a288f615c30aa222f2f7d10cace3af3ba262cad";
    public static final String SIMULATION_SHA256 = "8a111de40876087249bf0bb9e45f82e31cf39a46391f807b99cf8810920deaa7";
    private static final Logger log = LoggerFactory.getLogger(PromptBaselineService.class);
    private final PromptBaseline baseline;

    public PromptBaselineService() {
        String persona = read("prompts/persona.md");
        String personaHash = sha256(persona);
        if (!PERSONA_SHA256.equals(personaHash)) throw new IllegalStateException("Built-in Persona integrity check failed.");

        String simulation = read("prompts/simulation.md");
        String simulationHash = sha256(simulation);
        // The historical active Simulation has no final LF; apply_patch stores text resources with one.
        // Remove only that serialization LF, and only when it restores the pinned source checksum.
        if (!SIMULATION_SHA256.equals(simulationHash) && simulation.endsWith("\n")) {
            String withoutTerminalLf = simulation.substring(0, simulation.length() - 1);
            if (SIMULATION_SHA256.equals(sha256(withoutTerminalLf))) {
                simulation = withoutTerminalLf;
                simulationHash = SIMULATION_SHA256;
            }
        }
        if (!SIMULATION_SHA256.equals(simulationHash)) throw new IllegalStateException("Built-in Simulation integrity check failed.");

        baseline = new PromptBaseline(persona, simulation, personaHash, simulationHash);
        log.info("Built-in Persona loaded; chars: {}; sha256: {}...", persona.length(), shortHash(personaHash));
        log.info("Built-in Simulation loaded; chars: {}; sha256: {}...", simulation.length(), shortHash(simulationHash));
    }

    public PromptBaseline baseline() { return baseline; }
    public String loadPersona() { return baseline.persona(); }
    public String loadSimulation() { return baseline.simulation(); }

    private static String read(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            // Source archives may cross Windows/Unix filesystems with mixed CRLF/LF endings.
            // Normalize only CRLF transport endings so the pinned text hash remains platform-stable.
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Built-in prompt resource could not be loaded: " + path, failure);
        }
    }

    private static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String shortHash(String hash) { return hash.substring(0, 12); }
}
