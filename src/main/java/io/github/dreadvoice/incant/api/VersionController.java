package io.github.dreadvoice.incant.api;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/version")
public class VersionController {

    private static final String UNKNOWN = "unknown";

    private final ObjectProvider<BuildProperties> buildProperties;

    public VersionController(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Version version() {
        BuildProperties build = buildProperties.getIfAvailable();
        if (build == null) {
            return new Version("incant", UNKNOWN, null);
        }

        return new Version(build.getName(), build.getVersion(), build.getTime());
    }

    public record Version(String name, String version, Instant builtAt) {
    }
}
