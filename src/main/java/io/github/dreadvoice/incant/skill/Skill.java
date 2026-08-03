package io.github.dreadvoice.incant.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public record Skill(
        String name,
        String description,
        String instructions,
        Path root,
        SkillClass skillClass,
        Bundle bundle,
        Map<String, Object> metadata
) {

    public Skill {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(skillClass, "skillClass");

        if (name.isBlank()) {
            throw new IllegalArgumentException("skill name must not be blank: " + root);
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("skill description must not be blank: " + name);
        }

        instructions = instructions == null ? "" : instructions;
        bundle = bundle == null ? Bundle.empty() : bundle;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public record Bundle(List<Path> scripts, List<Path> references, List<Path> assets) {

        private static final Bundle EMPTY = new Bundle(List.of(), List.of(), List.of());

        public Bundle {
            scripts = scripts == null ? List.of() : List.copyOf(scripts);
            references = references == null ? List.of() : List.copyOf(references);
            assets = assets == null ? List.of() : List.copyOf(assets);
        }

        public static Bundle empty() {
            return EMPTY;
        }

        public boolean hasScripts() {
            return !scripts.isEmpty();
        }

        public boolean isEmpty() {
            return scripts.isEmpty() && references.isEmpty() && assets.isEmpty();
        }
    }

    public Path definitionFile() {
        return root.resolve("SKILL.md");
    }

    public int instructionLength() {
        return instructions.length();
    }

    public boolean isRunnable(boolean sandboxAvailable, boolean workspaceActive) {
        return switch (skillClass) {
            case INSTRUCTION -> true;
            case DOCUMENT -> sandboxAvailable;
            case CODING_AGENT -> workspaceActive;
        };
    }
}