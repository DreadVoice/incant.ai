package io.github.dreadvoice.incant.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.dreadvoice.incant.provider.ProviderStatusReporter;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderStatusReporter reporter;

    public ProviderController(ProviderStatusReporter reporter) {
        this.reporter = reporter;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderStatusReporter.Report providers() {
        return reporter.report();
    }
}
