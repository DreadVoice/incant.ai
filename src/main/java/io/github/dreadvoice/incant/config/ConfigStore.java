package io.github.dreadvoice.incant.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import io.github.dreadvoice.incant.provider.ProviderFactory;
import io.github.dreadvoice.incant.provider.ProviderProperties;
import jakarta.annotation.PostConstruct;

@Component
public class ConfigStore {

    public static final String BEDROCK = "bedrock";

    public static final List<String> KEYED_PROVIDERS =
            List.of(ProviderFactory.ANTHROPIC, ProviderFactory.OPENAI, ProviderFactory.GEMINI, BEDROCK);

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private static final String PROVIDERS = "providers";
    private static final String API_KEY = "api-key";
    private static final String MODEL = "model";
    private static final String OLLAMA_MODEL_ENVIRONMENT_VARIABLE = "INCANT_OLLAMA_MODEL";
    private static final String OWNER_ONLY = "rw-------";

    private final ProviderProperties properties;
    private final Path configPath;

    public ConfigStore(ProviderProperties properties, @Value("${incant.config-path}") String configPath) {
        this.properties = properties;
        this.configPath = Path.of(configPath);
    }

    @PostConstruct
    void applyStoredSettings() {
        read().forEach((provider, settings) -> {
            ProviderProperties.Settings target = properties.settings(provider);

            String apiKey = settings.get(API_KEY);
            if (apiKey != null && !target.hasApiKey()) {
                target.setApiKey(apiKey);
                log.info("loaded api key for provider '{}' from {}", provider, configPath);
            }

            String model = settings.get(MODEL);
            if (model != null && environmentModel(provider) == null) {
                target.setModel(model);
                log.info("loaded model '{}' for provider '{}' from {}", model, provider, configPath);
            }
        });
    }

    public synchronized void updateApiKeys(Map<String, String> apiKeys) {
        Map<String, Map<String, String>> stored = read();

        apiKeys.forEach((requested, value) -> {
            String provider = normalize(requested);
            if (!KEYED_PROVIDERS.contains(provider)) {
                throw new IllegalArgumentException(
                        "provider '" + requested + "' does not take an api key, expected one of " + KEYED_PROVIDERS);
            }

            String apiKey = value == null ? "" : value.strip();
            properties.settings(provider).setApiKey(apiKey);
            if (apiKey.isEmpty()) {
                settingsFor(stored, provider).remove(API_KEY);
            } else {
                settingsFor(stored, provider).put(API_KEY, apiKey);
            }
        });

        write(stored);
    }

    public synchronized void updateLocalModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }

        Map<String, Map<String, String>> stored = read();
        properties.settings(ProviderFactory.OLLAMA).setModel(model.strip());
        settingsFor(stored, ProviderFactory.OLLAMA).put(MODEL, model.strip());
        write(stored);
    }

    private static Map<String, String> settingsFor(Map<String, Map<String, String>> stored, String provider) {
        return stored.computeIfAbsent(provider, name -> new LinkedHashMap<>());
    }

    private static String environmentModel(String provider) {
        if (!ProviderFactory.OLLAMA.equals(provider)) {
            return null;
        }
        String value = System.getenv(OLLAMA_MODEL_ENVIRONMENT_VARIABLE);
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, Map<String, String>> read() {
        if (!Files.isRegularFile(configPath)) {
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            return settingsFrom(new Yaml().load(reader));
        } catch (IOException | RuntimeException e) {
            log.warn("could not read {}: {}", configPath, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, Map<String, String>> settingsFrom(Object document) {
        Map<String, Map<String, String>> stored = new LinkedHashMap<>();
        if (!(document instanceof Map<?, ?> root) || !(root.get(PROVIDERS) instanceof Map<?, ?> providers)) {
            return stored;
        }

        for (Map.Entry<?, ?> entry : providers.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> settings)) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (String field : List.of(API_KEY, MODEL)) {
                if (settings.get(field) instanceof String value && !value.isBlank()) {
                    values.put(field, value.strip());
                }
            }
            if (!values.isEmpty()) {
                stored.put(normalize(String.valueOf(entry.getKey())), values);
            }
        }
        return stored;
    }

    private void write(Map<String, Map<String, String>> stored) {
        Map<String, Object> providers = new LinkedHashMap<>();
        stored.forEach((provider, settings) -> {
            if (!settings.isEmpty()) {
                providers.put(provider, new LinkedHashMap<>(settings));
            }
        });

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        try {
            Path parent = configPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                new Yaml(options).dump(Map.of(PROVIDERS, providers), writer);
            }
            restrictToOwner();
        } catch (IOException e) {
            throw new IllegalStateException("could not write " + configPath + ": " + e.getMessage(), e);
        }
    }

    private void restrictToOwner() {
        try {
            Files.setPosixFilePermissions(configPath, PosixFilePermissions.fromString(OWNER_ONLY));
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("could not restrict permissions on {}: {}", configPath, e.getMessage());
        }
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
    }
}
