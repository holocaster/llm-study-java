package br.com.llm.study.roadmap.harness;

import br.com.llm.study.roadmap.dtos.AnthropicRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(Tool... tools) {
        for (Tool tool : tools) {
            this.tools.put(tool.name(), tool);
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<AnthropicRequest.Tool> schemas() {
        return tools.values().stream()
                .map(t -> new AnthropicRequest.Tool(t.name(), t.description(), t.inputSchema()))
                .toList();
    }
}
