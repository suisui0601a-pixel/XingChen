package online.wanan.xingchen.adapter.dsh;

/** Domain-facing DSH session port. It deliberately exposes no transport DTOs. */
public interface DshGateway {
    String findOrCreateWorkspace(String existingDirectory);
    String createSession(String workspaceId, String mode, String preset, String model);
    void prompt(String sessionId, String text);
    void stop(String sessionId);
    void archive(String sessionId);
    void selectModel(String sessionId, String model);
    default DshFollowSubscription follow(String sessionId, java.util.function.Consumer<DshFollowEvent> events, java.util.function.Consumer<Throwable> failure){throw new UnsupportedOperationException("DSH follow is unavailable");}
    default DshInteractionSubscription interactionEvents(java.util.function.Consumer<DshInteractionEvent> events, java.util.function.Consumer<Throwable> failure){throw new UnsupportedOperationException("DSH interaction events are unavailable");}
    default void respondToInteraction(String clientId,String eventId,com.fasterxml.jackson.databind.JsonNode outcome){throw new UnsupportedOperationException("DSH interaction results are unavailable");}
    default void respondToInteraction(String clientId,long generation,String eventId,com.fasterxml.jackson.databind.JsonNode outcome){respondToInteraction(clientId,eventId,outcome);}
    default void bindInteractionGeneration(String clientId,long generation) { }
}
