package io.github.dreadvoice.incant.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class FrontmatterParserTest {

    private static final Path SOURCE = Path.of("skills", "example", "SKILL.md");

    @Test
    void parsesNameDescriptionAndBody() {
        String raw = """
                ---
                name: pdf
                description: Fills in and generates PDF files.
                ---
                # PDF

                Step one.
                """;

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(raw, SOURCE);

        assertThat(parsed.frontmatter().requiredString("name")).isEqualTo("pdf");
        assertThat(parsed.frontmatter().requiredString("description"))
                .isEqualTo("Fills in and generates PDF files.");
        assertThat(parsed.body()).isEqualTo("# PDF\n\nStep one.");
    }

    @Test
    void parsesYamlSequenceAsStringList() {
        String raw = """
                ---
                name: pdf
                allowed-tools:
                  - bash
                  - read
                ---
                Body.
                """;

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(raw, SOURCE);

        assertThat(parsed.frontmatter().stringList("allowed-tools"))
                .containsExactly("bash", "read");
    }

    @Test
    void parsesCommaSeparatedScalarAsStringList() {
        String raw = """
                ---
                name: pdf
                allowed-tools: bash, read
                ---
                Body.
                """;

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(raw, SOURCE);

        assertThat(parsed.frontmatter().stringList("allowed-tools"))
                .containsExactly("bash", "read");
    }

    @Test
    void stripsByteOrderMarkAndCarriageReturns() {
        String raw = "\uFEFF---\r\nname: pdf\r\n---\r\nBody line.\r\n";

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(raw, SOURCE);

        assertThat(parsed.frontmatter().requiredString("name")).isEqualTo("pdf");
        assertThat(parsed.body()).isEqualTo("Body line.");
    }

    @Test
    void keepsHorizontalRulesInsideBody() {
        String raw = """
                ---
                name: pdf
                ---
                Intro.

                ---

                Outro.
                """;

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(raw, SOURCE);

        assertThat(parsed.body()).isEqualTo("Intro.\n\n---\n\nOutro.");
    }

    @Test
    void allowsEmptyBody() {
        String raw = """
                ---
                name: pdf
                ---
                """;

        FrontmatterParser.ParsedSkillFile parsed = FrontmatterParser.parse(raw, SOURCE);

        assertThat(parsed.body()).isEmpty();
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> FrontmatterParser.parse(null, SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("file is empty");
    }

    @Test
    void rejectsBlankFile() {
        assertThatThrownBy(() -> FrontmatterParser.parse("   \n\n", SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("file is empty");
    }

    @Test
    void rejectsFileWithoutFrontmatter() {
        assertThatThrownBy(() -> FrontmatterParser.parse("# PDF\n\nNo frontmatter here.\n", SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("missing YAML frontmatter");
    }

    @Test
    void rejectsUnterminatedFrontmatter() {
        String raw = """
                ---
                name: pdf
                description: Never closed.
                """;

        assertThatThrownBy(() -> FrontmatterParser.parse(raw, SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("unterminated frontmatter");
    }

    @Test
    void rejectsEmptyFrontmatterBlock() {
        String raw = """
                ---
                ---
                Body.
                """;

        assertThatThrownBy(() -> FrontmatterParser.parse(raw, SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("frontmatter block is empty");
    }

    @Test
    void rejectsFrontmatterThatIsNotAMapping() {
        String raw = """
                ---
                - pdf
                - docx
                ---
                Body.
                """;

        assertThatThrownBy(() -> FrontmatterParser.parse(raw, SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("must be a key-value mapping");
    }

    @Test
    void rejectsInvalidYaml() {
        String raw = """
                ---
                name: "unclosed
                ---
                Body.
                """;

        assertThatThrownBy(() -> FrontmatterParser.parse(raw, SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("invalid YAML in frontmatter");
    }

    @Test
    void rejectsDuplicateKeys() {
        String raw = """
                ---
                name: pdf
                name: docx
                ---
                Body.
                """;

        assertThatThrownBy(() -> FrontmatterParser.parse(raw, SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("invalid YAML in frontmatter");
    }

    @Test
    void reportsSourcePathInMessage() {
        assertThatThrownBy(() -> FrontmatterParser.parse("no frontmatter", SOURCE))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining(SOURCE.toString())
                .extracting(e -> ((SkillParseException) e).path())
                .isEqualTo(SOURCE);
    }

    @Test
    void missingKeyLookupsAreEmptyRatherThanFailing() {
        String raw = """
                ---
                name: pdf
                description: ""
                ---
                Body.
                """;

        Frontmatter frontmatter = FrontmatterParser.parse(raw, SOURCE).frontmatter();

        assertThat(frontmatter.optionalString("description")).isEmpty();
        assertThat(frontmatter.stringList("allowed-tools")).isEqualTo(List.of());
        assertThatThrownBy(() -> frontmatter.requiredString("description"))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("missing required frontmatter key: description");
    }
}
