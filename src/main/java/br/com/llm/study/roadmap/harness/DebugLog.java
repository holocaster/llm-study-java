package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Appends pretty-printed LLM request/response JSON to a file when HARNESS_DEBUG=1. No-op otherwise. */
public class DebugLog {

    private final boolean enabled;
    private final Path file;
    private final ObjectMapper mapper;

    public DebugLog(Path file, ObjectMapper mapper) {
        this.enabled = "1".equals(System.getenv("HARNESS_DEBUG"));
        this.file = file;
        this.mapper = mapper;
    }

    /** Append a timestamped "<label>\n<pretty json>" block. Never throws - a logging failure
        must not break the harness. */
    public void log(String label, String json) {
        if (!enabled) {
            return;
        }
        String entry = Instant.now() + " " + label + "\n" + prettify(json) + "\n";
        try {
            Files.writeString(file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("debug log write failed: " + e.getMessage());
        }
    }

    /** Re-serialize JSON with indentation; return the original string if it does not parse. */
    private String prettify(String json) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }
}
