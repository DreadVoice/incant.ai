package io.github.dreadvoice.incant.provider;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import software.amazon.awssdk.auth.token.credentials.StaticTokenProvider;
import software.amazon.awssdk.http.auth.spi.scheme.AuthSchemeOption;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public final class ProviderFactory {

    public static final String ANTHROPIC = "anthropic";
    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";
    public static final String BEDROCK = "bedrock";

    private static final String BEARER_AUTH = "smithy.api#httpBearerAuth";
    public static final String OLLAMA = "ollama";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    private static final Set<String> SUPPORTED = Set.of(ANTHROPIC, OPENAI, GEMINI, BEDROCK, OLLAMA);

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
        return create(provider, apiKey, modelName, baseUrl, null);
    }

    public static ChatModel create(String provider, String apiKey, String modelName, String baseUrl, String region) {
        String name = normalize(require(provider, "provider"));
        String model = require(modelName, "modelName");

        return switch (name) {
            case ANTHROPIC -> anthropic(require(apiKey, "apiKey"), model, baseUrl);
            case OPENAI -> openAi(require(apiKey, "apiKey"), model, baseUrl);
            case GEMINI -> gemini(require(apiKey, "apiKey"), model, baseUrl);
            case BEDROCK -> bedrock(apiKey, model, baseUrl, region);
            case OLLAMA -> ollama(model, baseUrl);
            default -> throw new IllegalArgumentException(
                    "unknown provider '" + provider + "', supported: " + SUPPORTED);
        };
    }

    public static StreamingChatModel createStreaming(String provider, String apiKey, String modelName,
            String baseUrl) {
        return createStreaming(provider, apiKey, modelName, baseUrl, null);
    }

    public static StreamingChatModel createStreaming(String provider, String apiKey, String modelName,
            String baseUrl, String region) {
        String name = normalize(require(provider, "provider"));
        String model = require(modelName, "modelName");

        return switch (name) {
            case ANTHROPIC -> streamingAnthropic(require(apiKey, "apiKey"), model, baseUrl);
            case OPENAI -> streamingOpenAi(require(apiKey, "apiKey"), model, baseUrl);
            case GEMINI -> streamingGemini(require(apiKey, "apiKey"), model, baseUrl);
            case BEDROCK -> streamingBedrock(apiKey, model, baseUrl, region);
            case OLLAMA -> streamingOllama(model, baseUrl);
            default -> throw new IllegalArgumentException(
                    "unknown provider '" + provider + "', supported: " + SUPPORTED);
        };
    }

    private static StreamingChatModel streamingAnthropic(String apiKey, String modelName, String baseUrl) {
        AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder = AnthropicStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl.strip());
        }
        return builder.build();
    }

    private static StreamingChatModel streamingOpenAi(String apiKey, String modelName, String baseUrl) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl.strip());
        }
        return builder.build();
    }

    private static StreamingChatModel streamingGemini(String apiKey, String modelName, String baseUrl) {
        GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder =
                GoogleAiGeminiStreamingChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelName);
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl.strip());
        }
        return builder.build();
    }

    private static StreamingChatModel streamingOllama(String modelName, String baseUrl) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(hasText(baseUrl) ? baseUrl.strip() : DEFAULT_OLLAMA_BASE_URL)
                .modelName(modelName)
                .build();
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

    private static ChatModel bedrock(String apiKey, String modelId, String baseUrl, String region) {
        BedrockRuntimeClientBuilder client = BedrockRuntimeClient.builder();
        if (hasText(region)) {
            client.region(Region.of(region.strip()));
        }
        if (hasText(apiKey)) {
            client.tokenProvider(StaticTokenProvider.create(apiKey::strip));
            client.authSchemeProvider(params -> List.of(AuthSchemeOption.builder().schemeId(BEARER_AUTH).build()));
        }
        if (hasText(baseUrl)) {
            client.endpointOverride(URI.create(baseUrl.strip()));
        }

        return BedrockChatModel.builder()
                .client(client.build())
                .modelId(modelId)
                .build();
    }

    private static StreamingChatModel streamingBedrock(String apiKey, String modelId, String baseUrl, String region) {
        BedrockRuntimeAsyncClientBuilder client = BedrockRuntimeAsyncClient.builder();
        if (hasText(region)) {
            client.region(Region.of(region.strip()));
        }
        if (hasText(apiKey)) {
            client.tokenProvider(StaticTokenProvider.create(apiKey::strip));
            client.authSchemeProvider(params -> List.of(AuthSchemeOption.builder().schemeId(BEARER_AUTH).build()));
        }
        if (hasText(baseUrl)) {
            client.endpointOverride(URI.create(baseUrl.strip()));
        }

        return BedrockStreamingChatModel.builder()
                .client(client.build())
                .modelId(modelId)
                .build();
    }

    private static ChatModel gemini(String apiKey, String modelName, String baseUrl) {
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
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
