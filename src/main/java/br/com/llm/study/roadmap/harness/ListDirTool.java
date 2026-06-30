package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListDirTool implements Tool {

    private final Workspace workspace;

    public ListDirTool(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override public String name() { return "list_dir"; }

    @Override public String description() {
        return "List the entries (files and subdirectories) of a directory.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Directory path relative to the workspace")),
                "required", List.of("path"));
    }

    @Override public boolean requiresApproval() { return false; }

    @Override public String execute(JsonNode input) throws Exception {
        Path dir = workspace.resolve(input.path("path").asText());
        try (Stream<Path> entries = Files.list(dir)) {
            String listing = entries
                    .map(p -> Files.isDirectory(p) ? p.getFileName() + "/" : p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.joining("\n"));
            return listing.isEmpty() ? "(empty directory)" : listing;
        }
    }
}
