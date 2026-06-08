package com.oncall.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ClassroomLlmClient {
    private final RestClient restClient;
    private final boolean enabled;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClassroomLlmClient(
            RestClient.Builder builder,
            @Value("${app.classroom-llm.enabled}") boolean enabled,
            @Value("${app.classroom-llm.endpoint}") String endpoint,
            @Value("${app.classroom-llm.api-key}") String apiKey,
            @Value("${app.classroom-llm.model}") String model,
            @Value("${app.classroom-llm.max-tokens}") int maxTokens) {
        this.restClient = builder.build();
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public boolean isEnabled() {
        return enabled && !endpoint.isBlank() && !apiKey.isBlank();
    }

    public String generate(String input) {
        JsonNode response = restClient.post()
                .uri(endpoint)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("x-api-key", apiKey)
                .body(Map.of(
                        "model", model,
                        "input", input,
                        "maxTokens", maxTokens))
                .retrieve()
                .body(JsonNode.class);
        return extractText(response);
    }

    private String extractText(JsonNode response) {
        if (response == null || response.isNull()) {
            return "Classroom LLM returned an empty response.";
        }
        for (String field : new String[] {"output", "text", "completion", "message", "content"}) {
            String value = response.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        JsonNode nestedText = response.path("data").path("text");
        if (!nestedText.asText("").isBlank()) {
            return nestedText.asText();
        }
        JsonNode nestedOutput = response.path("data").path("output");
        if (!nestedOutput.asText("").isBlank()) {
            return nestedOutput.asText();
        }
        JsonNode body = response.path("body");
        if (!body.asText("").isBlank()) {
            return body.asText();
        }
        return response.toPrettyString();
    }
}
