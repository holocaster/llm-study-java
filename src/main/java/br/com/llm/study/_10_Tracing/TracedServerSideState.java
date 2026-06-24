package br.com.llm.study._10_Tracing;

import br.com.llm.study.dtos.Message;
import br.com.llm.study.dtos.ResponsesResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

// #10 based on #9: the server-side-state chat loop (Responses API,
// previous_response_id), now wrapped in trace spans.
//
// A "trace" is nothing but structured logging of the loop. Here the tree is:
//   agent (whole run)
//     -> llm  (turn 1 model call)
//     -> llm  (turn 2 model call)
//     ...
// There are no tool spans because #9 has no tools - if tools were added, each
// execution would be a llm.child(name, "tool") and show up nested here.
public class TracedServerSideState {

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    static void main() throws IOException, InterruptedException {

        HttpClient httpClient = HttpClient.newHttpClient();
        List<Message> input = new ArrayList<>();
        input.add(new Message("user", "I want to buy a gpu"));

        Scanner scanner = new Scanner(System.in);

        Map<String, Object> body = new HashMap<>(Map.of(
                "model", "gpt-4o-mini",
                "input", input,
                "instructions", "You are a helpful assistant for tech store. Ask 1 question"));

        String previousId = null;
        boolean done = false;

        // Open the root workflow span; the Java analogue of `with trace("chat"):`.
        try (Span root = Tracer.trace("chat")) {
            root.input(input.get(0).content());

            while (!done) {

                if (previousId != null) {
                    body.put("previous_response_id", previousId);
                }
                String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                System.out.println("Payload: " + payload);

                String text;
                String responseId;

                // Span around this turn's model call. It auto-nests under the root
                // via the thread-local stack and closes itself at the end of the
                // try block - no manual end() or parent wiring.
                try (Span llm = Tracer.span("responses", "llm")) {
                    llm.input(Map.of(
                            "input", List.copyOf(input),
                            "previousResponseId", previousId == null ? "(none)" : previousId));

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.openai.com/v1/responses"))
                            .header("Authorization", "Bearer " + OPENAI_API_KEY)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("Response body: " + response.body());

                    if (response.statusCode() != 200) {
                        llm.output(Map.of("error", response.statusCode() + ": " + response.body()));
                        System.out.println("API error " + response.statusCode() + ": " + response.body());
                        break;
                    }

                    ResponsesResponse parsed = OBJECT_MAPPER.readValue(response.body(), ResponsesResponse.class);

                    text = parsed.output().stream()
                            .filter(item -> "message".equals(item.type()))
                            .flatMap(item -> item.content().stream())
                            .filter(part -> "output_text".equals(part.type()))
                            .map(ResponsesResponse.ContentPart::text)
                            .findFirst()
                            .orElse("(no text output)");
                    responseId = parsed.id();

                    // output records the assistant text and the response id that
                    // the NEXT turn chains to via previous_response_id.
                    llm.output(Map.of("id", responseId, "text", text));
                }

                System.out.println("Assistant: " + text);
                System.out.print("You (type 'exit' to quit): ");
                String reply = scanner.nextLine();
                if (reply == null || reply.equalsIgnoreCase("exit")) {
                    done = true;
                } else {
                    input.clear();
                    input.add(new Message("user", reply));
                    previousId = responseId;
                }
            }
        } // root span closes here -> Tracer prints the full nested trace

        httpClient.close();
    }
}
