package com.oncall.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class McpLogClient {
    private static final String DEFAULT_REQUEST_TEMPLATE = "{\"query\":\"${query}\",\"since\":\"${since}\",\"until\":\"${until}\",\"limit\":${limit}}";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String endpoint;
    private final String query;
    private final int limit;
    private final int lookbackMinutes;
    private final String requestTemplate;
    private final String logsPath;
    private final String timestampFields;
    private final String serviceFields;
    private final String levelFields;
    private final String messageFields;
    private final String attributesPath;
    private final String authHeader;
    private final String authToken;

    public McpLogClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.operations.mcp-logs.enabled}") boolean enabled,
            @Value("${app.operations.mcp-logs.endpoint}") String endpoint,
            @Value("${app.operations.mcp-logs.query}") String query,
            @Value("${app.operations.mcp-logs.limit}") int limit,
            @Value("${app.operations.logs-lookback-minutes}") int lookbackMinutes,
            @Value("${app.operations.mcp-logs.request-template}") String requestTemplate,
            @Value("${app.operations.mcp-logs.response.logs-path}") String logsPath,
            @Value("${app.operations.mcp-logs.response.timestamp-fields}") String timestampFields,
            @Value("${app.operations.mcp-logs.response.service-fields}") String serviceFields,
            @Value("${app.operations.mcp-logs.response.level-fields}") String levelFields,
            @Value("${app.operations.mcp-logs.response.message-fields}") String messageFields,
            @Value("${app.operations.mcp-logs.response.attributes-path}") String attributesPath,
            @Value("${app.operations.mcp-logs.auth-header:}") String authHeader,
            @Value("${app.operations.mcp-logs.auth-token:}") String authToken) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.query = query;
        this.limit = limit;
        this.lookbackMinutes = lookbackMinutes;
        this.requestTemplate = defaultIfBlank(requestTemplate, DEFAULT_REQUEST_TEMPLATE);
        this.logsPath = defaultIfBlank(logsPath, "logs");
        this.timestampFields = defaultIfBlank(timestampFields, "timestamp,time,ts");
        this.serviceFields = defaultIfBlank(serviceFields, "service,app,source");
        this.levelFields = defaultIfBlank(levelFields, "level,severity");
        this.messageFields = defaultIfBlank(messageFields, "message,msg,line");
        this.attributesPath = defaultIfBlank(attributesPath, "attributes");
        this.authHeader = authHeader;
        this.authToken = authToken;
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
                .headers(this::applyAuth)
                .body(requestBody(since, now))
                .retrieve()
                .body(JsonNode.class);

        JsonNode logs = nodeAt(root, logsPath);
        if (logs == null || !logs.isArray() || logs.isEmpty()) {
            return new OperationalSnapshot("mcp-logs", "mcp-logs-" + now, "logs", "", 0);
        }

        StringBuilder content = new StringBuilder("# MCP Queried Logs\n\n");
        int count = 0;
        for (JsonNode log : logs) {
            count++;
            content.append("## Log Entry ").append(count).append('\n')
                    .append("Timestamp: ").append(firstText(log, timestampFields)).append('\n')
                    .append("Service: ").append(firstText(log, serviceFields)).append('\n')
                    .append("Level: ").append(firstText(log, levelFields)).append('\n')
                    .append("Message: ").append(firstText(log, messageFields)).append('\n');
            JsonNode attributes = nodeAt(log, attributesPath);
            if (attributes != null && !attributes.isMissingNode() && !attributes.isNull()) {
                content.append("Attributes: ").append(attributes).append('\n');
            }
            content.append('\n');
        }

        return new OperationalSnapshot("mcp-logs", "mcp-logs-" + now, "logs", content.toString(), count);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank() || "__DEFAULT__".equals(value)) {
            return defaultValue;
        }
        return value;
    }

    private void applyAuth(HttpHeaders headers) {
        if (!authHeader.isBlank() && !authToken.isBlank()) {
            headers.set(authHeader, authToken);
        }
    }

    private JsonNode requestBody(Instant since, Instant until) {
        String json = requestTemplate
                .replace("${query}", escape(query))
                .replace("${since}", since.toString())
                .replace("${until}", until.toString())
                .replace("${limit}", Integer.toString(limit));
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid MCP_LOGS_REQUEST_TEMPLATE JSON", ex);
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JsonNode nodeAt(JsonNode node, String path) {
        if (node == null || path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            if (part.isBlank()) {
                continue;
            }
            current = current.path(part);
        }
        return current.isMissingNode() ? null : current;
    }

    private String firstText(JsonNode node, String fields) {
        for (String field : fields.split(",")) {
            JsonNode value = nodeAt(node, field.trim());
            if (value != null && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return "unknown";
    }
}
