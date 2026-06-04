package com.oncall.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PrometheusAlertClient {
    private final RestClient restClient;
    private final boolean enabled;
    private final String alertsPath;

    public PrometheusAlertClient(
            RestClient.Builder builder,
            @Value("${app.operations.prometheus.enabled}") boolean enabled,
            @Value("${app.operations.prometheus.base-url}") String baseUrl,
            @Value("${app.operations.prometheus.alerts-path}") String alertsPath) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.enabled = enabled;
        this.alertsPath = alertsPath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public OperationalSnapshot fetchAlerts() {
        if (!enabled) {
            return new OperationalSnapshot("prometheus", "prometheus-alerts-disabled", "alert", "", 0);
        }

        JsonNode root = restClient.get()
                .uri(alertsPath)
                .retrieve()
                .body(JsonNode.class);
        JsonNode alerts = root == null ? null : root.path("data").path("alerts");
        if (alerts == null || !alerts.isArray() || alerts.isEmpty()) {
            return new OperationalSnapshot("prometheus", "prometheus-alerts-" + Instant.now(), "alert", "", 0);
        }

        StringBuilder content = new StringBuilder("# Prometheus Alerts\n\n");
        int count = 0;
        for (JsonNode alert : alerts) {
            count++;
            JsonNode labels = alert.path("labels");
            JsonNode annotations = alert.path("annotations");
            content.append("## Alert ").append(count).append(": ")
                    .append(labels.path("alertname").asText("unknown-alert")).append('\n')
                    .append("State: ").append(alert.path("state").asText("unknown")).append('\n')
                    .append("Active at: ").append(alert.path("activeAt").asText("unknown")).append('\n')
                    .append("Labels: ").append(formatObject(labels)).append('\n')
                    .append("Annotations: ").append(formatObject(annotations)).append("\n\n");
        }
        return new OperationalSnapshot(
                "prometheus",
                "prometheus-alerts-" + Instant.now(),
                "alert",
                content.toString(),
                count);
    }

    private String formatObject(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(field.getKey()).append('=').append(field.getValue().asText());
        }
        return builder.toString();
    }
}
