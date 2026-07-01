package br.com.llm.study.roadmap._10_Tracing;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OpenAiTraceExporter {

    private static final String INGEST_ENDPOINT = "https://api.openai.com/v1/traces/ingest";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiTraceExporter() {
    }

    public static int export(Span root) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[trace export] OPENAI_API_KEY not set, skipping.");
            return -1;
        }

        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        List<Map<String, Object>> data = new ArrayList<>();

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("object", "trace");
        trace.put("id", traceId);
        trace.put("workflow_name", root.getName());
        trace.put("group_id", null);
        trace.put("metadata", null);
        data.add(trace);

        emit(root, traceId, null, data);

        try {
            String body = MAPPER.writeValueAsString(Map.of("data", data));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(INGEST_ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("OpenAI-Beta", "traces=v1")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                System.out.println("[trace export] error " + response.statusCode() + ": " + response.body());
            } else {
                System.out.println("[trace export] sent trace " + traceId + " (" + data.size() + " items)");
            }
            return response.statusCode();
        } catch (Exception e) {
            System.out.println("[trace export] failed: " + e.getMessage());
            return -1;
        }
    }

    private static void emit(Span span, String traceId, String parentSpanId, List<Map<String, Object>> data) {
        String spanId = "span_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("object", "trace.span");
        obj.put("id", spanId);
        obj.put("trace_id", traceId);
        obj.put("parent_id", parentSpanId);
        obj.put("started_at", span.startedAt() == null ? null : span.startedAt().toString());
        obj.put("ended_at", span.endedAt() == null ? null : span.endedAt().toString());
        obj.put("span_data", spanData(span));
        obj.put("error", null);
        data.add(obj);

        for (Span child : span.getChildren()) {
            emit(child, traceId, spanId, data);
        }
    }

    private static Map<String, Object> spanData(Span span) {
        Map<String, Object> sd = new LinkedHashMap<>();
        switch (span.getType()) {
            case "agent" -> {
                sd.put("type", "agent");
                sd.put("name", span.getName());
                sd.put("handoffs", List.of());
                sd.put("tools", List.of());
                sd.put("output_type", null);
            }
            case "llm", "responses", "generation" -> {

                String responseId = responseId(span);
                sd.put("type", "response");
                sd.put("response_id", responseId);
            }
            case "tool", "function" -> {
                sd.put("type", "function");
                sd.put("name", span.getName());
                sd.put("input", str(span.getInput()));
                sd.put("output", str(span.getOutput()));
            }
            case "handoff" -> sd.put("type", "handoff");
            default -> {
                sd.put("type", "custom");
                sd.put("name", span.getName());
                sd.put("data", Map.of());
            }
        }
        return sd;
    }

    private static String responseId(Span span) {
        if (span.getOutput() instanceof Map<?, ?> m && m.get("id") != null) {
            return m.get("id").toString();
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
