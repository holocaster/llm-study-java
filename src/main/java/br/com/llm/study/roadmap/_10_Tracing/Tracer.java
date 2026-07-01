package br.com.llm.study.roadmap._10_Tracing;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Tracer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ThreadLocal<Deque<Span>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final List<Span> COMPLETED = new ArrayList<>();
    private static final DateTimeFormatter CREATED_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").withZone(ZoneId.systemDefault());

    private Tracer() {
    }

    public static Span trace(String name) {
        return open(new Span(name, "agent"));
    }

    public static Span span(String name, String type) {
        Span parent = STACK.get().peek();
        Span span = (parent == null) ? new Span(name, type) : parent.child(name, type);
        return open(span);
    }

    private static Span open(Span span) {
        STACK.get().push(span);
        return span;
    }

    static void exit(Span span) {
        Deque<Span> stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) {
            COMPLETED.add(span);
            printDashboard();
            printDetail(span);

            OpenAiTraceExporter.export(span);
            STACK.remove();
        }
    }

    private static void printDashboard() {
        System.out.println();
        System.out.println("==== AGENT TRACES ====");
        System.out.printf("%-14s %-32s %9s %6s %15s %24s%n",
                "Workflow", "Flow", "Handoffs", "Tools", "Execution time", "Created");
        for (Span t : COMPLETED) {
            System.out.printf("%-14s %-32s %9d %6d %14.2fs %24s%n",
                    t.getName(), t.flow(), t.count("handoff"), t.count("tool"),
                    t.seconds(), CREATED_FMT.format(t.startedAt()));
        }
    }

    private static void printDetail(Span root) {
        try {
            System.out.println("---- trace detail: " + root.getName() + " ----");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
