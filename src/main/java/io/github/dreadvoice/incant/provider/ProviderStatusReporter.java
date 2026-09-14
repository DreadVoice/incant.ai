package io.github.dreadvoice.incant.provider;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ProviderStatusReporter {

    private final ProviderProperties properties;
    private final OllamaCatalog catalog;

    public ProviderStatusReporter(ProviderProperties properties, OllamaCatalog catalog) {
        this.properties = properties;
        this.catalog = catalog;
    }

    public Report report() {
        List<ProviderStatus> providers = new ArrayList<>();
        for (String provider : List.of(ProviderFactory.ANTHROPIC, ProviderFactory.OPENAI, ProviderFactory.GEMINI,
                ProviderFactory.BEDROCK, ProviderFactory.OLLAMA)) {
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
        String baseUrl = catalog.baseUrl();
        String model = settings.getModel();

        List<String> installed = catalog.models(baseUrl);
        if (installed == null) {
            return new ProviderStatus(ProviderFactory.OLLAMA, false, model, "not reachable at " + baseUrl);
        }

        boolean hasModel = installed.contains(model) || installed.contains(model + ":latest");
        return new ProviderStatus(ProviderFactory.OLLAMA, hasModel, model,
                hasModel ? "model installed" : "model not installed, installed: " + installed);
    }

    public record Report(String defaultProvider, List<ProviderStatus> providers) {
    }

    public record ProviderStatus(String name, boolean available, String model, String detail) {
    }
}
