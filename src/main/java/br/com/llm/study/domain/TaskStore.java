package br.com.llm.study.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory backing store for the assistant's tools. Each public method maps to
 * one LLM tool the model can call:
 *   addTask      -> add_task
 *   listTasks    -> list_tasks
 *   completeTask -> complete_task
 *   searchNotes  -> search_notes
 */
public class TaskStore {

    private final List<Task> tasks = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public Task addTask(String title, String notes) {
        Task task = new Task(nextId.getAndIncrement(), title, notes, false);
        tasks.add(task);
        return task;
    }

    public List<Task> listTasks() {
        return List.copyOf(tasks);
    }

    public Task completeTask(int id) {
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task.id() == id) {
                Task done = task.complete();
                tasks.set(i, done);
                return done;
            }
        }
        throw new IllegalArgumentException("No task with id " + id);
    }

    public List<Task> searchNotes(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<Task> matches = new ArrayList<>();
        for (Task task : tasks) {
            String notes = task.notes() == null ? "" : task.notes();
            if (notes.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(task);
            }
        }
        return matches;
    }
}
