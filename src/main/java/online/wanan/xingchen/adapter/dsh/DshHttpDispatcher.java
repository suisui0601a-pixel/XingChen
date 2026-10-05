package online.wanan.xingchen.adapter.dsh;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/** Thin boundary at which an HTTP request becomes dispatch-started. */
public interface DshHttpDispatcher {
    /** Hook runs before dispatch; throwing here guarantees no HTTP send method was invoked. */
    default void beforeDispatch(String endpoint, HttpRequest request) { }

    CompletableFuture<HttpResponse<String>> dispatch(HttpClient client, HttpRequest request);

    static DshHttpDispatcher javaHttpClient() {
        return (client, request) -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
