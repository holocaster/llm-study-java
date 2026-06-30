package br.com.llm.study.roadmap._15_Multimodal;

import br.com.llm.study.roadmap.dtos.ChatCompletionRequest;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse.Choice;
import br.com.llm.study.roadmap.dtos.Message;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class MultiModal {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final String MODEL_CHAT = "gpt-4o-mini";
  private static final String MODEL_SPEECH = "gpt-4o-mini-tts";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String MODEL_TRANSCRIBE = "gpt-4o-mini-transcribe";
  private static final String ENDPOINT_SPEECH = "https://api.openai.com/v1/audio/speech";
  private static final String ENDPOINT_CHAT = "https://api.openai.com/v1/chat/completions";
  private static final String ENDPOINT_TRANSCRIBE =
      "https://api.openai.com/v1/audio/transcriptions";

  static void main() throws IOException, InterruptedException {

    record AudioRequest(String model, String input, String voice, String response_format) {

    }

    Scanner scanner = new Scanner(System.in);

    HttpClient httpClient = HttpClient.newHttpClient();
    List<Message> messages = new ArrayList<>();
    boolean isOver = false;

    while (!isOver) {
      System.out.print("You (type 'exit' to quit): ");
      String reply = scanner.nextLine();
      if (reply == null || reply.equalsIgnoreCase("exit")) {
        isOver = true;
      } else {
        messages.add(Message.user(reply));

        String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
            new AudioRequest(MODEL_SPEECH, reply, "alloy", "mp3"));

        System.out.println("payload para speech: " + payload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT_SPEECH))
            .header("Authorization", "Bearer " + OPENAI_API_KEY)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(payload))
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, BodyHandlers.ofByteArray());

        if (response.statusCode() > 300) {
          System.out.println("ERROR");
          break;
        }

        Files.write(Path.of("reply.mp3"), response.body());

        //call to chat

        payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
            ChatCompletionRequest.of(MODEL_CHAT, messages));

        System.out.println("Payload para o chat: " + payload);

        request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT_CHAT))
            .header("Authorization", "Bearer " + OPENAI_API_KEY)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(payload))
            .build();


        HttpResponse<String> responseChat = httpClient.send(request, BodyHandlers.ofString());

        if (responseChat.statusCode() > 300) {
          System.out.println("ERROR: " + responseChat.body());
          break;
        }

        ChatCompletionResponse chatCompletionResponse = OBJECT_MAPPER.readValue(
            responseChat.body(),
            ChatCompletionResponse.class);
        Choice choice = chatCompletionResponse.choices().get(0);

        System.out.println("Assistant: " + choice.message().content());

        messages.add(choice.message());


      }
    }

    // Speech-to-text: transcribe the mp3 we just generated, closing the
    // text -> speech -> text round trip.
    Path replyAudio = Path.of("reply.mp3");
    if (Files.exists(replyAudio)) {
      System.out.println("Transcricao do reply.mp3: " + transcribe(httpClient, replyAudio));
    }
  }

  /**
   * Uploads the audio file to /v1/audio/transcriptions as multipart/form-data -- the
   * one OpenAI call here that is not a JSON body -- and returns the recognized text.
   */
  private static String transcribe(HttpClient httpClient, Path audioPath)
      throws IOException, InterruptedException {
    String boundary = "----java" + System.currentTimeMillis();
    byte[] audio = Files.readAllBytes(audioPath);

    java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
    java.io.PrintStream writer = new java.io.PrintStream(
        body, true, java.nio.charset.StandardCharsets.UTF_8);
    writer.print("--" + boundary + "\r\n");
    writer.print("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
    writer.print(MODEL_TRANSCRIBE + "\r\n");
    writer.print("--" + boundary + "\r\n");
    writer.print("Content-Disposition: form-data; name=\"file\"; filename=\"reply.mp3\"\r\n");
    writer.print("Content-Type: audio/mpeg\r\n\r\n");
    writer.flush();
    body.write(audio);
    writer.print("\r\n--" + boundary + "--\r\n");
    writer.flush();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(ENDPOINT_TRANSCRIBE))
        .header("Authorization", "Bearer " + OPENAI_API_KEY)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(BodyPublishers.ofByteArray(body.toByteArray()))
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
    if (response.statusCode() > 300) {
      return "ERROR: " + response.body();
    }
    return OBJECT_MAPPER.readTree(response.body()).path("text").asText();
  }
}
