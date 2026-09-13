package io.github.dreadvoice.incant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.dreadvoice.incant.provider.ProviderProperties;

class ConfigStoreTest {

    @TempDir
    private Path home;

    @Test
    void writesTheKeyToTheConfigFileAndAppliesItImmediately() {
        ProviderProperties properties = properties();

        store(properties).updateApiKeys(Map.of("anthropic", "sk-ant-test"));

        assertThat(properties.settings("anthropic").getApiKey()).isEqualTo("sk-ant-test");
        assertThat(configFile()).content().contains("anthropic").contains("sk-ant-test");
    }

    @Test
    void readsStoredKeysOnStartup() throws IOException {
        writeConfig("""
                providers:
                  openai:
                    api-key: sk-stored
                """);
        ProviderProperties properties = properties();

        store(properties).applyStoredSettings();

        assertThat(properties.settings("openai").getApiKey()).isEqualTo("sk-stored");
    }

    @Test
    void keepsAKeyThatIsAlreadyConfiguredFromTheEnvironment() throws IOException {
        writeConfig("""
                providers:
                  openai:
                    api-key: sk-stored
                """);
        ProviderProperties properties = properties();
        properties.settings("openai").setApiKey("sk-from-environment");

        store(properties).applyStoredSettings();

        assertThat(properties.settings("openai").getApiKey()).isEqualTo("sk-from-environment");
    }

    @Test
    void storesEachProviderSeparately() {
        ProviderProperties properties = properties();
        ConfigStore store = store(properties);

        store.updateApiKeys(Map.of("anthropic", "sk-ant"));
        store.updateApiKeys(Map.of("openai", "sk-openai"));

        assertThat(properties.settings("anthropic").getApiKey()).isEqualTo("sk-ant");
        assertThat(properties.settings("openai").getApiKey()).isEqualTo("sk-openai");
        assertThat(configFile()).content().contains("sk-ant").contains("sk-openai");
    }

    @Test
    void aBlankValueClearsTheStoredKey() {
        ProviderProperties properties = properties();
        ConfigStore store = store(properties);
        store.updateApiKeys(Map.of("anthropic", "sk-ant"));

        store.updateApiKeys(Map.of("anthropic", "  "));

        assertThat(properties.settings("anthropic").hasApiKey()).isFalse();
        assertThat(configFile()).content().doesNotContain("sk-ant");
    }

    @Test
    void rejectsAProviderThatTakesNoApiKey() {
        ConfigStore store = store(properties());

        assertThatThrownBy(() -> store.updateApiKeys(Map.of("ollama", "irrelevant")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not take an api key");
    }

    @Test
    void theConfigFileIsReadableOnlyByItsOwner() throws IOException {
        store(properties()).updateApiKeys(Map.of("anthropic", "sk-ant"));

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(configFile())))
                .isEqualTo("rw-------");
    }

    @Test
    void storesTheChosenLocalModel() {
        ProviderProperties properties = properties();

        store(properties).updateLocalModel("qwen2.5:0.5b");

        assertThat(properties.settings("ollama").getModel()).isEqualTo("qwen2.5:0.5b");
        assertThat(configFile()).content().contains("ollama").contains("qwen2.5:0.5b");
    }

    @Test
    void readsTheStoredLocalModelOnStartup() throws IOException {
        writeConfig("""
                providers:
                  ollama:
                    model: llama3.2:1b
                """);
        ProviderProperties properties = properties();
        properties.settings("ollama").setModel("incant-qwen");

        store(properties).applyStoredSettings();

        assertThat(properties.settings("ollama").getModel()).isEqualTo("llama3.2:1b");
    }

    @Test
    void keepsTheApiKeyWhenTheLocalModelChanges() {
        ProviderProperties properties = properties();
        ConfigStore store = store(properties);
        store.updateApiKeys(Map.of("openai", "sk-openai"));

        store.updateLocalModel("qwen2.5:0.5b");

        assertThat(configFile()).content().contains("sk-openai").contains("qwen2.5:0.5b");
    }

    @Test
    void rejectsABlankLocalModel() {
        ConfigStore store = store(properties());

        assertThatThrownBy(() -> store.updateLocalModel("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    private ConfigStore store(ProviderProperties properties) {
        return new ConfigStore(properties, configFile().toString());
    }

    private Path configFile() {
        return home.resolve(".incant").resolve("config.yml");
    }

    private void writeConfig(String content) throws IOException {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), content);
    }

    private static ProviderProperties properties() {
        Map<String, ProviderProperties.Settings> providers = new LinkedHashMap<>();
        providers.put("anthropic", new ProviderProperties.Settings());
        providers.put("openai", new ProviderProperties.Settings());
        providers.put("ollama", new ProviderProperties.Settings());

        ProviderProperties properties = new ProviderProperties();
        properties.setProviders(providers);
        return properties;
    }
}
