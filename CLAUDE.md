  # CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working agreement (read first)

- Do NOT alter any file. When asked about something, explain it or show how to do it — do not edit.
- Only modify, create, or delete a file when explicitly asked.

## What this is

A learning project for calling LLMs in pure Java — no SDK, raw `java.net.http.HttpClient` + Jackson.
Each step of `llm-learning-roadmap.md` is a separate, self-contained class with its own `main`,
under `src/main/java/br/com/llm/study/roadmap/_N_<Topic>/`. They are meant to be read and run
independently, not wired together. Start with the roadmap to find the relevant exercise.

## Build & run

Requires Java 25 and Maven 3.8+.

```bash
mvn package                 # compile all exercises
```

Run a single exercise by its fully-qualified main class, e.g.:

```bash
mvn exec:java -Dexec.mainClass=br.com.llm.study.roadmap._1_HelloCompletion.HelloCompletion
```

There are no automated tests; "running it" is the test. The README's `com.example.llm.Main`
command is stale — no such class exists.

## API keys

Exercises read keys from environment variables via `System.getenv("OPENAI_API_KEY")` /
`ANTHROPIC_API_KEY` and fail fast (`Objects.requireNonNull`) if unset. Keys live in `.env`
(gitignored). `.env` is auto-loaded by the VS Code launch config (`.vscode/launch.json`);
Maven does NOT read it, so for `mvn exec:java` export the vars first (e.g. `set -a; . ./.env; set +a`).

## Architecture

- `roadmap/dtos/` — shared Jackson records for request/response shapes of each API
  (OpenAI Chat Completions, Responses API, Anthropic Messages). Reused across exercises.
- `roadmap/domain/` — the toy domain: an in-memory `TaskStore` whose public methods map 1:1 to
  the LLM tools the agent exercises expose (`addTask` -> `add_task`, etc.). This is the single
  store the tool-calling and agent-loop exercises act on.
- Each `ObjectMapper` is configured with `FAIL_ON_UNKNOWN_PROPERTIES = false` and the
  `JavaTimeModule` — provider responses carry fields the DTOs intentionally don't model.

The roadmap is deliberately progressive: provider swap (#7), gateway routing (#7b),
Responses API (#8), server-side state (#9), and tracing (#10) all re-point or wrap earlier
exercises — read the earlier step before changing a later one.
