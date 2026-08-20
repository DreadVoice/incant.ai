package io.github.dreadvoice.incant.agent;

import java.util.Map;

public interface ToolHandler {

    String name();

    String execute(Map<String, Object> arguments);
}
