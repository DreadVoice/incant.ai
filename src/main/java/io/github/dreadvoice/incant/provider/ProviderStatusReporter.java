package io.github.dreadvoice.incant.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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

    private final String defaultProvider;
    private final String apiKey;
    private final String defaultModel;
    private final String ollamaBaseUrl;
    private final String ollamaModel;

    public ProviderStatusReporter(@Value("${incant.provider}") String defaultProvider,
            @Value("${incant.api-key}") String apiKey,
            @Value("${incant.model}") String defaultModel,
            @Value("${incant.ollama.base-url}") String ollamaBaseUrl,
            @Value("${incant.ollama.model}") String ollamaModel) {
        this.defaultProvider = defaultProvider.strip();
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.ollamaBaseUrl = ollamaBaseUrl.strip().replaceAll("/+$", "");
        this.ollamaModel = ollamaModel.strip();
    }

    public Report report() {
        List<ProviderStatus> providers = new ArrayList<>();
        for (String provider : List.of(ProviderFactory.ANTHROPIC, ProviderFactory.OPENAI, ProviderFactory.OLLAMA)) {
            providers.add(ProviderFactory.OLLAMA.equals(provider) ? ollamaStatus() : keyedStatus(provider));
        }
        return new Report(defaultProvider, providers);
    }

    private ProviderStatus keyedStatus(String provider) {
        boolean configured = apiKey != null && !apiKey.isBlank();
        String model = provider.equals(defaultProvider) ? defaultModel : "";
        return new ProviderStatus(provider, configured, model,
                configured ? "api key configured" : "no api key configured");
    }

    private ProviderStatus ollamaStatus() {
        List<String> installed = installedModels();
        if (installed == null) {
            return new ProviderStatus(ProviderFactory.OLLAMA, false, ollamaModel,
                    "not reachable at " + ollamaBaseUrl);
        }
        boolean hasModel = installed.contains(ollamaModel) || installed.contains(ollamaModel + ":latest");
        return new ProviderStatus(ProviderFactory.OLLAMA, hasModel, ollamaModel,
                hasModel ? "model installed" : "model not installed, installed: " + installed);
    }

    private List<String> installedModels() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/tags"))
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

    public record Report(String defaultProvider, List<ProviderStatus> providers) {
    }

    public record ProviderStatus(String name, boolean available, String model, String detail) {
    }
}
