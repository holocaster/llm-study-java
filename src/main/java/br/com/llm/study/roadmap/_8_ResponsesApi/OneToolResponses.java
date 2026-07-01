package br.com.llm.study.roadmap._8_ResponsesApi;

import br.com.llm.study.roadmap.domain.TaskStore;
import br.com.llm.study.roadmap.dtos.ResponsesResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OneToolResponses {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static final TaskStore TASK_STORE = new TaskStore();

    static void main() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();

        List<Object> input = new ArrayList<>();
        input.add(Map.of("role", "user",
                "content", "Add a task to call the dentist tomorrow, then list my tasks."));

        boolean done = false;
        while (!done) {
            Map<String, Object> body = Map.of(
                    "model", "gpt-4o-mini",
                    "input", input,
                    "tools", responsesTools());

            String payload = OBJECT_MAPPER.writeValueAsString(body);
            System.out.println("Payload: " + payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response body: " + response.body());

            if (response.statusCode() != 200) {
                System.out.println("API error " + response.statusCode() + ": " + response.body());
                break;
            }

            ResponsesResponse parsed = OBJECT_MAPPER.readValue(response.body(), ResponsesResponse.class);

            List<ResponsesResponse.OutputItem> functionCalls = parsed.output().stream()
                    .filter(item -> "function_call".equals(item.type()))
                    .toList();

            if (functionCalls.isEmpty()) {

                String text = parsed.output().stream()
                        .filter(item -> "message".equals(item.type()))
                        .flatMap(item -> item.content().stream())
                        .filter(part -> "output_text".equals(part.type()))
                        .map(ResponsesResponse.ContentPart::text)
                        .findFirst()
                        .orElse("(no text output)");
                System.out.println("Assistant: " + text);
                done = true;
                continue;
            }

            for (ResponsesResponse.OutputItem call : functionCalls) {
                System.out.println("Tool call: " + call.name() + " " + call.arguments());

                input.add(Map.of(
                        "type", "function_call",
                        "id", call.id(),
                        "call_id", call.callId(),
                        "name", call.name(),
                        "arguments", call.arguments()));

                JsonNode args = OBJECT_MAPPER.readTree(call.arguments());
                Object result;
                try {
                    result = switch (call.name()) {
                        case "add_task"      -> TASK_STORE.addTask(args.path("title").asText(), args.path("notes").asText(null));
                        case "list_tasks"    -> TASK_STORE.listTasks();
                        case "complete_task" -> TASK_STORE.completeTask(args.path("id").asInt());
                        case "search_notes"  -> TASK_STORE.searchNotes(args.path("query").asText());
                        default              -> Map.of("error", "unknown tool: " + call.name());
                    };
                } catch (Exception e) {
                    result = Map.of("error", e.getMessage());
                }

                input.add(Map.of(
                        "type", "function_call_output",
                        "call_id", call.callId(),
                        "output", OBJECT_MAPPER.writeValueAsString(result)));
            }
        }

        httpClient.close();
    }

    private static List<Map<String, Object>> responsesTools() {
        return List.of(
                tool("add_task", "Add a new task to the user's task list", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "Short task title"),
                                "notes", Map.of("type", "string", "description", "Optional extra details")),
                        "required", List.of("title"),
                        "additionalProperties", false)),
                tool("list_tasks", "List every task with its id, title and done status", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false)),
                tool("complete_task", "Mark the task with the given id as done", Map.of(
                        "type", "object",
                        "properties", Map.of("id", Map.of("type", "integer", "description", "Id of the task to complete")),
                        "required", List.of("id"),
                        "additionalProperties", false)),
                tool("search_notes", "Find tasks whose notes contain the given text", Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "Text to search for in task notes")),
                        "required", List.of("query"),
                        "additionalProperties", false)));
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "name", name, "description", description, "parameters", parameters);
    }
}
