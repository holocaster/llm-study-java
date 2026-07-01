package br.com.llm.study.roadmap._13_TokensContextWindow;

import br.com.llm.study.roadmap.dtos.ChatCompletionRequest;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse.Choice;
import br.com.llm.study.roadmap.dtos.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class Tokens {

  private static final String MODEL_CHAT = "gpt-4o-mini";

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static void main() throws IOException, InterruptedException {

    Scanner scanner = new Scanner(System.in);

    HttpClient httpClient = HttpClient.newHttpClient();
    List<Message> messages = new ArrayList<>();
    boolean isOver = false;

    int promptTokenOpenAI = 0, completionTokenAI = 0;

    EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
    Encoding encoding = encodingRegistry.getEncoding(EncodingType.O200K_BASE);

    while (!isOver) {
      System.out.print("You (type 'exit' to quit): ");
      String reply = scanner.nextLine();
      if (reply == null || reply.equalsIgnoreCase("exit")) {
        isOver = true;
      } else {
        messages.add(Message.user(reply));
        System.out.println("Estimativa de tokens do prompt (local): "
            + estimatePromptTokens(messages, encoding));

        String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
            ChatCompletionRequest.of(MODEL_CHAT, messages));

        System.out.println("payload: " + payload);

        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Authorization", "Bearer " + OPENAI_API_KEY)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(payload))
            .build();

        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        if (response.statusCode() > 300) {
          System.out.println("ERROR");
          break;
        }

        ChatCompletionResponse chatCompletionResponse = OBJECT_MAPPER.readValue(response.body(),
            ChatCompletionResponse.class);
        Choice choice = chatCompletionResponse.choices().get(0);

        System.out.println("Assistant: " + choice.message().content());

        System.out.println("Total nesse chat: " + chatCompletionResponse.usage());
        promptTokenOpenAI += chatCompletionResponse.usage().promptTokens();
        completionTokenAI += chatCompletionResponse.usage().completionTokens();
        System.out.println("PromptTokenAITotal: " + promptTokenOpenAI);
        System.out.println("CompletionTokenAITotal: " + completionTokenAI);
        System.out.println("TotalCompletionAITotal:" + (promptTokenOpenAI + completionTokenAI));

        messages.add(choice.message());
      }
    }
  }

  private static int estimatePromptTokens(List<Message> messages, Encoding encoding) {
    int total = 0;
    for (Message message : messages) {
      total += 3;
      total += encoding.countTokens(message.role());
      if (message.content() != null) {
        total += encoding.countTokens(message.content());
      }
    }
    total += 3;
    return total;
  }
}
