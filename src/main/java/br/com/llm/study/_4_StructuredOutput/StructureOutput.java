package br.com.llm.study._4_StructuredOutput;

import br.com.llm.study.domain.TaskStoreUtils;
import br.com.llm.study.dtos.ChatCompletionRequest;
import br.com.llm.study.dtos.ChatCompletionResponse;
import br.com.llm.study.dtos.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class StructureOutput {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).registerModule(new JavaTimeModule());

    static void main() throws JsonProcessingException {

        Message systemMessage = new Message("system", "You are a helpful manager assistant. Ask 1 or 2 questions to know what the customer wants. Always be polite ");
        Message userMessage = new  Message("user", "Can you help me organize my day");
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

        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);

        HttpClient httpClient = HttpClient.newHttpClient();
        Scanner scanner = new Scanner(System.in);

        boolean isOver = false;
        while (!isOver) {
            try {
                ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(
                        "gpt-4o-mini", messages, null, null, null, responseFormat, TaskStoreUtils.tools());
                final String payload = OBJECT_MAPPER.writeValueAsString(chatCompletionRequest);

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

                ChatCompletionResponse chatCompletionResponse = OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class);
                UserInput userInput = OBJECT_MAPPER.readValue(chatCompletionResponse.choices().get(0).message().content(), UserInput.class);

                System.out.println("Assistant: " + userInput.content());

                messages.add(new Message("assistant", userInput.content()));

                isOver = !userInput.need_user_input();

                if (!isOver) {
                    System.out.print("You: ");
                    String reply = scanner.nextLine();
                    messages.add(new Message("user", reply));
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
