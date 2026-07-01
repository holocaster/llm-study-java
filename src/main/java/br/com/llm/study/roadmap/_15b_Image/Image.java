package br.com.llm.study.roadmap._15b_Image;

import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Image {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String IMAGE_MODEL = "gpt-image-1";
  private static final String VISION_MODEL = "gpt-4o-mini";
  private static final String ENDPOINT_IMAGES = "https://api.openai.com/v1/images/generations";
  private static final String ENDPOINT_CHAT = "https://api.openai.com/v1/chat/completions";
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  static void main() throws IOException, InterruptedException {

    String prompt = "A moon with mars near it, photorealistic.";
    String base64Png = generateImage(prompt);
    Path out = Path.of("generated.png");
    Files.write(out, Base64.getDecoder().decode(base64Png));
    System.out.println("Saved generated image to " + out.toAbsolutePath());

    String description = describeImage(
        "Describe this image in one sentence.", "image/png", base64Png);
    System.out.println("Vision description: " + description);
  }

  private static String generateImage(String prompt) throws IOException, InterruptedException {
    Map<String, Object> body = Map.of(
        "model", IMAGE_MODEL,
        "prompt", prompt,
        "n", 1,
        "size", "1024x1024");
    String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
    System.out.println("Payload: " + payload );
    HttpResponse<String> response = post(ENDPOINT_IMAGES, payload);
    return OBJECT_MAPPER.readTree(response.body())
        .path("data").get(0).path("b64_json").asText();
  }

  private static String describeImage(String prompt, String mime, String base64)
      throws IOException, InterruptedException {
    String dataUrl = "data:" + mime + ";base64," + base64;
    Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
    Map<String, Object> imagePart = Map.of(
        "type", "image_url", "image_url", Map.of("url", dataUrl));
    Map<String, Object> message = Map.of(
        "role", "user",
        "content", List.of(textPart, imagePart));
    Map<String, Object> body = Map.of("model", VISION_MODEL, "messages", List.of(message));

    String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);

    System.out.println("Payload: " + payload.replaceAll("(base64,)[^\"]+", "$1<...>"));
    HttpResponse<String> response = post(ENDPOINT_CHAT, payload);
    ChatCompletionResponse parsed = OBJECT_MAPPER.readValue(
        response.body(), ChatCompletionResponse.class);
    return parsed.choices().get(0).message().content();
  }

  private static HttpResponse<String> post(String url, String payload)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + OPENAI_API_KEY)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("API error " + response.statusCode() + ": " + response.body());
    }
    return response;
  }
}
