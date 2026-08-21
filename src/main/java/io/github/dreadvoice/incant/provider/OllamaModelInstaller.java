package io.github.dreadvoice.incant.provider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OllamaModelInstaller {

    public static final String MODELFILE_RESOURCE = "ollama/Modelfile";

    private static final Logger log = LoggerFactory.getLogger(OllamaModelInstaller.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration CREATE_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final boolean enabled;
    private final String baseUrl;
    private final String modelName;

    public OllamaModelInstaller(ProviderProperties properties) {
        ProviderProperties.Settings settings = properties.settings(ProviderFactory.OLLAMA);
        this.enabled = settings.isAutoInstall();
        this.baseUrl = ProviderStatusReporter.baseUrl(settings);
        this.modelName = settings.getModel().strip();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void installOnFirstRun() {
        if (!enabled) {
            return;
        }
        try {
            install();
        } catch (Exception e) {
            log.warn("skipping local model install at {}: {}", baseUrl, e.toString());
        }
    }

    private void install() throws Exception {
        if (isInstalled()) {
            log.info("local model '{}' already installed", modelName);
            return;
        }

        Modelfile modelfile = readModelfile();
        log.info("installing local model '{}' from base '{}', this runs once and may take a while",
                modelName, modelfile.from());

        pull(modelfile.from());
        create(modelfile);
        log.info("local model '{}' is ready", modelName);
    }

    private boolean isInstalled() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                .timeout(LIST_TIMEOUT)
                .GET()
                .build());

        JsonNode models = MAPPER.readTree(response.body()).path("models");
        for (JsonNode model : models) {
            String name = model.path("name").asText("");
            if (name.equals(modelName) || name.equals(modelName + ":latest")) {
                return true;
            }
        }
        return false;
    }

    private void pull(String base) throws Exception {
        send(post("/api/pull", Map.of("model", base, "stream", false), PULL_TIMEOUT));
    }

    private void create(Modelfile modelfile) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("from", modelfile.from());
        if (!modelfile.system().isBlank()) {
            body.put("system", modelfile.system());
        }
        body.put("stream", false);
        send(post("/api/create", body, CREATE_TIMEOUT));
    }

    private HttpRequest post(String path, Map<String, Object> body, Duration timeout) throws IOException {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    request.uri() + " returned " + response.statusCode() + ": " + response.body());
        }
        return response;
    }

    static Modelfile readModelfile() throws IOException {
        try (InputStream stream = new ClassPathResource(MODELFILE_RESOURCE).getInputStream()) {
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static Modelfile parse(String content) {
        String from = "";
        StringBuilder system = new StringBuilder();
        boolean inSystem = false;

        for (String line : content.replace("\r\n", "\n").split("\n")) {
            String trimmed = line.strip();
            if (inSystem) {
                if (trimmed.endsWith("\"\"\"")) {
                    append(system, trimmed.substring(0, trimmed.length() - 3));
                    inSystem = false;
                } else {
                    append(system, trimmed);
                }
            } else if (trimmed.regionMatches(true, 0, "FROM ", 0, 5)) {
                from = trimmed.substring(5).strip();
            } else if (trimmed.regionMatches(true, 0, "SYSTEM ", 0, 7)) {
                String value = trimmed.substring(7).strip();
                if (value.startsWith("\"\"\"")) {
                    value = value.substring(3);
                    if (value.endsWith("\"\"\"")) {
                        value = value.substring(0, value.length() - 3);
                    } else {
                        inSystem = true;
                    }
                }
                append(system, value);
            }
        }

        if (from.isBlank()) {
            throw new IllegalStateException(MODELFILE_RESOURCE + " has no FROM line");
        }
        return new Modelfile(from, system.toString().strip());
    }

    private static void append(StringBuilder target, String line) {
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(line);
    }

    record Modelfile(String from, String system) {
    }
}
