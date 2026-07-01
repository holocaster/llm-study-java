package br.com.llm.study.roadmap._2b_PromptEngineering;

import br.com.llm.study.roadmap.dtos.ChatCompletionRequest;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.Message;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PromptEngineering {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final String MODEL = "gpt-4o-mini";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static final String TASK =
            "A father is 3 times as old as his son. In 12 years, he will be twice as old as his son. "
            + "How old is the son now?";

    private static final String STRONG_SYSTEM =
            "You are a meticulous math tutor. You never guess: you set up variables and equations "
            + "explicitly before computing. You always verify your answer by substituting it back into "
            + "the original problem statement before presenting it. Your final line must be exactly: "
            + "Answer: <number>";

    public static void main(String[] args) {
        Map<String, List<Message>> variations = new LinkedHashMap<>();

        variations.put("naive", List.of(
                Message.user(TASK)
        ));

        variations.put("strong_system", List.of(
                Message.system(STRONG_SYSTEM),
                Message.user(TASK)
        ));

        variations.put("few_shot", List.of(
                Message.system(STRONG_SYSTEM),
                Message.user("A mother is 4 times as old as her daughter. In 8 years, she will be 3 times "
                        + "as old as her daughter. How old is the daughter now?"),
                Message.assistant("Let the daughter's current age be x, so the mother's is 4x.\n"
                        + "In 8 years: 4x + 8 = 3(x + 8)\n4x + 8 = 3x + 24\nx = 16\n"
                        + "Check: mother is 64, daughter is 16 (4x). In 8 years: 72 and 24, and 72 = 3*24. Correct.\n"
                        + "Answer: 16"),
                Message.user("A boy is twice as old as his sister. In 5 years, he will be 1.5 times as old "
                        + "as his sister. How old is the sister now?"),
                Message.assistant("Let the sister's current age be x, so the boy's is 2x.\n"
                        + "In 5 years: 2x + 5 = 1.5(x + 5)\n2x + 5 = 1.5x + 7.5\n0.5x = 2.5\nx = 5\n"
                        + "Check: boy is 10, sister is 5. In 5 years: 15 and 10, and 15 = 1.5*10. Correct.\n"
                        + "Answer: 5"),
                Message.user(TASK)
        ));

        variations.put("think_step_by_step", List.of(
                Message.system(STRONG_SYSTEM),
                Message.user(TASK + "\n\nThink step by step before giving your final answer.")
        ));

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            for (Map.Entry<String, List<Message>> variation : variations.entrySet()) {
                String output = call(httpClient, variation.getValue());
                System.out.println("=== " + variation.getKey() + " ===");
                System.out.println(output);
                System.out.println();
            }
        }
    }

    private static String call(HttpClient httpClient, List<Message> messages) {
        try {
            ChatCompletionRequest requestBody = ChatCompletionRequest.of(MODEL, messages);
            String payload = OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "API error " + response.statusCode() + ": " + response.body();
            }

            ChatCompletionResponse chatCompletionResponse =
                    OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class);
            return chatCompletionResponse.choices().get(0).message().content();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
