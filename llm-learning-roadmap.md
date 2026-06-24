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

- [x] **7b. Route through OpenRouter** — take your #1 OpenAI code, change only the
  base URL, the auth header, and the `model` string (e.g. `openai/gpt-4o` ->
  `anthropic/claude-opus-4-8`). Same OpenAI-compatible `messages`/roles shape.
  Goal: a gateway turns the #7 provider swap into a one-line config change
  instead of a code change. The opposite lesson to #7 — feel both.

- [x] **8. Responses API by hand** — re-point #1 and #5 at `POST /v1/responses`.
  Note the shape change: `input` instead of `messages`, an `output` array
  instead of `choices`, tool calls as output items.
  Goal: same loop, newer API generation — field names move, fundamentals don't.

- [x] **9. Server-side conversation state** — let the server own history via
  `previous_response_id` (or the Conversations API) instead of resending it.
  Contrast with #2, where *you* owned the `List<Message>`.
  Goal: managed state vs. client-owned state — and the trade-offs.

- [x] **10. Tracing by hand** — wrap each model call and tool execution in your
  #6 loop in a *span*: start/end time, inputs, outputs, parent link. Print
  them as nested JSON.
  Goal: a "trace" is just structured logging of the loop. (This is what the
  Agent Traces dashboard shows, built by hand.)

## Beyond the basics (still raw HttpClient)

- [ ] **11. Embeddings + semantic search** — call the embeddings endpoint, store
  vectors for your notes, rank by cosine similarity to a query.
  Goal: meaning as vectors; nearest-neighbour search.

- [ ] **12. RAG** — retrieve top-k notes by embedding similarity, stuff them into
  the prompt, answer grounded in them (with citations).
  Goal: give the model knowledge it wasn't trained on.

- [ ] **13. Tokens + context window** — count tokens, watch the window fill,
  truncate or summarize old turns to stay under the limit.
  Goal: context is finite — you manage what fits.

- [ ] **14. Robustness** — handle errors, 429 rate limits, and timeouts with
  retry + exponential backoff.
  Goal: real calls fail; a production loop copes.

- [ ] **15. Multimodal** — send an image to a vision-capable model and get a
  description; optionally combine with a tool.
  Goal: LLMs aren't only text.

- [ ] **16. Evals** — score outputs against expected results: exact/keyword match
  plus an LLM-as-judge for open-ended answers.
  Goal: how you *know* a prompt or model change actually helped.

## MCP stage

- [ ] **17. MCP server** — wrap your task tools as an MCP server (stdio) using
  the official MCP Java SDK.
  Goal: tools as a reusable, process-external capability.

- [ ] **18. MCP client** — connect to your server, list tools, call them; then
  feed those MCP tools into your hand-built agent loop from #6.
  Goal: MCP decouples *who provides tools* from *who runs the agent*.

## Framework stage

- [ ] **19. Rebuild with LangChain4j (or Spring AI)** — same assistant, but the
  framework owns the loop, tool binding, and memory.
  Goal: appreciate exactly what the framework abstracts away — because you
  built it by hand first.
