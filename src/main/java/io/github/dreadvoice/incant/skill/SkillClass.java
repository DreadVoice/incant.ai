package io.github.dreadvoice.incant.skill;

public enum SkillClass {

    INSTRUCTION("Instruction", "Loads into context. Runs anywhere."),

    DOCUMENT("Document", "Requires a sandbox with script execution."),

    CODING_AGENT("Coding agent", "Requires an active workspace with repo access.");

    private final String displayName;
    private final String requirement;

    SkillClass(String displayName, String requirement) {
        this.displayName = displayName;
        this.requirement = requirement;
    }

    public String displayName() {
        return displayName;
    }

    public String requirement() {
        return requirement;
    }

    public boolean requiresSandbox() {
        return this == DOCUMENT;
    }

    public boolean requiresWorkspace() {
        return this == CODING_AGENT;
    }

    public boolean isContextOnly() {
        return this == INSTRUCTION;
    }
}