package br.com.llm.study.roadmap._15c_Video;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Video understanding by frame sampling. A chat LLM does not ingest video directly;
 * the standard trick is to sample frames and send them as multiple image parts in one
 * message. "Video" here = many stills + the model reasoning over the sequence.
 *
 * Extract frames first, e.g. one every 2 seconds, into a frames/ directory:
 *   ffmpeg -i input.mp4 -vf fps=1/2 frames/frame-%03d.jpg
 * then run this exercise. MAX_FRAMES caps how many we send (cost and payload size).
 */
public class Video {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String VISION_MODEL = "gpt-4o-mini";
  private static final String ENDPOINT_CHAT = "https://api.openai.com/v1/chat/completions";
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final Path FRAMES_DIR = Path.of("frames");
  private static final int MAX_FRAMES = 5;

  static void main() throws IOException, InterruptedException {
    List<Path> frames = listFrames(FRAMES_DIR, MAX_FRAMES);
    if (frames.isEmpty()) {
      System.out.println("No frames found in " + FRAMES_DIR.toAbsolutePath()
          + ". Extract them first, e.g.:");
      System.out.println("  ffmpeg -i input.mp4 -vf fps=1/2 frames/frame-%03d.jpg");
      return;
    }

    System.out.println("Sending " + frames.size() + " frames to the model...");
    String description = describeFrames(
        "These are sequential frames sampled from a video. Describe what happens, "
            + "in 2-3 sentences.",
        frames);
    System.out.println("Video description: " + description);
  }

  /** First MAX_FRAMES image files in the directory, sorted by name (frame order). */
  private static List<Path> listFrames(Path dir, int max) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(dir)) {
      return stream
          .filter(Video::isImage)
          .sorted()
          .limit(max)
          .toList();
    }
  }

  private static boolean isImage(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
  }

  /** Send all frames as image parts in a single chat message and return the description. */
  private static String describeFrames(String prompt, List<Path> frames)
      throws IOException, InterruptedException {
    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of("type", "text", "text", prompt));
    for (Path frame : frames) {
      String mime = frame.getFileName().toString().toLowerCase().endsWith(".png")
          ? "image/png" : "image/jpeg";
      String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(frame));
      String dataUrl = "data:" + mime + ";base64," + base64;
      content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
    }

    Map<String, Object> message = Map.of("role", "user", "content", content);
    Map<String, Object> body = Map.of("model", VISION_MODEL, "messages", List.of(message));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(ENDPOINT_CHAT))
        .header("Authorization", "Bearer " + OPENAI_API_KEY)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("API error " + response.statusCode() + ": " + response.body());
    }
    ChatCompletionResponse parsed = OBJECT_MAPPER.readValue(
        response.body(), ChatCompletionResponse.class);
    return parsed.choices().get(0).message().content();
  }
}
