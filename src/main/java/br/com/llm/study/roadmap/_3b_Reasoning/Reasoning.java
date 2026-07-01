package br.com.llm.study.roadmap._3b_Reasoning;

import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;

public class Reasoning {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final String MODEL = "gpt-5.1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static final String TASK =
            "You have 25 horses and a racetrack. In one race, you can run at most 5 horses at a "
            + "time, and the race only tells you their finishing order, not their exact times. "
            + "There is no other timing device. What is the minimum number of races needed to "
            + "determine the 3 fastest horses overall, and what is the race plan? "
            + "Give the number, then justify it precisely.";

    private static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high");

    public static void main(String[] args) {
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            for (String effort : EFFORT_LEVELS) {
                long start = System.nanoTime();
                ChatCompletionResponse response = call(httpClient, effort);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                var usage = response.usage();
                int reasoningTokens = usage.completionTokensDetails() == null
                        ? 0
                        : usage.completionTokensDetails().reasoningTokens();

                System.out.println("=== reasoning_effort: " + effort + " ===");
                System.out.println("latency_ms=" + elapsedMs
                        + " reasoning_tokens=" + reasoningTokens
                        + " completion_tokens=" + usage.completionTokens()
                        + " total_tokens=" + usage.totalTokens());
                System.out.println(response.choices().get(0).message().content());
                System.out.println();
            }
        }
    }

    private static ChatCompletionResponse call(HttpClient httpClient, String reasoningEffort) {
        try {
            ReasoningRequest requestBody = new ReasoningRequest(MODEL, List.of(Message.user(TASK)), reasoningEffort);
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ReasoningRequest(
            String model,
            List<Message> messages,
            @JsonProperty("reasoning_effort") String reasoningEffort
    ) {
    }
}
