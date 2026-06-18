package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {

    public Message(String role, String content) {
        this(role, content, null, null);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public static Message assistantToolCalls(List<ToolCall> toolCalls) {
        return new Message("assistant", null, toolCalls, null);
    }

    public static Message toolResult(String toolCallId, String content) {
        return new Message("tool", content, null, toolCallId);
    }
}
