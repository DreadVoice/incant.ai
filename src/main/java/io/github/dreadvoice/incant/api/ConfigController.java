package io.github.dreadvoice.incant.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.config.ApiKeyStore;
import io.github.dreadvoice.incant.provider.ProviderStatusReporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ApiKeyStore apiKeys;
    private final ProviderStatusReporter reporter;

    public ConfigController(ApiKeyStore apiKeys, ProviderStatusReporter reporter) {
        this.apiKeys = apiKeys;
        this.reporter = reporter;
    }

    @PutMapping(path = "/api-keys", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderStatusReporter.Report updateApiKeys(@Valid @RequestBody ApiKeys request) {
        apiKeys.update(request.apiKeys());
        return reporter.report();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    public record ApiKeys(@NotEmpty Map<String, String> apiKeys) {
    }
}
