package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicMessage(
        String role,
        List<ContentBlock> content
) {

    public static AnthropicMessage user(String text) {
        return new AnthropicMessage("user", List.of(new ContentBlock.Text(text)));
    }

    public static AnthropicMessage assistant(List<ContentBlock> content) {
        return new AnthropicMessage("assistant", content);
    }

    public static AnthropicMessage toolResults(List<ContentBlock> results) {
        return new AnthropicMessage("user", results);
    }
}
