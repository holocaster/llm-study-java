package br.com.llm.study._8_ResponsesApi;

import br.com.llm.study.dtos.ResponsesResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

// #8 part 1: #1 Hello completion re-pointed at the Responses API.
// Same loop, newer API generation: "input" instead of "messages",
// "output" array instead of "choices".
public class HelloResponses {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    static void main() {
        // "input" can be a plain string here (vs. the "messages" array in #1).
        final String payload = """
            {
                "model": "gpt-4o-mini",
                "input": "Tell me a joke"
            }
        """;

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/responses"))
                .header("Authorization", "Bearer " + OPENAI_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        System.out.println(payload);

        try {
            final HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response body: " + response.body());

            if (response.statusCode() != 200) {
                System.out.println("API error " + response.statusCode() + ": " + response.body());
                return;
            }

            ResponsesResponse parsed = OBJECT_MAPPER.readValue(response.body(), ResponsesResponse.class);

            // The text lives in the "message" item, not necessarily output[0]
            // (a "reasoning" item can come first). There is no top-level
            // "output_text" in the raw API - that is an SDK convenience.
            String text = parsed.output().stream()
                    .filter(item -> "message".equals(item.type()))
                    .flatMap(item -> item.content().stream())
                    .filter(part -> "output_text".equals(part.type()))
                    .map(ResponsesResponse.ContentPart::text)
                    .findFirst()
                    .orElse("(no text output)");

            System.out.println("Assistant: " + text);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
