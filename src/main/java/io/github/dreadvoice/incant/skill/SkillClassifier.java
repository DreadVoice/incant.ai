package io.github.dreadvoice.incant.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class SkillClassifier {

    public static final String ALLOWED_TOOLS_KEY = "allowed-tools";

    private static final Set<String> SCRIPT_EXTENSIONS = Set.of("py", "sh", "js", "ts", "rb", "ps1");
    private static final Set<String> AGENT_TOOLS = Set.of("edit", "multiedit", "write", "task", "git");
    private static final Set<String> AGENT_DIRECTORIES = Set.of("agents", "commands", "hooks");
    private static final int MAX_DEPTH = 4;

    private SkillClassifier() {
    }

    public static SkillClass classify(Path root, Frontmatter frontmatter) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(frontmatter, "frontmatter");

        if (hasAgentHarnessMarkers(root, frontmatter)) {
            return SkillClass.CODING_AGENT;
        }
        if (hasScripts(root)) {
            return SkillClass.DOCUMENT;
        }
        return SkillClass.INSTRUCTION;
    }

    public static boolean hasAgentHarnessMarkers(Path root, Frontmatter frontmatter) {
        List<String> tools = frontmatter.stringList(ALLOWED_TOOLS_KEY);
        for (String tool : tools) {
            if (AGENT_TOOLS.contains(tool.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String directory : AGENT_DIRECTORIES) {
            if (Files.isDirectory(root.resolve(directory))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasScripts(Path root) {
        if (!Files.isDirectory(root)) {
            return false;
        }

        try (Stream<Path> files = Files.walk(root, MAX_DEPTH)) {
            return files.filter(Files::isRegularFile).anyMatch(SkillClassifier::isScript);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to inspect skill files in " + root, e);
        }
    }

    private static boolean isScript(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return SCRIPT_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
