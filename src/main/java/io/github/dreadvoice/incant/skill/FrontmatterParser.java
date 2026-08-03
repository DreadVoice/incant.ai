package io.github.dreadvoice.incant.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class FrontmatterParser {

    private static final String DELIMITER = "---";
    private static final int MAX_YAML_CODE_POINTS = 256 * 1024;

    private FrontmatterParser() {
    }

    public static ParsedSkillFile parse(String raw, Path source) {
        if (raw == null || raw.isBlank()) {
            throw new SkillParseException(source, "file is empty");
        }

        List<String> lines = normalize(raw).lines().toList();

        if (lines.isEmpty() || !isDelimiter(lines.get(0))) {
            throw new SkillParseException(source,
                    "missing YAML frontmatter — file must begin with a '---' line");
        }

        int closing = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (isDelimiter(lines.get(i))) {
                closing = i;
                break;
            }
        }
        if (closing < 0) {
            throw new SkillParseException(source,
                    "unterminated frontmatter — no closing '---' line found");
        }

        String yaml = String.join("\n", lines.subList(1, closing));
        String body = String.join("\n", lines.subList(closing + 1, lines.size())).strip();

        return new ParsedSkillFile(new Frontmatter(loadYaml(yaml, source), source), body);
    }

    private static Map<String, Object> loadYaml(String yaml, Path source) {
        if (yaml.isBlank()) {
            throw new SkillParseException(source, "frontmatter block is empty");
        }

        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(MAX_YAML_CODE_POINTS);
        options.setAllowDuplicateKeys(false);

        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (YAMLException e) {
            throw new SkillParseException(source, "invalid YAML in frontmatter", e);
        }

        if (loaded == null) {
            throw new SkillParseException(source, "frontmatter block is empty");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new SkillParseException(source,
                    "frontmatter must be a key-value mapping, found "
                            + loaded.getClass().getSimpleName());
        }

        return map.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        e -> String.valueOf(e.getKey()),
                        e -> e.getValue() == null ? "" : e.getValue()));
    }

    private static String normalize(String raw) {
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean isDelimiter(String line) {
        return line.strip().equals(DELIMITER);
    }

    public record ParsedSkillFile(Frontmatter frontmatter, String body) {
    }
}