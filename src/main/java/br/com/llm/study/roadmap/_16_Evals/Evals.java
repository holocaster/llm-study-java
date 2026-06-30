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

/**
 * Evals, by hand. An eval is the test suite for the non-deterministic part of an LLM
 * app: a dataset of cases, a scorer, and an aggregate score you can compare before and
 * after a change. Two scorer kinds -- KEYWORD (deterministic) and JUDGE (a second LLM
 * call grading open-ended answers, reusing the structured-output pattern from #4).
 *
 * The "system under test" here is a plain completion (see answer()). Change the model
 * or the prompt, re-run, and watch the score move -- that is the whole point.
 */
public class Evals {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .registerModule(new JavaTimeModule());
  private static final String MODEL = "gpt-4o-mini";
  private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  // KEYWORD: the answer must contain an expected substring -- cheap and objective.
  // JUDGE: an LLM grades the answer against a reference -- for open-ended answers.
  enum Scorer { KEYWORD, JUDGE }

  // One test case. For KEYWORD, `expected` is a required substring; for JUDGE, it is
  // the reference answer. In a real project this dataset lives in a JSON/CSV file;
  // kept inline here so the exercise is self-contained and runnable as-is.
  record Case(String question, String expected, Scorer scorer) {}

  // What the judge returns -- structured output, same shape pattern as exercise #4.
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
      String answer = answer(testCase.question()); // the system under test
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

  /** The system being evaluated: a plain completion. Swap the model/prompt and re-run. */
  private static String answer(String question) throws IOException, InterruptedException {
    List<Message> messages = List.of(
        Message.system("Answer concisely."),
        Message.user(question));
    ChatCompletionResponse response = chat(
        new ChatCompletionRequest(MODEL, messages, 0.0, null, null, null));
    return response.choices().get(0).message().content().trim();
  }

  /** Deterministic scorer: case-insensitive substring match. */
  private static boolean scoreKeyword(String answer, String expected) {
    return answer.toLowerCase().contains(expected.toLowerCase());
  }

  /**
   * LLM-as-judge: a second model call grades the candidate against the reference and
   * returns structured {pass, reason}. Temperature 0 for consistent grading.
   */
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

  /** One chat completion round-trip. Shared by the system under test and the judge. */
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
