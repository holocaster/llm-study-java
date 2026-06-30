package br.com.llm.study.roadmap._17_McpServer;

import br.com.llm.study.roadmap.domain.Task;
import br.com.llm.study.roadmap.domain.TaskStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * #17 -- the same task tools from #5/#6, now exposed as an MCP server over stdio. The agent LOOP is
 * gone from here; this process only OWNS the tools. Any MCP client (your #18 loop, Claude Desktop,
 * an IDE) launches this as a subprocess and calls them.
 * <p>
 * stdio is the protocol channel: stdout carries JSON-RPC, so NEVER println to stdout here -- log to
 * System.err only.
 */
public class TaskMcpServer {

  private static final TaskStore STORE = new TaskStore();

  private static final Map<String, Object> ADD_TASK_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of(
          "title", Map.of("type", "string", "description", "Short task title"),
          "notes", Map.of("type", "string", "description", "Optional extra details")),
      "required", List.of("title"),
      "additionalProperties", false);
  private static final Map<String, Object> LIST_TASKS_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of(),
      "required", List.of(),
      "additionalProperties", false);
  private static final Map<String, Object> COMPLETE_TASK_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of(
          "id", Map.of("type", "integer", "description", "Id of the task to complete")),
      "required", List.of("id"),
      "additionalProperties", false);
  private static final Map<String, Object> SEARCH_NOTES_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of(
          "query", Map.of("type", "string", "description", "Text to search in task notes")),
      "required", List.of("query"),
      "additionalProperties", false);

  public static void main(String[] args) throws InterruptedException {
    StdioServerTransportProvider transport =
        new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()));

    McpSyncServer server = McpServer.sync(transport)
        .serverInfo("task-store", "1.0.0")
        // Required: the server must advertise the tools capability or addTool() throws.
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
        .build();

    server.addTool(tool("add_task", "Add a new task to the user's task list",
        ADD_TASK_SCHEMA, req -> {
          String title = (String) req.arguments().get("title");
          String notes = (String) req.arguments().get("notes");
          return "Added " + STORE.addTask(title, notes);
        }));

    server.addTool(tool("list_tasks", "List every task with its id, title and done status",
        LIST_TASKS_SCHEMA, req ->
            STORE.listTasks().stream().map(Task::toString).collect(Collectors.joining("\n"))));

    server.addTool(tool("complete_task", "Mark the task with the given id as done",
        COMPLETE_TASK_SCHEMA, req -> {
          int id = ((Number) req.arguments().get("id")).intValue();
          return "Completed " + STORE.completeTask(id);
        }));

    server.addTool(tool("search_notes", "Find tasks whose notes contain the given text",
        SEARCH_NOTES_SCHEMA, req -> {
          String query = (String) req.arguments().get("query");
          return STORE.searchNotes(query).stream()
              .map(Task::toString).collect(Collectors.joining("\n"));
        }));

    System.err.println("task-store MCP server ready on stdio");
    new CountDownLatch(1).await(); // long-running: block until the client disconnects
  }

  /**
   * Wraps a name + JSON schema + a plain String-returning body into a sync tool spec.
   */
  private static SyncToolSpecification tool(
      String name, String description, Map<String, Object> schema,
      Function<CallToolRequest, String> body) {
    return SyncToolSpecification.builder()
        .tool(Tool.builder(name, schema).description(description).build())
        .callHandler((exchange, request) -> CallToolResult.builder()
            .content(List.of(McpSchema.TextContent.builder(body.apply(request)).build()))
            .build())
        .build();
  }
}
