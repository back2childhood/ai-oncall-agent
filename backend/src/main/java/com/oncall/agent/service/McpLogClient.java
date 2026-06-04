package com.oncall.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class McpLogClient {
    private final RestClient restClient;
    private final boolean enabled;
    private final String endpoint;
    private final String query;
    private final int limit;
    private final int lookbackMinutes;

    public McpLogClient(
            RestClient.Builder builder,
            @Value("${app.operations.mcp-logs.enabled}") boolean enabled,
            @Value("${app.operations.mcp-logs.endpoint}") String endpoint,
            @Value("${app.operations.mcp-logs.query}") String query,
            @Value("${app.operations.mcp-logs.limit}") int limit,
            @Value("${app.operations.logs-lookback-minutes}") int lookbackMinutes) {
        this.restClient = builder.build();
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.query = query;
        this.limit = limit;
        this.lookbackMinutes = lookbackMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public OperationalSnapshot queryLogs() {
        if (!enabled) {
            return new OperationalSnapshot("mcp-logs", "mcp-logs-disabled", "logs", "", 0);
        }

        Instant now = Instant.now();
        Instant since = now.minus(lookbackMinutes, ChronoUnit.MINUTES);
        JsonNode root = restClient.post()
                .uri(endpoint)
                .body(Map.of(
                        "query", query,
                        "since", since.toString(),
                        "until", now.toString(),
                        "limit", limit))
                .retrieve()
                .body(JsonNode.class);

        JsonNode logs = logsNode(root);
        if (logs == null || !logs.isArray() || logs.isEmpty()) {
            return new OperationalSnapshot("mcp-logs", "mcp-logs-" + now, "logs", "", 0);
        }

        StringBuilder content = new StringBuilder("# MCP Queried Logs\n\n");
        int count = 0;
        for (JsonNode log : logs) {
            count++;
            content.append("## Log Entry ").append(count).append('\n')
                    .append("Timestamp: ").append(text(log, "timestamp", "time", "ts")).append('\n')
                    .append("Service: ").append(text(log, "service", "app", "source")).append('\n')
                    .append("Level: ").append(text(log, "level", "severity")).append('\n')
                    .append("Message: ").append(text(log, "message", "msg", "line")).append('\n');
            JsonNode attributes = log.path("attributes");
            if (!attributes.isMissingNode() && !attributes.isNull()) {
                content.append("Attributes: ").append(attributes).append('\n');
            }
            content.append('\n');
        }

        return new OperationalSnapshot("mcp-logs", "mcp-logs-" + now, "logs", content.toString(), count);
    }

    private JsonNode logsNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.path("logs").isArray()) {
            return root.path("logs");
        }
        if (root.path("data").path("logs").isArray()) {
            return root.path("data").path("logs");
        }
        if (root.path("result").isArray()) {
            return root.path("result");
        }
        return null;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}
