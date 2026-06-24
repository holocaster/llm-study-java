package br.com.llm.study._10_Tracing;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// The Java analogue of the Python Agents SDK "with trace(...)" ergonomics:
//
//   try (Span chat = Tracer.trace("chat")) {
//       try (Span llm = Tracer.span("responses", "llm")) { ... }
//   }
//
// A thread-local stack holds the currently-open spans, so a span opened inside
// another auto-nests under it - you never pass a parent by hand. When the root
// span closes, the whole trace is printed as nested JSON.
public final class Tracer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ThreadLocal<Deque<Span>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final List<Span> COMPLETED = new ArrayList<>();
    private static final DateTimeFormatter CREATED_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").withZone(ZoneId.systemDefault());

    private Tracer() {
    }

    // Start the root workflow span (like Python's `with trace("name")`).
    public static Span trace(String name) {
        return open(new Span(name, "agent"));
    }

    // Start a nested span under whatever span is currently open.
    public static Span span(String name, String type) {
        Span parent = STACK.get().peek();
        Span span = (parent == null) ? new Span(name, type) : parent.child(name, type);
        return open(span);
    }

    private static Span open(Span span) {
        STACK.get().push(span);
        return span;
    }

    // Called by Span.close(): pop the stack; when the root closes, record it and
    // reprint the dashboard (the "list" view) plus this trace's detail JSON.
    static void exit(Span span) {
        Deque<Span> stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) {
            COMPLETED.add(span);
            printDashboard();
            printDetail(span);
            // Also ship it to OpenAI so it appears in Logs -> Agent Traces.
            OpenAiTraceExporter.export(span);
            STACK.remove();
        }
    }

    // The "Agent Traces" list view: one row per completed trace.
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

    // Drill-in view: the full nested span tree as JSON.
    private static void printDetail(Span root) {
        try {
            System.out.println("---- trace detail: " + root.getName() + " ----");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
