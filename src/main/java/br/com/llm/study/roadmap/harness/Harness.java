package br.com.llm.study.roadmap.harness;

import br.com.llm.study.roadmap.dtos.AnthropicMessage;
import br.com.llm.study.roadmap.dtos.AnthropicRequest;
import br.com.llm.study.roadmap.dtos.AnthropicResponse;
import br.com.llm.study.roadmap.dtos.ContentBlock;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class Harness {

    private static final String API_KEY = Objects.requireNonNull(
            System.getenv("ANTHROPIC_API_KEY"), "ANTHROPIC_API_KEY not set");
    private static final String URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_ITERATIONS = 15;

    private static final String SYSTEM_PROMPT =
            "You are a small coding/task agent operating inside a workspace directory. "
          + "Use the provided tools to read, list, and write files and run shell commands. "
          + "You can delegate a self-contained subtask to a subagent with the spawn_agent tool. "
          + "Prefer reading before writing. Give a concise final answer when done.";

    private static final String SUBAGENT_PROMPT =
            "You are a subagent handling one self-contained task inside a workspace directory. "
          + "You have file and shell tools but cannot delegate further. "
          + "Do the task and return a concise final answer.";

    private static final ObjectMapper M = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private final SessionStore sessionStore;
    private final DebugLog debug;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Scanner scanner = new Scanner(System.in);

    public Harness(SessionStore sessionStore, DebugLog debug) {
        this.sessionStore = sessionStore;
        this.debug = debug;
    }

    public static void main(String[] args) throws Exception {
        Path workDir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Workspace workspace = new Workspace(workDir);

        ReadFileTool read = new ReadFileTool(workspace);
        ListDirTool list = new ListDirTool(workspace);
        WriteFileTool write = new WriteFileTool(workspace);
        RunCommandTool run = new RunCommandTool(workspace);

        SessionStore store = new SessionStore(workDir.resolve("session.json"), M);
        DebugLog debug = new DebugLog(workDir.resolve("debug.log"), M);
        Harness harness = new Harness(store, debug);

        ToolRegistry subTools = new ToolRegistry(read, list, write, run);
        AgentRunner runner = task -> {
            List<AnthropicMessage> sub = new ArrayList<>();
            sub.add(AnthropicMessage.user(task));
            return harness.runConversation(sub, SUBAGENT_PROMPT, subTools);
        };
        ToolRegistry topLevelTools = new ToolRegistry(
                read, list, write, run, new SpawnAgentTool(runner));

        harness.run(workspace, topLevelTools);
    }

    public void run(Workspace workspace, ToolRegistry topLevelTools) throws Exception {
        List<AnthropicMessage> messages = sessionStore.load();
        if (!messages.isEmpty()) {
            System.out.println("Resumed " + messages.size() + " messages from previous session.");
        }
        System.out.println("Workspace: " + workspace.dir() + "  (type /exit to quit)");

        while (true) {
            System.out.print("\nyou> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.equals("/exit")) break;
            if (line.isEmpty()) continue;

            messages.add(AnthropicMessage.user(line));
            String answer = runConversation(messages, SYSTEM_PROMPT, topLevelTools);
            System.out.println("\nagent> " + answer);
            sessionStore.save(messages);
        }
        System.out.println("Goodbye.");
    }

    private String runConversation(List<AnthropicMessage> messages,
                                   String systemPrompt,
                                   ToolRegistry tools) throws Exception {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            AnthropicResponse response = callAnthropic(messages, systemPrompt, tools);
            if (response == null) {
                return "(API error)";
            }
            messages.add(AnthropicMessage.assistant(response.content()));

            if ("tool_use".equals(response.stopReason())) {
                List<ContentBlock> results = new ArrayList<>();
                for (ContentBlock block : response.content()) {
                    if (block instanceof ContentBlock.ToolUse use) {
                        results.add(new ContentBlock.ToolResult(use.id(), handleToolUse(use, tools)));
                    } else if (block instanceof ContentBlock.Text(String text1)) {
                        System.out.println("(thinking) " + text1);
                    }
                }
                messages.add(AnthropicMessage.toolResults(results));
            } else {
                return finalText(response.content());
            }
        }
        return "(stopped: reached max iterations)";
    }

    private String finalText(List<ContentBlock> content) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof ContentBlock.Text text) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    private String handleToolUse(ContentBlock.ToolUse use, ToolRegistry tools) {
        Tool tool = tools.get(use.name());
        if (tool == null) {
            return "ERROR: unknown tool " + use.name();
        }
        if (tool.requiresApproval()) {
            System.out.println("\nTool '" + use.name() + "' wants to run with input: " + use.input());
            System.out.print("Approve? [y/N] ");
            String answer = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (!answer.equalsIgnoreCase("y")) {
                return "Denied by user.";
            }
        }
        try {
            return tool.execute(use.input());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private AnthropicResponse callAnthropic(List<AnthropicMessage> messages,
                                            String systemPrompt,
                                            ToolRegistry tools) throws Exception {
        AnthropicRequest request = new AnthropicRequest(
                MODEL, MAX_TOKENS, systemPrompt, messages, tools.schemas());
        String payload = M.writeValueAsString(request);
        debug.log("REQUEST", payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("x-api-key", API_KEY)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        debug.log("RESPONSE", response.body());
        if (response.statusCode() != 200) {
            System.out.println("API error " + response.statusCode() + ": " + response.body());
            return null;
        }
        return M.readValue(response.body(), AnthropicResponse.class);
    }
}
