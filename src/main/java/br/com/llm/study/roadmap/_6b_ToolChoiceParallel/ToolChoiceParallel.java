package br.com.llm.study.roadmap._6b_ToolChoiceParallel;

import br.com.llm.study.roadmap.domain.TaskStore;
import br.com.llm.study.roadmap.domain.TaskStoreUtils;
import br.com.llm.study.roadmap.dtos.ChatCompletionRequest.Tool;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.Message;
import br.com.llm.study.roadmap.dtos.ToolCall;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

public class ToolChoiceParallel {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final String MODEL = "gpt-4o-mini";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static final TaskStore TASK_STORE = new TaskStore();

    public static void main(String[] args) {
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            partA(httpClient);
            partB(httpClient);
        }
    }

    private static void partA(HttpClient httpClient) {
        System.out.println("===== Part A: tool_choice =====");
        String prompt = "Please list all of my tasks.";

        showChoice(httpClient, "auto", prompt, "auto");

        showChoice(httpClient, "none", prompt, "none");

        Object forceAddTask = Map.of("type", "function", "function", Map.of("name", "add_task"));
        showChoice(httpClient, "force add_task", prompt, forceAddTask);
    }

    private static void showChoice(HttpClient httpClient, String label, String prompt, Object toolChoice) {
        List<Message> messages = List.of(Message.user(prompt));
        ChatCompletionResponse.Choice choice = call(httpClient, messages, toolChoice).choices().get(0);

        System.out.println("--- tool_choice=" + label + " ---");
        System.out.println("finish_reason=" + choice.finishReason());
        List<ToolCall> toolCalls = choice.message().toolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            System.out.println("no tool calls; content: " + choice.message().content());
        } else {
            for (ToolCall tc : toolCalls) {
                System.out.println("tool_call: " + tc.function().name() + " " + tc.function().arguments());
            }
        }
        System.out.println();
    }

    private static void partB(HttpClient httpClient) {
        System.out.println("===== Part B: parallel tool calls =====");
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("You are a task assistant. Use the provided tools to fulfil the request."));
        messages.add(Message.user("Add three separate tasks: buy milk, call the dentist, and pay the rent."));

        ChatCompletionResponse.Choice first = call(httpClient, messages, "auto").choices().get(0);
        messages.add(first.message());

        List<ToolCall> toolCalls = first.message().toolCalls();
        System.out.println("model returned " + (toolCalls == null ? 0 : toolCalls.size()) + " tool call(s) in one turn");

        if (toolCalls != null) {

            for (ToolCall toolCall : toolCalls) {
                Object result = execute(toolCall);
                messages.add(Message.toolResult(toolCall.id(), toJson(result)));
                System.out.println("executed " + toolCall.function().name() + " -> " + toJson(result));
            }
        }

        ChatCompletionResponse.Choice second = call(httpClient, messages, "auto").choices().get(0);
        System.out.println("\nFinal answer: " + second.message().content());
    }

    private static Object execute(ToolCall toolCall) {
        try {
            String name = toolCall.function().name();
            JsonNode args = OBJECT_MAPPER.readTree(toolCall.function().arguments());
            return switch (name) {
                case "add_task"      -> TASK_STORE.addTask(args.path("title").asText(), args.path("notes").asText(null));
                case "list_tasks"    -> TASK_STORE.listTasks();
                case "complete_task" -> TASK_STORE.completeTask(args.path("id").asInt());
                case "search_notes"  -> TASK_STORE.searchNotes(args.path("query").asText());
                default              -> Map.of("error", "unknown tool: " + name);
            };
        } catch (Exception e) {
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }

    private static ChatCompletionResponse call(HttpClient httpClient, List<Message> messages, Object toolChoice) {
        try {
            ToolChoiceRequest requestBody = new ToolChoiceRequest(MODEL, messages, TaskStoreUtils.tools(), toolChoice);
            String payload = OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("API error " + response.statusCode() + ": " + response.body());
            }
            return OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ToolChoiceRequest(
            String model,
            List<Message> messages,
            List<Tool> tools,
            @JsonProperty("tool_choice") Object toolChoice
    ) {
    }
}
