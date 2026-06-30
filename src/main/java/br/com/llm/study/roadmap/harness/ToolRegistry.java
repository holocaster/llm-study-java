package br.com.llm.study.roadmap.harness;

import br.com.llm.study.roadmap.dtos.AnthropicRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds the available tools, exposes their schemas, and dispatches by name. */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(Tool... tools) {
        for (Tool tool : tools) {
            this.tools.put(tool.name(), tool);
        }
    }

    /** Returns the tool for this name, or null if none is registered. */
    public Tool get(String name) {
        return tools.get(name);
    }

    /** Build the tool schema list sent to the model each turn. */
    public List<AnthropicRequest.Tool> schemas() {
        return tools.values().stream()
                .map(t -> new AnthropicRequest.Tool(t.name(), t.description(), t.inputSchema()))
                .toList();
    }
}
