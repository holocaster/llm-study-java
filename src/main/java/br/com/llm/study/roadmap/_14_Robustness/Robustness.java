package br.com.llm.study.roadmap._14_Robustness;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Robustness {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final String URL = "https://api.openai.com/v1/chat/completions";

  private static final int MAX_RETRIES = 3;
  private static final Duration BASE_DELAY = Duration.ofMillis(500);
  private static final Duration MAX_DELAY = Duration.ofSeconds(10);

  private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);

  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  static void main() throws InterruptedException {
    String payload = """
        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}]}
        """;

    System.out.println("== scenario 1: healthy call (succeeds first try) ==");
    runScenario(buildRequest(OPENAI_API_KEY, payload, Duration.ofSeconds(30)));

    System.out.println("== scenario 2: 1ms timeout (retryable -> backoff -> give up) ==");
    runScenario(buildRequest(OPENAI_API_KEY, payload, Duration.ofMillis(1)));

    System.out.println("== scenario 3: bad key (401 non-retryable -> fail fast) ==");
    runScenario(buildRequest("sk-invalid-key", payload, Duration.ofSeconds(30)));
  }

  private static void runScenario(HttpRequest request) throws InterruptedException {
    try {
      HttpResponse<String> response = sendWithRetry(request);
      System.out.println("result: HTTP " + response.statusCode());
    } catch (IOException e) {
      System.out.println("result: failed after retries -> " + e.getClass().getSimpleName());
    }
    System.out.println();
  }

  static HttpResponse<String> sendWithRetry(HttpRequest request)
      throws IOException, InterruptedException {
    int attempt = 0;
    while (true) {
      try {
        HttpResponse<String> response = HTTP_CLIENT.send(
            request, HttpResponse.BodyHandlers.ofString());

        if (!RETRYABLE_STATUS.contains(response.statusCode())) {
          return response;
        }
        if (attempt >= MAX_RETRIES) {
          System.out.println("  gave up after " + (attempt + 1) + " attempts (HTTP "
              + response.statusCode() + ")");
          return response;
        }

        int currentAttempt = attempt;
        Duration delay = retryAfter(response).orElseGet(() -> backoff(currentAttempt));
        System.out.println("  attempt " + (attempt + 1) + " -> HTTP " + response.statusCode()
            + ", retrying in " + delay.toMillis() + "ms");
        Thread.sleep(delay.toMillis());
        attempt++;

      } catch (IOException e) {
        if (attempt >= MAX_RETRIES) {
          System.out.println("  gave up after " + (attempt + 1) + " attempts ("
              + e.getClass().getSimpleName() + ")");
          throw e;
        }
        Duration delay = backoff(attempt);
        System.out.println("  attempt " + (attempt + 1) + " -> " + e.getClass().getSimpleName()
            + ", retrying in " + delay.toMillis() + "ms");
        Thread.sleep(delay.toMillis());
        attempt++;
      }
    }
  }

  private static Duration backoff(int attempt) {
    long exponential = BASE_DELAY.toMillis() * (1L << attempt);
    long capped = Math.min(exponential, MAX_DELAY.toMillis());
    long jitter = ThreadLocalRandom.current().nextLong(capped / 2 + 1);
    return Duration.ofMillis(Math.min(capped + jitter, MAX_DELAY.toMillis()));
  }

  private static Optional<Duration> retryAfter(HttpResponse<?> response) {
    return response.headers().firstValue("retry-after")
        .filter(value -> !value.isBlank() && value.chars().allMatch(Character::isDigit))
        .map(value -> Duration.ofSeconds(Long.parseLong(value)));
  }

  private static HttpRequest buildRequest(String apiKey, String payload, Duration timeout) {
    return HttpRequest.newBuilder()
        .uri(URI.create(URL))
        .timeout(timeout)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
  }
}
