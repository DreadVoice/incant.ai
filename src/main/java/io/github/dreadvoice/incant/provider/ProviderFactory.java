package io.github.dreadvoice.incant.provider;

import java.util.Locale;
import java.util.Set;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

public final class ProviderFactory {

    public static final String ANTHROPIC = "anthropic";

    private static final Set<String> SUPPORTED = Set.of(ANTHROPIC);

    private ProviderFactory() {
    }

    public static Set<String> supported() {
        return SUPPORTED;
    }

    public static boolean supports(String provider) {
        return provider != null && SUPPORTED.contains(normalize(provider));
    }

    public static ChatModel create(String provider, String apiKey, String modelName) {
        String name = normalize(require(provider, "provider"));

        return switch (name) {
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .apiKey(require(apiKey, "apiKey"))
                    .modelName(require(modelName, "modelName"))
                    .build();
            default -> throw new IllegalArgumentException(
                    "unknown provider '" + provider + "', supported: " + SUPPORTED);
        };
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
