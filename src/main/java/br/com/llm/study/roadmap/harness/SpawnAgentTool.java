package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public class SpawnAgentTool implements Tool {

    private final AgentRunner runner;

    public SpawnAgentTool(AgentRunner runner) {
        this.runner = runner;
    }

    @Override public String name() { return "spawn_agent"; }

    @Override public String description() {
        return "Delegate a self-contained subtask to a fresh subagent. The subagent has the "
             + "file and shell tools but CANNOT spawn further agents, and it sees ONLY the task "
             + "string you provide - not this conversation. Include all needed context in the task. "
             + "Returns the subagent's final answer.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of("type", "string",
                                "description", "A complete, self-contained description of the subtask")),
                "required", List.of("task"));
    }

    @Override public boolean requiresApproval() { return false; }

    @Override public String execute(JsonNode input) {
        String task = input.path("task").asText();
        System.out.println("\n-- subagent start: " + task);
        try {
            String result = runner.run(task);
            System.out.println("-- subagent done --");
            return result;
        } catch (Exception e) {
            System.out.println("-- subagent failed --");
            return "ERROR: subagent failed: " + e.getMessage();
        }
    }
}
