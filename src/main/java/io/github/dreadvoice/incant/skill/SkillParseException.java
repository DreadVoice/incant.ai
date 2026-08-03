package io.github.dreadvoice.incant.skill;

import java.nio.file.Path;

public class SkillParseException extends RuntimeException {

    private final transient Path path;

    public SkillParseException(Path path, String message) {
        super(path + ": " + message);
        this.path = path;
    }

    public SkillParseException(Path path, String message, Throwable cause) {
        super(path + ": " + message, cause);
        this.path = path;
    }

    public Path path() {
        return path;
    }
}