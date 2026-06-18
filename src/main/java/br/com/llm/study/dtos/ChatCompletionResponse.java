package br.com.llm.study.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage,
        @JsonProperty("service_tier") String serviceTier,
        @JsonProperty("system_fingerprint") String systemFingerprint
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            Message message,
            Object logprobs,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens,
            @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,
            @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptTokensDetails(
            @JsonProperty("cached_tokens") int cachedTokens,
            @JsonProperty("audio_tokens") int audioTokens
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompletionTokensDetails(
            @JsonProperty("reasoning_tokens") int reasoningTokens,
            @JsonProperty("audio_tokens") int audioTokens,
            @JsonProperty("accepted_prediction_tokens") int acceptedPredictionTokens,
            @JsonProperty("rejected_prediction_tokens") int rejectedPredictionTokens
    ) {
    }
}
