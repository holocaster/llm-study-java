package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class WriteFileTool implements Tool {

    private final Workspace workspace;

    public WriteFileTool(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "write_file"; }

    @Override public String description() {
        return "Write text to a file, creating parent directories and overwriting if it exists.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "File path relative to the workspace"),
                        "content", Map.of("type", "string", "description", "Text to write")),
                "required", List.of("path", "content"));
    }

    @Override public boolean requiresApproval() { return true; }

    @Override public String execute(JsonNode input) throws Exception {
        Path file = workspace.resolve(input.path("path").asText());
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        String content = input.path("content").asText();
        Files.writeString(file, content);
        return "Wrote " + content.length() + " chars to " + file;
    }
}
