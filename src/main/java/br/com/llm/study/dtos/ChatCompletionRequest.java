package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        Double temperature,
        @JsonProperty("max_completion_tokens") Integer maxTokens,
        Boolean stream,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        List<Tool> tools
) {

    public ChatCompletionRequest(String model, List<Message> messages, Double temperature,
                                 Integer maxTokens, Boolean stream, ResponseFormat responseFormat) {
        this(model, messages, temperature, maxTokens, stream, responseFormat, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            String type,
            Function function
    ) {
        public static Tool function(String name, String description, Map<String, Object> parameters) {
            return new Tool("function", new Function(name, description, parameters));
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Function(
                String name,
                String description,
                Map<String, Object> parameters
        ) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseFormat(
            String type,
            @JsonProperty("json_schema") JsonSchema jsonSchema
    ) {
        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object", null);
        }

        public static ResponseFormat jsonSchema(JsonSchema schema) {
            return new ResponseFormat("json_schema", schema);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonSchema(
            String name,
            Boolean strict,
            Map<String, Object> schema
    ) {
    }

    public static ChatCompletionRequest of(String model, List<Message> messages) {
        return new ChatCompletionRequest(model, messages, null, null, null, null);
    }
}
