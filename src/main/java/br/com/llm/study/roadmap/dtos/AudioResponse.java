package br.com.llm.study.roadmap.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response shape when a chat completion returns audio -- requested with
 * modalities ["text","audio"] on an audio-capable model (e.g. gpt-4o-audio-preview).
 * The assistant message has no text content; instead it carries an `audio` object:
 * the base64-encoded audio bytes plus the transcript of what they say.
 */
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

        /**
         * Folds this audio reply into a plain conversation message. The public Message
         * has no audio field, so the transcript stands in as the text content (audio
         * replies have null content). Note this keeps only the text -- the audio bytes
         * and the audio id needed to reference it server-side are dropped.
         */
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
