package br.com.llm.study.roadmap._18_McpClient;

import br.com.llm.study.roadmap.dtos.ChatCompletionRequest;
import br.com.llm.study.roadmap.dtos.ChatCompletionResponse;
import br.com.llm.study.roadmap.dtos.Message;
import br.com.llm.study.roadmap.dtos.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TaskMcpClient {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final String SERVER_MAIN = "br.com.llm.study.roadmap._17_McpServer.TaskMcpServer";
  private static final int MAX_TURNS = 10;

  static void main() throws IOException, InterruptedException {

    ServerParameters params = ServerParameters
        .builder(System.getProperty("java.home") + "/bin/java")
        .args("-cp", System.getProperty("java.class.path"), SERVER_MAIN)
        .build();

    try (McpSyncClient mcp = McpClient
        .sync(new StdioClientTransport(params, new JacksonMcpJsonMapper(new ObjectMapper())))
        .requestTimeout(Duration.ofSeconds(30))
        .build()) {

      mcp.initialize();

      List<McpSchema.Tool> mcpTools = mcp.listTools().tools();
      List<ChatCompletionRequest.Tool> openAiTools = mcpTools.stream()
          .map(t -> ChatCompletionRequest.Tool.function(t.name(), t.description(), t.inputSchema()))
          .toList();
      System.out.println("Discovered MCP tools: "
          + mcpTools.stream().map(McpSchema.Tool::name).toList());

      List<Message> messages = new ArrayList<>();
      messages.add(Message.system("You are a task assistant. Use the tools to fulfil the "
          + "request, then give a short summary."));
      messages.add(Message.user("Add a task 'buy milk' with notes '2 liters', then list all "
          + "tasks."));

      runLoop(mcp, messages, openAiTools);
    }
  }

  private static void runLoop(McpSyncClient mcp, List<Message> messages,
      List<ChatCompletionRequest.Tool> tools) throws IOException, InterruptedException {
    for (int turn = 0; turn < MAX_TURNS; turn++) {
      ChatCompletionResponse response = chat(new ChatCompletionRequest(
          "gpt-4o-mini", messages, 0.0, null, null, null, tools));
      ChatCompletionResponse.Choice choice = response.choices().get(0);
      messages.add(choice.message());

      if (!"tool_calls".equals(choice.finishReason())) {
        System.out.println("Assistant: " + choice.message().content());
        return;
      }

      for (ToolCall toolCall : choice.message().toolCalls()) {
        String name = toolCall.function().name();
        Map<String, Object> args = parseArguments(toolCall.function().arguments());
        System.out.println("-> calling MCP tool " + name + " " + args);

        McpSchema.CallToolResult result = mcp.callTool(
            CallToolRequest.builder(name).arguments(args).build());
        String text = textOf(result);
        System.out.println("<- " + text);

        messages.add(Message.toolResult(toolCall.id(), text));
      }
    }
    System.out.println("Reached max turns (" + MAX_TURNS + "), stopping.");
  }

  private static Map<String, Object> parseArguments(String json) throws IOException {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
    });
  }

  private static String textOf(McpSchema.CallToolResult result) {
    return result.content().stream()
        .filter(McpSchema.TextContent.class::isInstance)
        .map(content -> ((McpSchema.TextContent) content).text())
        .collect(Collectors.joining("\n"));
  }

  private static ChatCompletionResponse chat(ChatCompletionRequest request)
      throws IOException, InterruptedException {
    String payload = OBJECT_MAPPER.writeValueAsString(request);
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
        .header("Authorization", "Bearer " + OPENAI_API_KEY)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(
        httpRequest, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("API error " + response.statusCode() + ": " + response.body());
    }
    return OBJECT_MAPPER.readValue(response.body(), ChatCompletionResponse.class);
  }
}
