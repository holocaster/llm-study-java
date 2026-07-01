package br.com.llm.study.roadmap._16_Evals;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Evals {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .registerModule(new JavaTimeModule());
  private static final String MODEL = "gpt-4o-mini";
  private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  enum Scorer { KEYWORD, JUDGE }

  record Case(String question, String expected, Scorer scorer) {}

  record Verdict(boolean pass, String reason) {}

  private static final List<Case> DATASET = List.of(
      new Case("What is the capital of France?", "Paris", Scorer.KEYWORD),
      new Case("What is 2 + 2? Reply with just the number.", "4", Scorer.KEYWORD),
      new Case("Name the language that runs on the JVM and is named after an island.",
          "Java", Scorer.KEYWORD),
      new Case("Explain in one sentence why the sky is blue.",
          "Sunlight scatters off air molecules and shorter blue wavelengths scatter "
              + "more, so the sky looks blue.",
          Scorer.JUDGE),
      new Case("Summarize in one sentence what a large language model is.",
          "A large language model is a neural network trained on large amounts of text "
              + "to predict and generate language.",
          Scorer.JUDGE));

  static void main() throws IOException, InterruptedException {
    int passed = 0;
    for (Case testCase : DATASET) {
      String answer = answer(testCase.question());
      boolean ok = switch (testCase.scorer()) {
        case KEYWORD -> scoreKeyword(answer, testCase.expected());
        case JUDGE -> scoreJudge(testCase.question(), testCase.expected(), answer);
      };
      if (ok) {
        passed++;
      }
      System.out.printf("[%s] %-7s Q: %s%n         A: %s%n%n",
          ok ? "PASS" : "FAIL", testCase.scorer(), testCase.question(), answer);
    }
    System.out.printf("Score: %d/%d passed%n", passed, DATASET.size());
  }

  private static String answer(String question) throws IOException, InterruptedException {
    List<Message> messages = List.of(
        Message.system("Answer concisely."),
        Message.user(question));
    ChatCompletionResponse response = chat(
        new ChatCompletionRequest(MODEL, messages, 0.0, null, null, null));
    return response.choices().get(0).message().content().trim();
  }

  private static boolean scoreKeyword(String answer, String expected) {
    return answer.toLowerCase().contains(expected.toLowerCase());
  }

  private static boolean scoreJudge(String question, String reference, String candidate)
      throws IOException, InterruptedException {
    String prompt = """
        You are grading an answer. Set pass=true only if the candidate answer is
        factually consistent with the reference and actually answers the question.
        Ignore differences in wording and length.

        Question: %s
        Reference answer: %s
        Candidate answer: %s
        """.formatted(question, reference, candidate);

    var schema = Map.of(
        "type", "object",
        "properties", Map.of(
            "pass", Map.of("type", "boolean"),
            "reason", Map.of("type", "string")),
        "required", List.of("pass", "reason"),
        "additionalProperties", false);
    var responseFormat = ChatCompletionRequest.ResponseFormat.jsonSchema(
        new ChatCompletionRequest.JsonSchema("verdict", true, schema));

    ChatCompletionResponse response = chat(new ChatCompletionRequest(
        MODEL, List.of(Message.user(prompt)), 0.0, null, null, responseFormat));
    Verdict verdict = OBJECT_MAPPER.readValue(
        response.choices().get(0).message().content(), Verdict.class);
    System.out.println("         judge: " + verdict.reason());
    return verdict.pass();
  }

  private static ChatCompletionResponse chat(ChatCompletionRequest request)
      throws IOException, InterruptedException {
    String payload = OBJECT_MAPPER.writeValueAsString(request);
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(ENDPOINT))
        .header("Authorization", "Bearer " + OPENAI_API_KEY)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(
        httpRequest, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("API error " + response.statusCode() + ": " + response.body());
    }
    return OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class);
  }
}
