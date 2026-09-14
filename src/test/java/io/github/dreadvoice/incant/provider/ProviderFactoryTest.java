package io.github.dreadvoice.incant.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

class ProviderFactoryTest {

    @Test
    void supportsGemini() {
        assertThat(ProviderFactory.supports("gemini")).isTrue();
        assertThat(ProviderFactory.supported()).contains(ProviderFactory.GEMINI);
    }

    @Test
    void buildsAGeminiChatModel() {
        assertThat(ProviderFactory.create(ProviderFactory.GEMINI, "gm-key", "gemini-2.5-flash", null))
                .isInstanceOf(GoogleAiGeminiChatModel.class);
    }

    @Test
    void buildsAStreamingGeminiChatModel() {
        assertThat(ProviderFactory.createStreaming(ProviderFactory.GEMINI, "gm-key", "gemini-2.5-flash", null))
                .isInstanceOf(GoogleAiGeminiStreamingChatModel.class);
    }

    @Test
    void geminiNeedsAnApiKey() {
        assertThatThrownBy(() -> ProviderFactory.create(ProviderFactory.GEMINI, "  ", "gemini-2.5-flash", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void supportsBedrock() {
        assertThat(ProviderFactory.supports("bedrock")).isTrue();
        assertThat(ProviderFactory.supported()).contains(ProviderFactory.BEDROCK);
    }

    @Test
    void buildsABedrockChatModel() {
        assertThat(ProviderFactory.create(ProviderFactory.BEDROCK, "bd-key", "a.model-v1:0", null, "us-east-1"))
                .isInstanceOf(BedrockChatModel.class);
    }

    @Test
    void buildsAStreamingBedrockChatModel() {
        assertThat(ProviderFactory.createStreaming(ProviderFactory.BEDROCK, "bd-key", "a.model-v1:0", null,
                "us-east-1")).isInstanceOf(BedrockStreamingChatModel.class);
    }

    @Test
    void bedrockStillNeedsAModelId() {
        assertThatThrownBy(() -> ProviderFactory.create(ProviderFactory.BEDROCK, "bd-key", "  ", null, "us-east-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }

    @Test
    void anUnknownProviderIsStillRejected() {
        assertThatThrownBy(() -> ProviderFactory.create("mistral", "key", "model", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown provider");
    }
}
