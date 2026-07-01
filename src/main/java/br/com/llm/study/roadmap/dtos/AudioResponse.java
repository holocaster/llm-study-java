package br.com.llm.study.roadmap.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AudioResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content,
            Audio audio
    ) {

        public br.com.llm.study.roadmap.dtos.Message toMessage() {
            String text = content != null ? content
                    : (audio != null ? audio.transcript() : null);
            return new br.com.llm.study.roadmap.dtos.Message(role, text);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Audio(
            String id,
            String data,
            String transcript,
            @JsonProperty("expires_at") long expiresAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }
}
