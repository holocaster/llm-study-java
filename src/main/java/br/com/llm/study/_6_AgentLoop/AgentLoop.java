package br.com.llm.study._6_AgentLoop;

import br.com.llm.study.domain.TaskStore;
import br.com.llm.study.domain.TaskStoreUtils;
import br.com.llm.study.dtos.ChatCompletionRequest;
import br.com.llm.study.dtos.ChatCompletionResponse;
import br.com.llm.study.dtos.Message;
import br.com.llm.study.dtos.ToolCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class AgentLoop {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule()).setDefaultPrettyPrinter(new DefaultPrettyPrinter());

    private static final TaskStore TASK_STORE = new TaskStore();

    static {
        TASK_STORE.addTask("teste 1", "note2");
        TASK_STORE.addTask("teste 2", "note2");
    }

    static void main() {

        Message systemMessage = new Message("system", "You are a helpful manager assistant. Ask 1 or 2 questions to know what the customer wants. Always be polite ");
        Message userMessage = new  Message("user", "Help me add a schedule doctor at 13pm and meeting at 17pm for today.");
        var schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "need_user_input", Map.of("type", "boolean"),
                        "content",   Map.of("type", "string")
                ),
                "required", List.of("need_user_input", "content"),
                "additionalProperties", false
        );
        var responseFormat = ChatCompletionRequest.ResponseFormat.jsonSchema(
                new ChatCompletionRequest.JsonSchema("question", true, schema));

        // We own the history: this list grows every turn.
        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);

        HttpClient httpClient = HttpClient.newHttpClient();
        Scanner scanner = new Scanner(System.in);

        final int maxTurns = 10;
        int turns = 0;

        boolean isOver = false;
        while (!isOver) {
            // Safety cap: stop a misbehaving model from calling tools forever.
            if (++turns > maxTurns) {
                System.out.println("Reached max turns (" + maxTurns + "), stopping.");
                break;
            }
            try {
                // Rebuild the request each turn from the current history.
                ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(
                        "gpt-4o-mini", messages, 0.7, null, null, responseFormat, TaskStoreUtils.tools());
                final String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(chatCompletionRequest);

                System.out.println("Payload: " + payload);
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                        .header("Authorization", "Bearer " + OPENAI_API_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.out.println("API error " + response.statusCode() + ": " + response.body());
                    break;
                }

                System.out.println("API response: " + response.body());

                ChatCompletionResponse chatCompletionResponse = OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class);
                ChatCompletionResponse.Choice choice = chatCompletionResponse.choices().get(0);

                System.out.println("Assistant: " + choice.message());

                messages.add(choice.message());

                if("tool_calls".equals(choice.finishReason())) {
                    for (ToolCall toolCall : choice.message().toolCalls()) {
                        String name = toolCall.function().name();
                        JsonNode args = OBJECT_MAPPER.readTree(toolCall.function().arguments());

                        Object result;
                        try {
                            result = switch (name) {
                                case "add_task"      -> TASK_STORE.addTask(args.path("title").asText(),
                                        args.path("notes").asText(null));
                                case "list_tasks"    -> TASK_STORE.listTasks();
                                case "complete_task" -> TASK_STORE.completeTask(args.path("id").asInt());
                                case "search_notes"  -> TASK_STORE.searchNotes(args.path("query").asText());
                                default              -> Map.of("error", "unknown tool: " + name);
                            };
                        } catch (Exception e) {
                            result = Map.of("error", e.getMessage());
                        }

                        String resultJson = OBJECT_MAPPER.writeValueAsString(result);
                        messages.add(Message.toolResult(toolCall.id(), resultJson));
                    }
                } else {
                    UserInput userInput = OBJECT_MAPPER.readValue(choice.message().content(), UserInput.class);
                    isOver = !userInput.need_user_input();
                    if (!isOver) {
                        System.out.print("You: ");
                        String reply = scanner.nextLine();
                        messages.add(new Message("user", reply));
                    }
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        scanner.close();
        httpClient.close();
    }

    public record UserInput (boolean need_user_input, String content){}

}
