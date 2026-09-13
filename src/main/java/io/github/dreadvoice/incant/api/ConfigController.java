package io.github.dreadvoice.incant.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.config.ConfigStore;
import io.github.dreadvoice.incant.provider.OllamaCatalog;
import io.github.dreadvoice.incant.provider.ProviderFactory;
import io.github.dreadvoice.incant.provider.ProviderProperties;
import io.github.dreadvoice.incant.provider.ProviderStatusReporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigStore config;
    private final ProviderStatusReporter reporter;
    private final OllamaCatalog catalog;
    private final ProviderProperties properties;

    public ConfigController(ConfigStore config, ProviderStatusReporter reporter, OllamaCatalog catalog,
            ProviderProperties properties) {
        this.config = config;
        this.reporter = reporter;
        this.catalog = catalog;
        this.properties = properties;
    }

    @PutMapping(path = "/api-keys", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderStatusReporter.Report updateApiKeys(@Valid @RequestBody ApiKeys request) {
        config.updateApiKeys(request.apiKeys());
        return reporter.report();
    }

    @GetMapping(path = "/local-models", produces = MediaType.APPLICATION_JSON_VALUE)
    public LocalModels localModels() {
        OllamaCatalog.Installed installed = catalog.installed();
        return new LocalModels(installed.baseUrl(), installed.reachable(), installed.models(),
                properties.settings(ProviderFactory.OLLAMA).getModel());
    }

    @PutMapping(path = "/local-model", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderStatusReporter.Report updateLocalModel(@Valid @RequestBody LocalModel request) {
        OllamaCatalog.Installed installed = catalog.installed();
        if (!installed.reachable()) {
            throw new IllegalArgumentException("ollama is not reachable at " + installed.baseUrl());
        }
        if (!installed.models().contains(request.model())) {
            throw new IllegalArgumentException(
                    "model '" + request.model() + "' is not installed, installed: " + installed.models());
        }

        config.updateLocalModel(request.model());
        return reporter.report();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    public record ApiKeys(@NotEmpty Map<String, String> apiKeys) {
    }

    public record LocalModel(@NotBlank String model) {
    }

    public record LocalModels(String baseUrl, boolean reachable, List<String> models, String selected) {
    }
}
