package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** One capability the model can call. Maps 1:1 to an Anthropic tool schema. */
public interface Tool {

    String name();

    String description();

    /** JSON Schema object that becomes AnthropicRequest.Tool.input_schema. */
    Map<String, Object> inputSchema();

    /** When true, the harness asks the user before executing this tool. */
    boolean requiresApproval();

    /** Run the tool and return the text that becomes the tool_result content. */
    String execute(JsonNode input) throws Exception;
}
