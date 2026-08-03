package io.github.dreadvoice.incant.skill;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public record Frontmatter(Map<String, Object> values, Path source) {

    public Frontmatter {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public String requiredString(String key) {
        return optionalString(key).orElseThrow(() ->
                new SkillParseException(source, "missing required frontmatter key: " + key));
    }

    public Optional<String> optionalString(String key) {
        Object raw = values.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        String text = String.valueOf(raw).trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    public List<String> stringList(String key) {
        Object raw = values.get(key);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return java.util.Arrays.stream(String.valueOf(raw).split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public Map<String, Object> excluding(Set<String> keys) {
        Map<String, Object> rest = new LinkedHashMap<>(values);
        rest.keySet().removeAll(keys);
        return Map.copyOf(rest);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}