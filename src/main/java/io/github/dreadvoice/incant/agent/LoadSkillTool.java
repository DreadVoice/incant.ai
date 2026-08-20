package io.github.dreadvoice.incant.agent;

import java.util.Map;
import java.util.Objects;

import io.github.dreadvoice.incant.skill.Skill;
import io.github.dreadvoice.incant.skill.SkillRegistry;

public final class LoadSkillTool implements ToolHandler {

    private final SkillRegistry registry;

    public LoadSkillTool(SkillRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public String name() {
        return SystemPromptBuilder.LOAD_SKILL_TOOL;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Object requested = arguments.get(SkillTools.NAME_ARGUMENT);
        if (requested == null || requested.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "argument '" + SkillTools.NAME_ARGUMENT + "' is required, available skills: " + available());
        }

        String skillName = requested.toString().strip();
        Skill skill = registry.find(skillName).orElseThrow(() -> new IllegalArgumentException(
                "unknown skill '" + skillName + "', available skills: " + available()));

        if (skill.instructions().isBlank()) {
            throw new IllegalStateException("skill '" + skillName + "' has no instructions");
        }
        return skill.instructions();
    }

    private String available() {
        return registry.all().stream().map(Skill::name).toList().toString();
    }
}
