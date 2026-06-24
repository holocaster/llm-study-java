package br.com.llm.study._7b_OpenRouter;

import br.com.llm.study.dtos.ChatCompletionResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public class OpenRouter {

    private static final String OPENROUTER_APIKEY = Objects.requireNonNull(System.getenv("OPENROUTER_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).registerModule(new JavaTimeModule());

    static void main() {
        final String payload = """
            {
                "model": "deepseek/deepseek-v4-flash",
                "messages": [
                    {
                        "role": "user",
                        "content": "Tell me a joke"
                    }
                ]
            }
        """;

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Authorization", "Bearer " + OPENROUTER_APIKEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();


        System.out.println(payload);

        try {
            final HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
