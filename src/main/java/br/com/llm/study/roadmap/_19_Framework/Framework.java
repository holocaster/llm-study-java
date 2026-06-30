package br.com.llm.study.roadmap._19_Framework;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class Framework {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .registerModule(new JavaTimeModule());
  private static final String MODEL = "gpt-4o-mini";
  private static final String MODEL_SPEECH = "gpt-4o-mini-tts";

  static void main() {
    OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
        .apiKey(OPENAI_API_KEY)
        .modelName(MODEL)
        .temperature(0.0)
        .build();

    OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel = OpenAiAudioTranscriptionModel.builder()
        .apiKey(OPENAI_API_KEY)
        .modelName(MODEL_SPEECH)
        .build();

    Scanner scanner = new Scanner(System.in);

    List<ChatMessage> list = new ArrayList<>();

    boolean isOver = false;
    while (!isOver) {
      System.out.print("You (type 'exit' to quit): ");
      String reply = scanner.nextLine();
      if (reply == null || reply.equalsIgnoreCase("exit")) {
        isOver = true;
      } else {
        list.add(UserMessage.userMessage(reply));

        ChatResponse chatResponse = openAiChatModel.chat(list);
        System.out.println("chat response: " + chatResponse.aiMessage().text());

        list.add(chatResponse.aiMessage());
      }
    }

  }
}
