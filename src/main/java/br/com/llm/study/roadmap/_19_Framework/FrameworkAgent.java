package br.com.llm.study.roadmap._19_Framework;

import br.com.llm.study.roadmap.domain.Task;
import br.com.llm.study.roadmap.domain.TaskStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class FrameworkAgent {

  private static final String OPENAI_API_KEY = Objects.requireNonNull(
      System.getenv("OPENAI_API_KEY"));

  public static class TaskTools {
    private final TaskStore store = new TaskStore();

    @Tool("Add a new task to the user's task list")
    public Task addTask(@P("Short task title") String title,
        @P("Optional extra details") String notes) {
      return store.addTask(title, notes);
    }

    @Tool("List every task with its id, title and done status")
    public List<Task> listTasks() {
      return store.listTasks();
    }

    @Tool("Mark the task with the given id as done")
    public Task completeTask(@P("Id of the task to complete") int id) {
      return store.completeTask(id);
    }

    @Tool("Find tasks whose notes contain the given text")
    public List<Task> searchNotes(@P("Text to search in task notes") String query) {
      return store.searchNotes(query);
    }
  }

  public interface Assistant {
    String chat(String userMessage);
  }

  static void main() {
    OpenAiChatModel model = OpenAiChatModel.builder()
        .apiKey(OPENAI_API_KEY)
        .modelName("gpt-4o-mini")
        .temperature(0.0)
        .build();

    Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new TaskTools())
        .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
        .build();

    Scanner scanner = new Scanner(System.in);
    while (true) {
      System.out.print("You (type 'exit' to quit): ");
      String input = scanner.nextLine();
      if (input == null || input.equalsIgnoreCase("exit")) {
        break;
      }
      System.out.println("Assistant: " + assistant.chat(input));
    }
    scanner.close();
  }
}
