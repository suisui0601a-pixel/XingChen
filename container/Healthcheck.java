import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class Healthcheck {
    public static void main(String[] args) {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:3200/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().contains("\"status\":\"UP\"")) System.exit(1);
        } catch (Exception ignored) {
            System.exit(1);
        }
    }
}
