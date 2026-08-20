package io.github.dreadvoice.incant.agent;

import java.util.List;
import java.util.Objects;

import io.github.dreadvoice.incant.skill.Skill;
import io.github.dreadvoice.incant.skill.SkillRegistry;

public final class SystemPromptBuilder {

    public static final String LOAD_SKILL_TOOL = "load_skill";

    private static final String HEADING = "## Available skills";
    private static final String GUIDANCE = "Each entry below is a skill you can use. The description says when the "
            + "skill applies; the instructions are not loaded yet. Call the " + LOAD_SKILL_TOOL + " tool with a "
            + "skill's name to read its full instructions, and do that before following it. Load a skill only when "
            + "the request actually calls for it, and never invent a skill name that is not listed.";

    private final SkillRegistry registry;

    public SystemPromptBuilder(SkillRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public String build(String basePrompt, boolean sandboxAvailable, boolean workspaceActive) {
        String base = basePrompt == null ? "" : basePrompt.strip();
        List<Skill> runnable = runnable(sandboxAvailable, workspaceActive);

        if (runnable.isEmpty()) {
            return base;
        }

        StringBuilder prompt = new StringBuilder();
        if (!base.isEmpty()) {
            prompt.append(base).append("\n\n");
        }
        prompt.append(HEADING).append("\n\n").append(GUIDANCE).append("\n\n");
        for (Skill skill : runnable) {
            prompt.append("- ").append(skill.name()).append(": ").append(oneLine(skill.description())).append('\n');
        }
        return prompt.toString().strip();
    }

    public List<Skill> runnable(boolean sandboxAvailable, boolean workspaceActive) {
        return registry.all().stream()
                .filter(skill -> skill.isRunnable(sandboxAvailable, workspaceActive))
                .toList();
    }

    private static String oneLine(String description) {
        return description.strip().replaceAll("\s+", " ");
    }
}
