package br.com.llm.study.roadmap._19_Framework;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * #19, multimodal edition: the same image/audio/video calls from #15/#15b/#15c, but the
 * framework (LangChain4j) owns the HTTP, JSON, and message shaping. We only build typed
 * Content objects (ImageContent, Audio) and read a typed response -- no raw payloads.
 *
 * Inputs are reused from the earlier exercises so this runs in continuation:
 *   image -> generated.png (#15b)   audio -> reply.mp3 (#15)   video -> frames/ (#15c)
 *
 * Note: OpenAI has no video input, so "video" is frame sampling (many ImageContent),
 * exactly as in #15c -- LangChain4j's VideoContent targets providers like Gemini.
 */
public class FrameworkMultimodal {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final String VISION_MODEL = "gpt-4o-mini";
  private static final String TRANSCRIBE_MODEL = "whisper-1"; // NOT gpt-4o-mini-tts (that is TTS)
  private static final int MAX_FRAMES = 5;

  static void main() throws IOException {
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .apiKey(OPENAI_API_KEY)
        .modelName(VISION_MODEL)
        .temperature(0.0)
        .build();

    image(chatModel);
    audio();
    video(chatModel);
  }

  /** Vision: send an image as a typed ImageContent and read back a description. */
  private static void image(OpenAiChatModel chatModel) throws IOException {
    Path file = Path.of("generated.png");
    if (!Files.exists(file)) {
      System.out.println("[image] skipped: run #15b first to produce " + file.toAbsolutePath());
      return;
    }
    String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    // The prompt must be a TextContent, not the first String arg -- that arg is the
    // message NAME, and OpenAI rejects a name containing spaces.
    UserMessage message = UserMessage.from(
        TextContent.from("Describe this image in one sentence."),
        ImageContent.from(base64, "image/png"));
    System.out.println("[image] " + describe(chatModel, message));
  }

  /** Audio: transcribe a file with the framework's dedicated transcription model. */
  private static void audio() throws IOException {
    Path file = Path.of("reply.mp3");
    if (!Files.exists(file)) {
      System.out.println("[audio] skipped: run #15 first to produce " + file.toAbsolutePath());
      return;
    }
    OpenAiAudioTranscriptionModel transcriptionModel = OpenAiAudioTranscriptionModel.builder()
        .apiKey(OPENAI_API_KEY)
        .modelName(TRANSCRIBE_MODEL)
        .build();
    Audio audio = Audio.builder()
        .binaryData(Files.readAllBytes(file))
        .mimeType("audio/mpeg")
        .build();
    String text = transcriptionModel
        .transcribe(AudioTranscriptionRequest.builder().audio(audio).build())
        .text();
    System.out.println("[audio] transcript: " + text);
  }

  /** Video by frame sampling: many ImageContent parts in one message (see class note). */
  private static void video(OpenAiChatModel chatModel) throws IOException {
    List<Path> frames = listFrames(Path.of("frames"), MAX_FRAMES);
    if (frames.isEmpty()) {
      System.out.println("[video] skipped: extract frames first, e.g. "
          + "ffmpeg -i input.mp4 -vf fps=1/2 frames/frame-%03d.jpg");
      return;
    }
    List<Content> parts = new ArrayList<>();
    parts.add(TextContent.from(
        "These are sequential frames from a video. Describe what happens in 2-3 sentences."));
    for (Path frame : frames) {
      String mime = frame.getFileName().toString().toLowerCase().endsWith(".png")
          ? "image/png" : "image/jpeg";
      String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(frame));
      parts.add(ImageContent.from(base64, mime));
    }
    UserMessage message = UserMessage.from(parts);
    System.out.println("[video] " + frames.size() + " frames -> " + describe(chatModel, message));
  }

  private static String describe(OpenAiChatModel chatModel, UserMessage message) {
    return chatModel.chat(List.<ChatMessage>of(message)).aiMessage().text();
  }

  private static List<Path> listFrames(Path dir, int max) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(dir)) {
      return stream
          .filter(p -> {
            String n = p.getFileName().toString().toLowerCase();
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png");
          })
          .sorted()
          .limit(max)
          .toList();
    }
  }
}
