package br.com.llm.study.roadmap._14b_PromptCaching;

import br.com.llm.study.roadmap.dtos.AnthropicMessage;
import br.com.llm.study.roadmap.dtos.ContentBlock;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

public class PromptCaching {

    private static final String ANTHROPIC_API_KEY = Objects.requireNonNull(System.getenv("ANTHROPIC_API_KEY"));
    private static final String MODEL = "claude-haiku-4-5";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) {

        String longSystemPrompt = buildLongSystemPrompt();

        List<SystemBlock> system = List.of(SystemBlock.cached(longSystemPrompt));
        List<AnthropicMessage> messages = List.of(
                AnthropicMessage.user("In one sentence, what is your job?"));

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            System.out.println("first call (expect cache write):");
            callAndReport(httpClient, system, messages);

            System.out.println("\nsecond call (expect cache read, lower latency):");
            callAndReport(httpClient, system, messages);
        }
    }

    private static void callAndReport(HttpClient httpClient, List<SystemBlock> system, List<AnthropicMessage> messages) {
        long start = System.nanoTime();
        CacheResponse response = call(httpClient, system, messages);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        CacheUsage usage = response.usage();
        System.out.printf(
                "latency_ms=%d input=%d cache_write=%d cache_read=%d output=%d%n",
                elapsedMs, usage.inputTokens(), usage.cacheCreationInputTokens(),
                usage.cacheReadInputTokens(), usage.outputTokens());
    }

    private static CacheResponse call(HttpClient httpClient, List<SystemBlock> system, List<AnthropicMessage> messages) {
        try {
            CacheRequest requestBody = new CacheRequest(MODEL, 128, system, messages);
            String payload = OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", ANTHROPIC_API_KEY)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("API error " + response.statusCode() + ": " + response.body());
            }
            return OBJECT_MAPPER.readValue(response.body(), CacheResponse.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String buildLongSystemPrompt() {
        String paragraph =
                "You are TaskBot, a meticulous personal task and notes assistant. You help the user "
                + "capture, organize, and complete tasks. You are concise, never invent tasks the user "
                + "did not mention, and you always confirm before deleting anything. You track due dates, "
                + "priorities, and notes, and you surface the most urgent item first when asked. ";
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < 60; i++) {
            builder.append(paragraph);
        }
        return builder.toString();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CacheRequest(
            String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            List<SystemBlock> system,
            List<AnthropicMessage> messages
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SystemBlock(
            String type,
            String text,
            @JsonProperty("cache_control") CacheControl cacheControl
    ) {
        static SystemBlock cached(String text) {
            return new SystemBlock("text", text, new CacheControl("ephemeral"));
        }
    }

    private record CacheControl(String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CacheResponse(
            List<ContentBlock> content,
            CacheUsage usage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CacheUsage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("cache_creation_input_tokens") int cacheCreationInputTokens,
            @JsonProperty("cache_read_input_tokens") int cacheReadInputTokens
    ) {
    }
}
