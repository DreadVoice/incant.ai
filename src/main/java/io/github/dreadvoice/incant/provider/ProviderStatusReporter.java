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
public class ProviderStatusReporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(PING_TIMEOUT)
            .build();

    private final ProviderProperties properties;

    public ProviderStatusReporter(ProviderProperties properties) {
        this.properties = properties;
    }

    public Report report() {
        List<ProviderStatus> providers = new ArrayList<>();
        for (String provider : List.of(ProviderFactory.ANTHROPIC, ProviderFactory.OPENAI, ProviderFactory.OLLAMA)) {
            providers.add(ProviderFactory.OLLAMA.equals(provider) ? ollamaStatus() : keyedStatus(provider));
        }
        return new Report(properties.getProvider(), providers);
    }

    private ProviderStatus keyedStatus(String provider) {
        ProviderProperties.Settings settings = properties.settings(provider);
        if (!settings.hasApiKey()) {
            return new ProviderStatus(provider, false, settings.getModel(), "no api key configured");
        }
        if (!settings.hasModel()) {
            return new ProviderStatus(provider, false, "", "api key configured but no model configured");
        }
        return new ProviderStatus(provider, true, settings.getModel(), "api key configured");
    }

    private ProviderStatus ollamaStatus() {
        ProviderProperties.Settings settings = properties.settings(ProviderFactory.OLLAMA);
        String baseUrl = baseUrl(settings);
        String model = settings.getModel();

        List<String> installed = installedModels(baseUrl);
        if (installed == null) {
            return new ProviderStatus(ProviderFactory.OLLAMA, false, model, "not reachable at " + baseUrl);
        }

        boolean hasModel = installed.contains(model) || installed.contains(model + ":latest");
        return new ProviderStatus(ProviderFactory.OLLAMA, hasModel, model,
                hasModel ? "model installed" : "model not installed, installed: " + installed);
    }

    private List<String> installedModels(String baseUrl) {
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

    public record Report(String defaultProvider, List<ProviderStatus> providers) {
    }

    public record ProviderStatus(String name, boolean available, String model, String detail) {
    }
}
