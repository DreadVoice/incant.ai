package io.github.dreadvoice.incant.agent;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public final class SkillTools {

    public static final String NAME_ARGUMENT = "name";

    private static final String DESCRIPTION = "Returns the full instructions of one skill listed under "
            + "'Available skills' in the system prompt. Call this before following a skill, and only for a skill "
            + "that is listed there.";

    private SkillTools() {
    }

    public static ToolSpecification loadSkill() {
        return ToolSpecification.builder()
                .name(SystemPromptBuilder.LOAD_SKILL_TOOL)
                .description(DESCRIPTION)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(NAME_ARGUMENT, "Name of the skill to load, exactly as listed.")
                        .required(NAME_ARGUMENT)
                        .build())
                .build();
    }

    public static List<ToolSpecification> all() {
        return List.of(loadSkill());
    }

    public static List<String> loadedSkills(List<AgentOrchestrator.ToolCall> toolCalls) {
        return toolCalls.stream()
                .filter(call -> !call.failed())
                .filter(call -> SystemPromptBuilder.LOAD_SKILL_TOOL.equals(call.tool()))
                .map(call -> call.arguments().get(NAME_ARGUMENT))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
