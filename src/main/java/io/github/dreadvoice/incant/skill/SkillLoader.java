package io.github.dreadvoice.incant.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class SkillLoader {

    private static final String NAME_KEY = "name";
    private static final String DESCRIPTION_KEY = "description";
    private static final Set<String> CORE_KEYS = Set.of(NAME_KEY, DESCRIPTION_KEY);
    private static final String SCRIPTS_DIR = "scripts";
    private static final String REFERENCES_DIR = "references";
    private static final String ASSETS_DIR = "assets";

    private final SkillSource source;

    public SkillLoader(SkillSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public List<Skill> load() {
        List<Skill> skills = new ArrayList<>();
        for (Path root : source.discover()) {
            skills.add(loadSkill(root));
        }
        return List.copyOf(skills);
    }

    public Skill loadSkill(Path root) {
        Objects.requireNonNull(root, "root");
        Path definition = root.resolve(LocalDirSkillSource.DEFINITION_FILE);

        if (!Files.isRegularFile(definition)) {
            throw new SkillParseException(definition, "no " + LocalDirSkillSource.DEFINITION_FILE + " found");
        }

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(read(definition), definition);
        Frontmatter frontmatter = parsed.frontmatter();

        return new Skill(
                frontmatter.requiredString(NAME_KEY),
                frontmatter.requiredString(DESCRIPTION_KEY),
                parsed.body(),
                root,
                SkillClassifier.classify(root, frontmatter),
                bundleOf(root),
                frontmatter.excluding(CORE_KEYS));
    }

    private static Skill.Bundle bundleOf(Path root) {
        return new Skill.Bundle(
                filesIn(root.resolve(SCRIPTS_DIR)),
                filesIn(root.resolve(REFERENCES_DIR)),
                filesIn(root.resolve(ASSETS_DIR)));
    }

    private static List<Path> filesIn(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + directory, e);
        }
    }

    private static String read(Path definition) {
        try {
            return Files.readString(definition);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + definition, e);
        }
    }
}
