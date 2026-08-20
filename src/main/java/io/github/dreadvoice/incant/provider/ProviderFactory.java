package io.github.dreadvoice.incant.provider;

import java.util.Locale;
import java.util.Set;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public final class ProviderFactory {

    public static final String ANTHROPIC = "anthropic";
    public static final String OPENAI = "openai";

    private static final Set<String> SUPPORTED = Set.of(ANTHROPIC, OPENAI);

    private ProviderFactory() {
    }

    public static Set<String> supported() {
        return SUPPORTED;
    }

    public static boolean supports(String provider) {
        return provider != null && SUPPORTED.contains(normalize(provider));
    }

    public static ChatModel create(String provider, String apiKey, String modelName) {
        return create(provider, apiKey, modelName, null);
    }

    public static ChatModel create(String provider, String apiKey, String modelName, String baseUrl) {
        String name = normalize(require(provider, "provider"));
        String key = require(apiKey, "apiKey");
        String model = require(modelName, "modelName");

        return switch (name) {
            case ANTHROPIC -> anthropic(key, model, baseUrl);
            case OPENAI -> openAi(key, model, baseUrl);
            default -> throw new IllegalArgumentException(
                    "unknown provider '" + provider + "', supported: " + SUPPORTED);
        };
    }

    private static ChatModel anthropic(String apiKey, String modelName, String baseUrl) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl.strip());
        }
        return builder.build();
    }

    private static ChatModel openAi(String apiKey, String modelName, String baseUrl) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl.strip());
        }
        return builder.build();
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String require(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
