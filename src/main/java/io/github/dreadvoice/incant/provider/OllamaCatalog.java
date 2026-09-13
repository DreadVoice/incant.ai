package io.github.dreadvoice.incant.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OllamaCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(PING_TIMEOUT)
            .build();

    private final ProviderProperties properties;

    public OllamaCatalog(ProviderProperties properties) {
        this.properties = properties;
    }

    public String baseUrl() {
        return baseUrl(properties.settings(ProviderFactory.OLLAMA));
    }

    public Installed installed() {
        String baseUrl = baseUrl();
        List<String> models = models(baseUrl);
        return new Installed(baseUrl, models != null, models == null ? List.of() : models);
    }

    List<String> models(String baseUrl) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                            .timeout(PING_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                return null;
            }

            List<String> names = new ArrayList<>();
            for (JsonNode model : MAPPER.readTree(response.body()).path("models")) {
                names.add(model.path("name").asText(""));
            }
            return names;
        } catch (Exception e) {
            return null;
        }
    }

    static String baseUrl(ProviderProperties.Settings settings) {
        String baseUrl = settings.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return ProviderFactory.DEFAULT_OLLAMA_BASE_URL;
        }
        return baseUrl.strip().replaceAll("/+$", "");
    }

    public record Installed(String baseUrl, boolean reachable, List<String> models) {
    }
}
