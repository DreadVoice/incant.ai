package io.github.dreadvoice.incant.provider;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;

@Component
public class ChatModelResolver {

    private static final Logger log = LoggerFactory.getLogger(ChatModelResolver.class);

    private final String defaultProvider;
    private final String apiKey;
    private final String defaultModel;
    private final String baseUrl;
    private final String ollamaBaseUrl;
    private final String ollamaModel;

    public ChatModelResolver(@Value("${incant.provider}") String defaultProvider,
            @Value("${incant.api-key}") String apiKey,
            @Value("${incant.model}") String defaultModel,
            @Value("${incant.base-url}") String baseUrl,
            @Value("${incant.ollama.base-url}") String ollamaBaseUrl,
            @Value("${incant.ollama.model}") String ollamaModel) {
        this.defaultProvider = normalize(defaultProvider);
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
    }

    public Resolved resolve(String requestedProvider, String requestedModel) {
        String provider = hasText(requestedProvider) ? normalize(requestedProvider) : defaultProvider;
        if (!ProviderFactory.supports(provider)) {
            throw new IllegalArgumentException(
                    "unknown provider '" + provider + "', supported: " + ProviderFactory.supported());
        }

        String model = hasText(requestedModel) ? requestedModel.strip() : defaultModelFor(provider);

        if (!ProviderFactory.OLLAMA.equals(provider) && !hasText(apiKey)) {
            log.warn("no api key configured for provider '{}', falling back to local model '{}'",
                    provider, ollamaModel);
            provider = ProviderFactory.OLLAMA;
            model = ollamaModel;
        }

        return new Resolved(provider, model, ProviderFactory.create(provider, keyFor(provider), model,
                urlFor(provider)));
    }

    private String defaultModelFor(String provider) {
        if (ProviderFactory.OLLAMA.equals(provider)) {
            return ollamaModel;
        }
        if (provider.equals(defaultProvider) && hasText(defaultModel)) {
            return defaultModel;
        }
        throw new IllegalArgumentException("model is required for provider '" + provider + "'");
    }

    private String keyFor(String provider) {
        return ProviderFactory.OLLAMA.equals(provider) ? "" : apiKey;
    }

    private String urlFor(String provider) {
        return ProviderFactory.OLLAMA.equals(provider) ? ollamaBaseUrl : baseUrl;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Resolved(String provider, String model, ChatModel chatModel) {
    }
}
