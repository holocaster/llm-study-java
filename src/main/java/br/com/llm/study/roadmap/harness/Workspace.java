package br.com.llm.study.roadmap.harness;

import java.nio.file.Path;

/** The filesystem safety boundary: every tool path resolves under this base dir. */
public final class Workspace {

    private final Path base;

    public Workspace(Path base) {
        this.base = base.toAbsolutePath().normalize();
    }

    public Path dir() {
        return base;
    }

    /** Resolve a model-supplied path under the base, rejecting any escape. */
    public Path resolve(String path) {
        Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return resolved;
    }
}
