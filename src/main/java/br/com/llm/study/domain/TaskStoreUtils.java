package br.com.llm.study.domain;

import br.com.llm.study.dtos.ChatCompletionRequest.Tool;

import java.util.List;
import java.util.Map;

public final class TaskStoreUtils {

    private TaskStoreUtils() {
    }

    public static List<Tool> tools() {
        return List.of(addTask(), listTasks(), completeTask(), searchNotes());
    }

    private static Tool addTask() {
        return Tool.function(
                "add_task",
                "Add a new task to the user's task list",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "Short task title"),
                                "notes", Map.of("type", "string", "description", "Optional extra details")
                        ),
                        "required", List.of("title"),
                        "additionalProperties", false
                )
        );
    }

    private static Tool listTasks() {
        return Tool.function(
                "list_tasks",
                "List every task with its id, title and done status",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of(),
                        "additionalProperties", false
                )
        );
    }

    private static Tool completeTask() {
        return Tool.function(
                "complete_task",
                "Mark the task with the given id as done",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of("type", "integer", "description", "Id of the task to complete")
                        ),
                        "required", List.of("id"),
                        "additionalProperties", false
                )
        );
    }

    private static Tool searchNotes() {
        return Tool.function(
                "search_notes",
                "Find tasks whose notes contain the given text",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Text to search for in task notes")
                        ),
                        "required", List.of("query"),
                        "additionalProperties", false
                )
        );
    }
}
