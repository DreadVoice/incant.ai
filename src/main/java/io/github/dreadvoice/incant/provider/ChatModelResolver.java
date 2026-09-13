package io.github.dreadvoice.incant.provider;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

@Component
public class ChatModelResolver {

    private static final Logger log = LoggerFactory.getLogger(ChatModelResolver.class);

    private final ProviderProperties properties;

    public ChatModelResolver(ProviderProperties properties) {
        this.properties = properties;
    }

    public Resolved resolve(String requestedProvider, String requestedModel) {
        Choice choice = choose(requestedProvider, requestedModel);

        return new Resolved(choice.provider(), choice.model(), ProviderFactory.create(
                choice.provider(), choice.settings().getApiKey(), choice.model(), choice.settings().getBaseUrl()));
    }

    public ResolvedStream resolveStreaming(String requestedProvider, String requestedModel) {
        Choice choice = choose(requestedProvider, requestedModel);

        return new ResolvedStream(choice.provider(), choice.model(), ProviderFactory.createStreaming(
                choice.provider(), choice.settings().getApiKey(), choice.model(), choice.settings().getBaseUrl()));
    }

    private Choice choose(String requestedProvider, String requestedModel) {
        String provider = hasText(requestedProvider) ? normalize(requestedProvider) : properties.getProvider();
        if (!ProviderFactory.supports(provider)) {
            throw new IllegalArgumentException(
                    "unknown provider '" + provider + "', supported: " + ProviderFactory.supported());
        }

        ProviderProperties.Settings settings = properties.settings(provider);

        if (!ProviderFactory.OLLAMA.equals(provider) && !settings.hasApiKey()) {
            ProviderProperties.Settings ollama = properties.settings(ProviderFactory.OLLAMA);
            log.warn("no api key configured for provider '{}', falling back to local model '{}'",
                    provider, ollama.getModel());
            provider = ProviderFactory.OLLAMA;
            settings = ollama;
            requestedModel = null;
        }

        String model = hasText(requestedModel) ? requestedModel.strip() : configuredModel(provider, settings);

        return new Choice(provider, model, settings);
    }

    private static String configuredModel(String provider, ProviderProperties.Settings settings) {
        if (!settings.hasModel()) {
            throw new IllegalArgumentException("no model configured for provider '" + provider + "'");
        }
        return settings.getModel().strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Resolved(String provider, String model, ChatModel chatModel) {
    }

    public record ResolvedStream(String provider, String model, StreamingChatModel chatModel) {
    }

    private record Choice(String provider, String model, ProviderProperties.Settings settings) {
    }
}
