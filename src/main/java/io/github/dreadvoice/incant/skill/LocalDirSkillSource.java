package io.github.dreadvoice.incant.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class LocalDirSkillSource implements SkillSource {

    public static final String DEFINITION_FILE = "SKILL.md";

    private final Path root;

    public LocalDirSkillSource(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public String name() {
        return root.toString();
    }

    @Override
    public List<Path> discover() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(LocalDirSkillSource::isSkillDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list skills in " + root, e);
        }
    }

    private static boolean isSkillDirectory(Path candidate) {
        return Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve(DEFINITION_FILE));
    }

    @Override
    public String toString() {
        return "LocalDirSkillSource[" + root + "]";
    }
}
