package io.github.dreadvoice.incant.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SkillRegistry {

    private final SkillLoader loader;

    private volatile Map<String, Skill> skills = Map.of();

    public SkillRegistry(SkillLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public synchronized List<Skill> refresh() {
        Map<String, Skill> reloaded = new LinkedHashMap<>();
        for (Skill skill : loader.load()) {
            Skill previous = reloaded.putIfAbsent(skill.name(), skill);
            if (previous != null) {
                throw new SkillParseException(skill.definitionFile(),
                        "duplicate skill name '" + skill.name() + "', already loaded from " + previous.root());
            }
        }
        skills = Map.copyOf(reloaded);
        return all();
    }

    public List<Skill> all() {
        return List.copyOf(skills.values());
    }

    public Optional<Skill> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(skills.get(name));
    }

    public List<Skill> byClass(SkillClass skillClass) {
        return skills.values().stream()
                .filter(skill -> skill.skillClass() == skillClass)
                .toList();
    }

    public int size() {
        return skills.size();
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }
}
