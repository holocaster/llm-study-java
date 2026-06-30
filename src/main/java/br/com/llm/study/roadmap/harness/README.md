# Agent Harness

A small hand-built agent harness: a REPL whose loop drives the Anthropic Messages API,
with a pluggable tool registry, file/shell tools, permission gating, session persistence,
and subagent delegation.

## Run

```bash
set -a; . ./.env; set +a   # export ANTHROPIC_API_KEY (Maven does not read .env)
mvn -q exec:java -Dexec.mainClass=br.com.llm.study.roadmap.harness.Harness -Dexec.args="."
```

`exec.args` is the workspace directory (defaults to `.`). Type `/exit` to quit.
History is saved to `session.json` and resumed on the next run.

Set `HARNESS_DEBUG=1` to append every LLM request and response (top-level and subagent)
as JSON to `debug.log` in the workspace. Unset by default; inspect with `jq` or `grep`.

## Tools

| Tool | Approval | Purpose |
|------|----------|---------|
| `read_file`    | no  | read a file under the workspace |
| `list_dir`     | no  | list a directory |
| `write_file`   | yes | write a file (prompts `[y/N]`) |
| `run_command`  | yes | run a shell command (prompts `[y/N]`) |
| `spawn_agent`  | no  | delegate a self-contained subtask to a fresh subagent |

## Example prompts

Basic tools:
- `Read README.md and tell me what this project does.`
- `List the files under src/main/java/br/com/llm/study/roadmap/harness.`

Permission gating (you will be asked to approve):
- `Create a file notes.txt containing "hello".`
- `Run the command: ls -la and tell me how many entries there are.`

Subagent delegation (`spawn_agent`):
- `Use a separate subagent for each of these, then combine the results: (1) summarize README.md, (2) count the .java files under src, (3) list any TODO comments.`
- `Spawn a subagent to create a file hello.txt containing hi.`

The subagent sees only the task string you give it (not the conversation), runs the same
loop with the file/shell tools, cannot delegate further, and returns only its final answer.

Delegation works best when the request names independent, self-contained subtasks. On the
default Haiku model the agent rarely delegates on its own; say "use a subagent" explicitly,
or switch `MODEL` to `claude-sonnet-4-6` for more spontaneous delegation.
