package br.com.llm.study.roadmap.harness;

import java.nio.file.Path;

public final class Workspace {

    private final Path base;

    public Workspace(Path base) {
        this.base = base.toAbsolutePath().normalize();
    }

    public Path dir() {
        return base;
    }

    public Path resolve(String path) {
        Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return resolved;
    }
}
