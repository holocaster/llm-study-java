package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool implements Tool {

    private final Workspace workspace;

    public ReadFileTool(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "read_file"; }

    @Override public String description() {
        return "Read the full contents of a text file at the given path.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "File path relative to the workspace")),
                "required", List.of("path"));
    }

    @Override public boolean requiresApproval() { return false; }

    @Override public String execute(JsonNode input) throws Exception {
        Path file = workspace.resolve(input.path("path").asText());
        return Files.readString(file);
    }
}
