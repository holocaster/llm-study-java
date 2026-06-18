# LLM, Tools & MCP — Java Learning Roadmap

A series of small, standalone exercises. Each is its own runnable `main`.
Shared toy domain: a **personal task/notes assistant** whose tools are backed
by a single local store (start with a JSON file; swap to SQLite later if wanted).

- Language: Java (raw `HttpClient` first, frameworks later)
- Provider: OpenAI first, then a Claude variation

---

## Raw stage (no SDK, just HttpClient + a JSON lib)

- [x] **1. Hello completion** — one POST to OpenAI Chat Completions.
  See the raw request body (`model`, `messages`, roles) and parse the response.
  Goal: an LLM call is just an HTTP request.

- [x] **2. Conversation state** — maintain a `List<Message>` across turns.
  Goal: the model is stateless; *you* own the history.

- [x] **3. Generation params + streaming** — `temperature`/`max_tokens`,
  then parse the SSE stream token-by-token.
  Goal: feel what streaming actually is.

- [x] **4. Structured output** — force JSON (`response_format`) and
  deserialize into a Java record.
  Goal: getting machine-usable data out.

- [x] **5. One tool, by hand** — define an `add_task` tool schema, detect the
  model's `tool_call`, execute it against your store, send the result back,
  get the final answer.
  Goal: tool-calling is a request/response dance you orchestrate. (Core concept.)

- [x] **6. Agent loop, by hand** — multiple tools (`add_task`, `list_tasks`,
  `complete_task`, `search_notes`) in a `while` loop until the model stops
  calling tools.
  Goal: an "agent" is just that loop. No framework needed.

- [x] **7. Provider swap** — re-point exercises 5-6 at Anthropic's Messages API.
  Goal: see what's API-specific (shapes, field names) vs. fundamental (the loop).

## MCP stage

- [ ] **8. MCP server** — wrap your task tools as an MCP server (stdio) using
  the official MCP Java SDK.
  Goal: tools as a reusable, process-external capability.

- [ ] **9. MCP client** — connect to your server, list tools, call them; then
  feed those MCP tools into your hand-built agent loop from #6.
  Goal: MCP decouples *who provides tools* from *who runs the agent*.

## Framework stage

- [ ] **10. Rebuild with LangChain4j (or Spring AI)** — same assistant, but the
  framework owns the loop, tool binding, and memory.
  Goal: appreciate exactly what the framework abstracts away — because you
  built it by hand first.
