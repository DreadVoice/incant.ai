package io.github.dreadvoice.incant.provider;

import java.util.Locale;
import java.util.Set;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public final class ProviderFactory {

    public static final String ANTHROPIC = "anthropic";
    public static final String OPENAI = "openai";
    public static final String OLLAMA = "ollama";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    private static final Set<String> SUPPORTED = Set.of(ANTHROPIC, OPENAI, OLLAMA);

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
        String model = require(modelName, "modelName");

        return switch (name) {
            case ANTHROPIC -> anthropic(require(apiKey, "apiKey"), model, baseUrl);
            case OPENAI -> openAi(require(apiKey, "apiKey"), model, baseUrl);
            case OLLAMA -> ollama(model, baseUrl);
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

    private static ChatModel ollama(String modelName, String baseUrl) {
        return OllamaChatModel.builder()
                .baseUrl(hasText(baseUrl) ? baseUrl.strip() : DEFAULT_OLLAMA_BASE_URL)
                .modelName(modelName)
                .build();
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
