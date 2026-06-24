package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Responses API (POST /v1/responses): note "output" array instead of "choices".
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponsesResponse(
        String id,
        String object,
        String model,
        String status,
        List<OutputItem> output,
        Usage usage
) {

    // One entry in the "output" array. The "type" field discriminates:
    // "message" carries text in "content"; "function_call" carries a tool call.
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
