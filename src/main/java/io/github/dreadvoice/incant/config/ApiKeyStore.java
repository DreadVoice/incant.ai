package io.github.dreadvoice.incant.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
public class ApiKeyStore {

    public static final Set<String> KEYED_PROVIDERS = Set.of(ProviderFactory.ANTHROPIC, ProviderFactory.OPENAI);

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStore.class);
    private static final String PROVIDERS = "providers";
    private static final String API_KEY = "api-key";
    private static final String OWNER_ONLY = "rw-------";

    private final ProviderProperties properties;
    private final Path configPath;

    public ApiKeyStore(ProviderProperties properties, @Value("${incant.config-path}") String configPath) {
        this.properties = properties;
        this.configPath = Path.of(configPath);
    }

    @PostConstruct
    void applyStoredKeys() {
        Map<String, String> stored = read();
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            ProviderProperties.Settings settings = properties.settings(entry.getKey());
            if (!settings.hasApiKey()) {
                settings.setApiKey(entry.getValue());
                log.info("loaded api key for provider '{}' from {}", entry.getKey(), configPath);
            }
        }
    }

    public synchronized void update(Map<String, String> apiKeys) {
        Map<String, String> stored = read();

        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            String provider = normalize(entry.getKey());
            if (!KEYED_PROVIDERS.contains(provider)) {
                throw new IllegalArgumentException(
                        "provider '" + entry.getKey() + "' does not take an api key, expected one of "
                                + KEYED_PROVIDERS);
            }

            String apiKey = entry.getValue() == null ? "" : entry.getValue().strip();
            properties.settings(provider).setApiKey(apiKey);
            if (apiKey.isEmpty()) {
                stored.remove(provider);
            } else {
                stored.put(provider, apiKey);
            }
        }

        write(stored);
    }

    private Map<String, String> read() {
        if (!Files.isRegularFile(configPath)) {
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object document = new Yaml().load(reader);
            return keysFrom(document);
        } catch (IOException | RuntimeException e) {
            log.warn("could not read {}: {}", configPath, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, String> keysFrom(Object document) {
        Map<String, String> keys = new LinkedHashMap<>();
        if (!(document instanceof Map<?, ?> root) || !(root.get(PROVIDERS) instanceof Map<?, ?> providers)) {
            return keys;
        }

        for (Map.Entry<?, ?> entry : providers.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> settings && settings.get(API_KEY) instanceof String apiKey
                    && !apiKey.isBlank()) {
                keys.put(normalize(String.valueOf(entry.getKey())), apiKey.strip());
            }
        }
        return keys;
    }

    private void write(Map<String, String> apiKeys) {
        Map<String, Object> providers = new LinkedHashMap<>();
        apiKeys.forEach((provider, apiKey) -> providers.put(provider, new LinkedHashMap<>(Map.of(API_KEY, apiKey))));

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
        return provider == null ? "" : provider.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
