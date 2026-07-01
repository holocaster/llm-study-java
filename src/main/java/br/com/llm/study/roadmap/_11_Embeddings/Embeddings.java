package br.com.llm.study.roadmap._11_Embeddings;

import br.com.llm.study.roadmap.dtos.EmbeddingResponse;
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

public class Embeddings {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String MODEL = "text-embedding-3-small";

  static void main() throws IOException, InterruptedException {

    List<String> notes = List.of(
        "buy milk and eggs at the grocery store",
        "the dentist appointment is on friday morning",
        "refactor the payment service to use the new API",
        "pick up the kids from soccer practice");

    String query = "things to do for work";

    List<List<Double>> noteVectors = embed(notes);
    List<Double> queryVector = embed(List.of(query)).get(0);

    record Ranked(String note, double score) {}
    List<Ranked> ranked = new ArrayList<>();
    for (int i = 0; i < notes.size(); i++) {
      ranked.add(new Ranked(notes.get(i), cosine(queryVector, noteVectors.get(i))));
    }
    ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());

    System.out.println("query: " + query);
    System.out.println("ranked notes:");
    for (Ranked r : ranked) {
      System.out.printf("  %.4f  %s%n", r.score(), r.note());
    }
  }

  private static List<List<Double>> embed(List<String> inputs)
      throws IOException, InterruptedException {
    String payload = OBJECT_MAPPER.writeValueAsString(
        Map.of("model", MODEL, "input", inputs));

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

    List<EmbeddingResponse.Item> items = new ArrayList<>(embeddingResponse.data());
    items.sort(Comparator.comparingInt(EmbeddingResponse.Item::index));

    List<List<Double>> vectors = new ArrayList<>();
    for (EmbeddingResponse.Item item : items) {
      vectors.add(item.embedding());
    }
    return vectors;
  }

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
