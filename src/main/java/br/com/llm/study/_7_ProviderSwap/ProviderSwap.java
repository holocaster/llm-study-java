package br.com.llm.study._7_ProviderSwap;

import br.com.llm.study.domain.TaskStore;
import br.com.llm.study.dtos.AnthropicMessage;
import br.com.llm.study.dtos.AnthropicRequest;
import br.com.llm.study.dtos.AnthropicResponse;
import br.com.llm.study.dtos.ContentBlock;
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

public class ProviderSwap {

    private static final String ANTHROPIC_API_KEY = Objects.requireNonNull(System.getenv("ANTHROPIC_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule()).setDefaultPrettyPrinter(new DefaultPrettyPrinter());

    private static final String MODEL = "claude-haiku-4-5";
    private static final String SYSTEM_PROMPT =
            "You are a helpful manager assistant. Ask 1 or 2 questions to know what the customer wants. Always be polite ";

    private static final TaskStore TASK_STORE = new TaskStore();

    static {
        TASK_STORE.addTask("teste 1", "note2");
        TASK_STORE.addTask("teste 2", "note2");
    }

    static void main() {

        List<AnthropicMessage> messages = new ArrayList<>();
        messages.add(AnthropicMessage.user("Help me add a schedule doctor at 13pm and meeting at 17pm for today."));

        HttpClient httpClient = HttpClient.newHttpClient();
        Scanner scanner = new Scanner(System.in);

        final int maxTurns = 10;
        int turns = 0;

        boolean isOver = false;
        while (!isOver) {
            if (++turns > maxTurns) {
                System.out.println("Reached max turns (" + maxTurns + "), stopping.");
                break;
            }
            try {
                AnthropicRequest body = new AnthropicRequest(MODEL, 1024, SYSTEM_PROMPT, messages, anthropicTools());
                final String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);

                System.out.println("Payload: " + payload);
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.anthropic.com/v1/messages"))
                        .header("x-api-key", ANTHROPIC_API_KEY)
                        .header("anthropic-version", "2023-06-01")
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.out.println("API error " + response.statusCode() + ": " + response.body());
                    break;
                }

                System.out.println("API response: " + response.body());

                AnthropicResponse chatResponse = OBJECT_MAPPER.readValue(response.body(), AnthropicResponse.class);
                String stopReason = chatResponse.stopReason();
                List<ContentBlock> content = chatResponse.content();

                messages.add(AnthropicMessage.assistant(content));

                for (ContentBlock block : content) {
                    if (block instanceof ContentBlock.Text text) {
                        System.out.println("Assistant: " + text.text());
                    }
                }

                if ("tool_use".equals(stopReason)) {
                    List<ContentBlock> toolResults = new ArrayList<>();
                    for (ContentBlock block : content) {
                        if (!(block instanceof ContentBlock.ToolUse call)) {
                            continue;
                        }
                        JsonNode args = call.input();

                        Object result;
                        try {
                            result = switch (call.name()) {
                                case "add_task"      -> TASK_STORE.addTask(args.path("title").asText(),
                                        args.path("notes").asText(null));
                                case "list_tasks"    -> TASK_STORE.listTasks();
                                case "complete_task" -> TASK_STORE.completeTask(args.path("id").asInt());
                                case "search_notes"  -> TASK_STORE.searchNotes(args.path("query").asText());
                                default              -> Map.of("error", "unknown tool: " + call.name());
                            };
                        } catch (Exception e) {
                            result = Map.of("error", e.getMessage());
                        }

                        String resultJson = OBJECT_MAPPER.writeValueAsString(result);
                        toolResults.add(new ContentBlock.ToolResult(call.id(), resultJson));
                    }
                    messages.add(AnthropicMessage.toolResults(toolResults));
                } else if ("max_tokens".equals(stopReason)) {
                    System.out.println("[hit max_tokens - requesting continuation]");
                    messages.add(AnthropicMessage.user(
                            "Continue exactly from where you left off. Do not repeat text you already wrote."));
                } else {
                    System.out.print("You (type 'exit' to quit): ");
                    String reply = scanner.nextLine();
                    if (reply == null || reply.equalsIgnoreCase("exit")) {
                        isOver = true;
                    } else {
                        messages.add(AnthropicMessage.user(reply));
                    }
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        scanner.close();
        httpClient.close();
    }

    private static List<AnthropicRequest.Tool> anthropicTools() {
        return List.of(
                new AnthropicRequest.Tool("add_task", "Add a new task to the user's task list", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "Short task title"),
                                "notes", Map.of("type", "string", "description", "Optional extra details")
                        ),
                        "required", List.of("title")
                )),
                new AnthropicRequest.Tool("list_tasks", "List every task with its id, title and done status", Map.of(
                        "type", "object",
                        "properties", Map.of()
                )),
                new AnthropicRequest.Tool("complete_task", "Mark the task with the given id as done", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of("type", "integer", "description", "Id of the task to complete")
                        ),
                        "required", List.of("id")
                )),
                new AnthropicRequest.Tool("search_notes", "Find tasks whose notes contain the given text", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Text to search for in task notes")
                        ),
                        "required", List.of("query")
                ))
        );
    }

}
