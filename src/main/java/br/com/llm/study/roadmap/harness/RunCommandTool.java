package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class RunCommandTool implements Tool {

    private final Workspace workspace;

    public RunCommandTool(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "run_command"; }

    @Override public String description() {
        return "Run a shell command in the workspace and return its combined output and exit code.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "Shell command run via sh -c")),
                "required", List.of("command"));
    }

    @Override public boolean requiresApproval() { return true; }

    @Override public String execute(JsonNode input) throws Exception {
        String command = input.path("command").asText();
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.directory(workspace.dir().toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return "exit code: " + exit + "\n" + output;
    }
}
