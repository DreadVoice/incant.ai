package io.github.dreadvoice.incant.skill;

import java.nio.file.Path;
import java.util.List;

public interface SkillSource {

    String name();

    List<Path> discover();
}
