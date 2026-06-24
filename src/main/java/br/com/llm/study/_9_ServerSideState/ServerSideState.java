package br.com.llm.study._9_ServerSideState;

import br.com.llm.study.domain.TaskStore;
import br.com.llm.study.dtos.Message;
import br.com.llm.study.dtos.ResponsesResponse;
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


public class ServerSideState {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static final TaskStore TASK_STORE = new TaskStore();

    static void main() throws IOException, InterruptedException {

        HttpClient httpClient = HttpClient.newHttpClient();
        List<Message> input = new ArrayList<>();
        input.add(new Message("user", "I want to buy a gpu"));

        Scanner scanner = new Scanner(System.in);

        Map<String, Object> body = new HashMap<>(Map.of(
                "model", "gpt-4o-mini",
                "input", input,
                "instructions", "You are a helpful assistant for tech store. Ask 1 question"));
//                "tools", responsesTools());

        String previousId = null;
        boolean done = false;
        while (!done) {

            if (previousId != null) {
                body.put("previous_response_id", previousId);
            }
            String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
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

            String text = parsed.output().stream()
                    .filter(item -> "message".equals(item.type()))
                    .flatMap(item -> item.content().stream())
                    .filter(part -> "output_text".equals(part.type()))
                    .map(ResponsesResponse.ContentPart::text)
                    .findFirst()
                    .orElse("(no text output)");
            System.out.println("Assistant: " + text);
            System.out.print("You (type 'exit' to quit): ");
            String reply = scanner.nextLine();
            if (reply == null || reply.equalsIgnoreCase("exit")) {
                done = true;
            } else {
                input.clear();
                input.add(new Message("user", reply));
                previousId = parsed.id();
            }
        }

        httpClient.close();
    }
}
