package br.com.llm.study.roadmap.harness;

import br.com.llm.study.roadmap.dtos.AnthropicMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SessionStore {

    private final Path file;
    private final ObjectMapper mapper;

    public SessionStore(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    public List<AnthropicMessage> load() throws Exception {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        AnthropicMessage[] saved = mapper.readValue(Files.readString(file), AnthropicMessage[].class);
        return new ArrayList<>(List.of(saved));
    }

    public void save(List<AnthropicMessage> messages) throws Exception {
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages));
    }
}
