package br.com.llm.study.roadmap.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponsesResponse(
        String id,
        String object,
        String model,
        String status,
        List<OutputItem> output,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(
            String type,
            String id,
            String role,
            String status,
            List<ContentPart> content,
            @JsonProperty("call_id") String callId,
            String name,
            String arguments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentPart(
            String type,
            String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }
}
