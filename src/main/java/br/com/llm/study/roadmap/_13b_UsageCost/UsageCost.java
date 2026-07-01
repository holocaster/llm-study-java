package br.com.llm.study.roadmap._13b_UsageCost;

import br.com.llm.study.roadmap.domain.TaskStore;
import br.com.llm.study.roadmap.domain.TaskStoreUtils;
import br.com.llm.study.roadmap.dtos.ChatCompletionRequest;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse.Usage;
import br.com.llm.study.roadmap.dtos.Message;
import br.com.llm.study.roadmap.dtos.ToolCall;
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

public class UsageCost {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final String MODEL = "gpt-4o-mini";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static final double INPUT_PER_MTOK = 0.15;
    private static final double CACHED_INPUT_PER_MTOK = 0.075;
    private static final double OUTPUT_PER_MTOK = 0.60;

    private static final TaskStore TASK_STORE = new TaskStore();

    public static void main(String[] args) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("You are a task assistant. Use the tools to fulfil the request."));
        messages.add(Message.user("Add two tasks: buy milk and call the dentist. "
                + "Then list all my tasks and tell me how many I have."));

        double runningCost = 0.0;
        int runningTokens = 0;

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            final int maxTurns = 10;
            for (int turn = 1; turn <= maxTurns; turn++) {
                ChatCompletionResponse response = call(httpClient, messages);
                ChatCompletionResponse.Choice choice = response.choices().get(0);
                messages.add(choice.message());

                double turnCost = costOf(response.usage());
                runningCost += turnCost;
                runningTokens += response.usage().totalTokens();
                printTurn(turn, response.usage(), turnCost, runningCost);

                if (!"tool_calls".equals(choice.finishReason())) {
                    System.out.println("\nFinal answer: " + choice.message().content());
                    break;
                }

                for (ToolCall toolCall : choice.message().toolCalls()) {
                    Object result = execute(toolCall);
                    messages.add(Message.toolResult(toolCall.id(), toJson(result)));
                }
            }
        }

        System.out.printf("%n===== run total: %d tokens, $%.6f =====%n", runningTokens, runningCost);
    }

    private static double costOf(Usage usage) {
        int cached = usage.promptTokensDetails() == null ? 0 : usage.promptTokensDetails().cachedTokens();
        int freshInput = usage.promptTokens() - cached;
        return freshInput * INPUT_PER_MTOK / 1_000_000
                + cached * CACHED_INPUT_PER_MTOK / 1_000_000
                + usage.completionTokens() * OUTPUT_PER_MTOK / 1_000_000;
    }

    private static void printTurn(int turn, Usage usage, double turnCost, double runningCost) {
        int cached = usage.promptTokensDetails() == null ? 0 : usage.promptTokensDetails().cachedTokens();
        System.out.printf(
                "turn %d: prompt=%d (cached=%d) completion=%d total=%d | turn=$%.6f running=$%.6f%n",
                turn, usage.promptTokens(), cached, usage.completionTokens(), usage.totalTokens(),
                turnCost, runningCost);
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

    private static ChatCompletionResponse call(HttpClient httpClient, List<Message> messages) {
        try {
            ChatCompletionRequest requestBody = new ChatCompletionRequest(
                    MODEL, messages, 0.7, null, null, null, TaskStoreUtils.tools());
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
}
