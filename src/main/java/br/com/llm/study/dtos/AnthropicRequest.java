package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnthropicRequest(
        String model,
        @JsonProperty("max_tokens") Integer maxTokens,
        String system,
        List<AnthropicMessage> messages,
        List<Tool> tools
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema
    ) {
    }
}
