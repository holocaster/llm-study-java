package br.com.llm.study.roadmap.harness;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface Tool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    boolean requiresApproval();

    String execute(JsonNode input) throws Exception;
}
