package online.wanan.xingchen.core.prompt;

/** Immutable, process-lifetime prompt baseline loaded from classpath resources. */
public record PromptBaseline(String persona, String simulation, String personaSha256, String simulationSha256) {
    public PromptBaseline {
        if (persona == null || persona.isBlank()) throw new IllegalArgumentException("built-in Persona is empty");
        if (simulation == null || simulation.isBlank()) throw new IllegalArgumentException("built-in Simulation is empty");
        if (personaSha256 == null || simulationSha256 == null) throw new IllegalArgumentException("prompt hashes are required");
    }
}
