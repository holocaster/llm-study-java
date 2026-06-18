package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentBlock.Text.class, name = "text"),
        @JsonSubTypes.Type(value = ContentBlock.ToolUse.class, name = "tool_use"),
        @JsonSubTypes.Type(value = ContentBlock.ToolResult.class, name = "tool_result")
})
public sealed interface ContentBlock
        permits ContentBlock.Text, ContentBlock.ToolUse, ContentBlock.ToolResult {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Text(String text) implements ContentBlock {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolUse(String id, String name, JsonNode input) implements ContentBlock {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ToolResult(
            @JsonProperty("tool_use_id") String toolUseId,
            String content
    ) implements ContentBlock {
    }
}
