package io.github.dreadvoice.incant.provider;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "incant")
public class ProviderProperties {

    private String provider = ProviderFactory.ANTHROPIC;

    private Map<String, Settings> providers = new LinkedHashMap<>();

    public String getProvider() {
        return provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, Settings> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Settings> providers) {
        this.providers = providers;
    }

    public Settings settings(String provider) {
        Settings settings = providers.get(provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT));
        return settings == null ? new Settings() : settings;
    }

    public static class Settings {

        private String apiKey = "";
        private String model = "";
        private String baseUrl = "";
        private boolean autoInstall;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isAutoInstall() {
            return autoInstall;
        }

        public void setAutoInstall(boolean autoInstall) {
            this.autoInstall = autoInstall;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean hasModel() {
            return model != null && !model.isBlank();
        }
    }
}
