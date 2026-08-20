package io.github.dreadvoice.incant.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public final class ToolDispatcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {
    };

    private final Map<String, ToolHandler> handlers;

    public ToolDispatcher(Iterable<ToolHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        Map<String, ToolHandler> byName = new LinkedHashMap<>();
        for (ToolHandler handler : handlers) {
            ToolHandler previous = byName.putIfAbsent(handler.name(), handler);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate tool handler for '" + handler.name() + "'");
            }
        }
        this.handlers = Map.copyOf(byName);
    }

    public Set<String> names() {
        return handlers.keySet();
    }

    public boolean supports(String name) {
        return name != null && handlers.containsKey(name);
    }

    public String dispatch(ToolExecutionRequest request) {
        Objects.requireNonNull(request, "request");

        ToolHandler handler = handlers.get(request.name());
        if (handler == null) {
            return error("unknown tool '" + request.name() + "', available: " + handlers.keySet());
        }

        Map<String, Object> arguments;
        try {
            arguments = parse(request.arguments());
        } catch (Exception e) {
            return error("could not read arguments for '" + request.name() + "': " + e.getMessage());
        }

        try {
            return handler.execute(arguments);
        } catch (Exception e) {
            return error("tool '" + request.name() + "' failed: " + e.getMessage());
        }
    }

    private static Map<String, Object> parse(String arguments) throws Exception {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = MAPPER.readValue(arguments, ARGUMENTS);
        return parsed == null ? Map.of() : parsed;
    }

    private static String error(String message) {
        return "Error: " + message;
    }
}
