package br.com.llm.study._10_Tracing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// One node in the trace. A "trace" is just these spans nested by parent.
// The parent link is explicit (parentId) AND structural (children nesting).
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "parentId", "name", "type", "durationMs", "input", "output", "children"})
public class Span implements AutoCloseable {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final String id = "span-" + SEQ.getAndIncrement();
    private String parentId;
    private final String name;
    private final String type;            // "agent" | "llm" | "tool"

    private final Instant start = Instant.now();   // no getter -> not serialized
    private Instant finish;                         // set on end()
    private long durationMs;
    private Object input;
    private Object output;
    private final List<Span> children = new ArrayList<>();

    public Span(String name, String type) {
        this.name = name;
        this.type = type;
    }

    // Open a child span: sets the parent link and nests it under this span.
    public Span child(String name, String type) {
        Span c = new Span(name, type);
        c.parentId = this.id;
        children.add(c);
        return c;
    }

    public Span input(Object in)   { this.input = in;   return this; }
    public Span output(Object out) { this.output = out; return this; }

    // Stamp the end time when the unit of work finishes.
    public Span end() {
        this.finish = Instant.now();
        this.durationMs = Duration.between(start, finish).toMillis();
        return this;
    }

    // Lets a span be used as a try-with-resources resource (the Java analogue of
    // Python's "with trace(...)"): closing stamps the end and pops the stack.
    @Override
    public void close() {
        end();
        Tracer.exit(this);
    }

    // Getters so Jackson serializes the tree.
    public String getId()           { return id; }
    public String getParentId()     { return parentId; }
    public String getName()         { return name; }
    public String getType()         { return type; }
    public long getDurationMs()     { return durationMs; }
    public Object getInput()        { return input; }
    public Object getOutput()       { return output; }
    public List<Span> getChildren() { return children; }

    // ---- aggregations for the dashboard "list" view ----
    // (not bean getters, so Jackson does not serialize them into the detail JSON)

    // When this span started (for the "Created" column).
    public Instant startedAt() {
        return start;
    }

    // When this span finished (null until end()/close()).
    public Instant endedAt() {
        return finish;
    }

    // Total wall-clock duration in seconds (for the "Execution time" column).
    public double seconds() {
        return durationMs / 1000.0;
    }

    // Count this span plus all descendants of the given type, e.g. "tool", "handoff".
    public long count(String type) {
        long n = this.type.equals(type) ? 1 : 0;
        for (Span c : children) {
            n += c.count(type);
        }
        return n;
    }

    // Breadcrumb of the direct child step names (for the "Flow" column).
    public String flow() {
        return children.stream().map(Span::getName).collect(Collectors.joining(" > "));
    }
}
