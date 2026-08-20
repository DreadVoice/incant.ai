package io.github.dreadvoice.incant.agent;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.dreadvoice.incant.skill.LocalDirSkillSource;
import io.github.dreadvoice.incant.skill.SkillLoader;
import io.github.dreadvoice.incant.skill.SkillRegistry;

@Configuration
public class AgentConfiguration {

    @Bean
    public SkillRegistry skillRegistry(@Value("${incant.skills-path}") String skillsPath) {
        SkillRegistry registry = new SkillRegistry(new SkillLoader(new LocalDirSkillSource(Path.of(skillsPath))));
        registry.refresh();
        return registry;
    }

    @Bean
    public SystemPromptBuilder systemPromptBuilder(SkillRegistry registry) {
        return new SystemPromptBuilder(registry);
    }

    @Bean
    public ToolDispatcher toolDispatcher(SkillRegistry registry) {
        return new ToolDispatcher(List.of(new LoadSkillTool(registry)));
    }
}
