package io.github.dreadvoice.incant.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SkillClassifierTest {

    @Test
    void classifiesPdfAsDocument() {
        assertThat(classify("pdf")).isEqualTo(SkillClass.DOCUMENT);
    }

    @Test
    void classifiesDocxWithNestedScriptsAsDocument() {
        assertThat(classify("docx")).isEqualTo(SkillClass.DOCUMENT);
    }

    @Test
    void classifiesBrainstormingAsInstruction() {
        assertThat(classify("brainstorming")).isEqualTo(SkillClass.INSTRUCTION);
    }

    @Test
    void classifiesGitWorktreeSkillAsCodingAgentFromAllowedTools() {
        assertThat(classify("using-git-worktrees")).isEqualTo(SkillClass.CODING_AGENT);
    }

    @Test
    void classifiesBundledCommandsAndAgentsAsCodingAgent() {
        assertThat(classify("superpowers")).isEqualTo(SkillClass.CODING_AGENT);
    }

    @Test
    void agentMarkersWinOverScripts() {
        assertThat(classify("artifacts-builder")).isEqualTo(SkillClass.CODING_AGENT);
        assertThat(SkillClassifier.hasScripts(skillRoot("artifacts-builder"))).isTrue();
    }

    @Test
    void detectsScriptsOnlyWhereTheyExist() {
        assertThat(SkillClassifier.hasScripts(skillRoot("pdf"))).isTrue();
        assertThat(SkillClassifier.hasScripts(skillRoot("docx"))).isTrue();
        assertThat(SkillClassifier.hasScripts(skillRoot("brainstorming"))).isFalse();
    }

    @Test
    void detectsAgentHarnessMarkersOnlyWhereTheyExist() {
        assertThat(SkillClassifier.hasAgentHarnessMarkers(
                skillRoot("superpowers"), frontmatter("superpowers"))).isTrue();
        assertThat(SkillClassifier.hasAgentHarnessMarkers(
                skillRoot("pdf"), frontmatter("pdf"))).isFalse();
    }

    @Test
    void missingDirectoryHasNoScripts() {
        assertThat(SkillClassifier.hasScripts(Path.of("does", "not", "exist"))).isFalse();
    }

    private static SkillClass classify(String name) {
        return SkillClassifier.classify(skillRoot(name), frontmatter(name));
    }

    private static Frontmatter frontmatter(String name) {
        Path definition = skillRoot(name).resolve(LocalDirSkillSource.DEFINITION_FILE);
        try {
            return FrontmatterParser.parse(Files.readString(definition), definition).frontmatter();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read fixture " + definition, e);
        }
    }

    private static Path skillRoot(String name) {
        URL url = SkillClassifierTest.class.getResource("/skills/" + name);
        assertThat(url).as("fixture skill %s", name).isNotNull();
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
