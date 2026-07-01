package br.com.llm.study.roadmap._10_Tracing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "parentId", "name", "type", "durationMs", "input", "output", "children"})
public class Span implements AutoCloseable {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final String id = "span-" + SEQ.getAndIncrement();
    private String parentId;
    private final String name;
    private final String type;

    private final Instant start = Instant.now();
    private Instant finish;
    private long durationMs;
    private Object input;
    private Object output;
    private final List<Span> children = new ArrayList<>();

    public Span(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Span child(String name, String type) {
        Span c = new Span(name, type);
        c.parentId = this.id;
        children.add(c);
        return c;
    }

    public Span input(Object in)   { this.input = in;   return this; }
    public Span output(Object out) { this.output = out; return this; }

    public Span end() {
        this.finish = Instant.now();
        this.durationMs = Duration.between(start, finish).toMillis();
        return this;
    }

    @Override
    public void close() {
        end();
        Tracer.exit(this);
    }

    public String getId()           { return id; }
    public String getParentId()     { return parentId; }
    public String getName()         { return name; }
    public String getType()         { return type; }
    public long getDurationMs()     { return durationMs; }
    public Object getInput()        { return input; }
    public Object getOutput()       { return output; }
    public List<Span> getChildren() { return children; }

    public Instant startedAt() {
        return start;
    }

    public Instant endedAt() {
        return finish;
    }

    public double seconds() {
        return durationMs / 1000.0;
    }

    public long count(String type) {
        long n = this.type.equals(type) ? 1 : 0;
        for (Span c : children) {
            n += c.count(type);
        }
        return n;
    }

    public String flow() {
        return children.stream().map(Span::getName).collect(Collectors.joining(" > "));
    }
}
