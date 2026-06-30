package br.com.llm.study.roadmap._12_Rag;

import br.com.llm.study.roadmap.dtos.ChatCompletionRequest;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.EmbeddingResponse;
import br.com.llm.study.roadmap.dtos.Message;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class Rag {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String MODEL_EMBEDDING = "text-embedding-3-small";
  private static final String MODEL_CHAT = "gpt-4o-mini";
  private static final int TOP_K = 2;
  // Drop retrieved notes weaker than this cosine score, so off-topic questions
  // get no context and the model is told to say it does not know.
  private static final double MIN_SIMILARITY = 0.3;

  private static final String SYSTEM_PROMPT = """
        You are a helpful assistant with a good sense of humor, but give information clearly.
        Answer the user's question using ONLY the context below. Each context item is
        numbered like [1]. Cite the number(s) you used, for example [1]. If the context
        does not contain the answer, say that you don't know.
        Context:
        %s
      """;

  static void main() throws IOException, InterruptedException {
    List<String> notes = List.of(
        "Paulo needs to go to the mall at 2 pm",
        "the dentist appointment is on friday morning",
        "refactor the payment service to use the new API",
        "pick up the kids from soccer practice");

    Scanner scanner = new Scanner(System.in);

    List<List<Double>> noteVectors = embed(notes);
    boolean isOver = false;
    while (!isOver) {
      System.out.print("You (type 'exit' to quit): ");
      String reply = scanner.nextLine();
      if (reply == null || reply.equalsIgnoreCase("exit")) {
        isOver = true;
      } else {
        // Embed every note in one batch call, then the query on its own.
        List<Double> queryVector = embed(List.of(reply)).get(0);

        // Rank notes by cosine similarity to the query, highest first.
        record Ranked(String note, double score) {

        }
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
          ranked.add(new Ranked(notes.get(i), cosine(queryVector, noteVectors.get(i))));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());

        // Keep the top-k, then drop anything below the relevance threshold.
        List<Ranked> retrieved = ranked.subList(0, Math.min(TOP_K, ranked.size())).stream()
            .filter(r -> r.score() >= MIN_SIMILARITY)
            .toList();

        System.out.println("query: " + reply);
        System.out.println("retrieved notes:");
        for (Ranked r : retrieved) {
          System.out.printf("  %.4f  %s%n", r.score(), r.note());
        }

        // Number each note so the model can cite it as [1], [2], ...
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < retrieved.size(); i++) {
          context.append("[").append(i + 1).append("] ").append(retrieved.get(i).note()).append("\n");
        }
        if (retrieved.isEmpty()) {
          context.append("(no relevant notes found)");
        }

        List<Message> messages = List.of(
            Message.system(SYSTEM_PROMPT.formatted(context.toString())),
            Message.user(reply));
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.of(MODEL_CHAT, messages);
        String payload = OBJECT_MAPPER.writeValueAsString(chatCompletionRequest);

        System.out.println("payload: " + payload);

        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Authorization", "Bearer " + OPENAI_API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        final HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() > 300) {
          System.out.println("ERROR");
          break;
        }
        ChatCompletionResponse chatCompletionResponse = OBJECT_MAPPER.readValue(response.body(),
            ChatCompletionResponse.class);
        ChatCompletionResponse.Choice choice = chatCompletionResponse.choices().get(0);

        System.out.println("Assistant: " + choice.message().content());
      }
    }
  }

  /**
   * Embeds inputs in a single call; returns vectors ordered to match inputs.
   */
  private static List<List<Double>> embed(List<String> inputs)
      throws IOException, InterruptedException {
    String payload = OBJECT_MAPPER.writeValueAsString(
        Map.of("model", MODEL_EMBEDDING, "input", inputs));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.openai.com/v1/embeddings"))
        .header("Authorization", "Bearer " + OPENAI_API_KEY)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();

    HttpResponse<String> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());

    EmbeddingResponse embeddingResponse = OBJECT_MAPPER.readValue(response.body(),
        EmbeddingResponse.class);

    // The API may return items out of order; each carries its input index.
    List<EmbeddingResponse.Item> items = new ArrayList<>(embeddingResponse.data());
    items.sort(Comparator.comparingInt(EmbeddingResponse.Item::index));

    List<List<Double>> vectors = new ArrayList<>();
    for (EmbeddingResponse.Item item : items) {
      vectors.add(item.embedding());
    }
    return vectors;
  }

  /**
   * Cosine similarity: dot(a, b) / (|a| * |b|).
   */
  private static double cosine(List<Double> a, List<Double> b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.size(); i++) {
      double x = a.get(i);
      double y = b.get(i);
      dot += x * y;
      normA += x * x;
      normB += y * y;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
