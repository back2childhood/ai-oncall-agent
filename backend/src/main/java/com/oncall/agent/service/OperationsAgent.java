package com.oncall.agent.service;

import com.oncall.agent.domain.OperationalSyncRun;
import com.oncall.agent.domain.SyncStatus;
import com.oncall.agent.dto.OperationalSyncResponse;
import com.oncall.agent.dto.OperationalSyncSummary;
import com.oncall.agent.repository.OperationalSyncRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OperationsAgent {
    private final PrometheusAlertClient prometheusAlertClient;
    private final McpLogClient mcpLogClient;
    private final IngestionService ingestionService;
    private final OperationalSyncRunRepository syncRunRepository;
    private final boolean syncEnabled;
    private final long fixedDelayMs;

    public OperationsAgent(
            PrometheusAlertClient prometheusAlertClient,
            McpLogClient mcpLogClient,
            IngestionService ingestionService,
            OperationalSyncRunRepository syncRunRepository,
            @Value("${app.operations.sync-enabled}") boolean syncEnabled,
            @Value("${app.operations.fixed-delay-ms}") long fixedDelayMs) {
        this.prometheusAlertClient = prometheusAlertClient;
        this.mcpLogClient = mcpLogClient;
        this.ingestionService = ingestionService;
        this.syncRunRepository = syncRunRepository;
        this.syncEnabled = syncEnabled;
        this.fixedDelayMs = fixedDelayMs;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public List<OperationalSyncResponse> processSignalsNow() {
        return List.of(
                processSnapshot("prometheus", prometheusAlertClient::fetchAlerts, prometheusAlertClient.isEnabled()),
                processSnapshot("mcp-logs", mcpLogClient::queryLogs, mcpLogClient.isEnabled()));
    }

    public OperationalSyncSummary summary() {
        return new OperationalSyncSummary(
                syncEnabled,
                prometheusAlertClient.isEnabled(),
                mcpLogClient.isEnabled(),
                fixedDelayMs,
                syncRunRepository.findTop20ByOrderByStartedAtDesc().stream()
                        .map(this::toResponse)
                        .toList());
    }

    private OperationalSyncResponse processSnapshot(String source, SnapshotSupplier supplier, boolean sourceEnabled) {
        Instant started = Instant.now();
        OperationalSyncRun run = new OperationalSyncRun();
        run.setId(UUID.randomUUID());
        run.setSource(source);
        run.setStartedAt(started);

        try {
            if (!sourceEnabled) {
                run.setStatus(SyncStatus.SKIPPED);
                run.setItemCount(0);
                run.setMessage(source + " sync is disabled");
                return save(run);
            }

            OperationalSnapshot snapshot = supplier.get();
            run.setItemCount(snapshot.itemCount());
            if (snapshot.isEmpty()) {
                run.setStatus(SyncStatus.SKIPPED);
                run.setMessage("No new " + source + " items found");
                return save(run);
            }

            UUID documentId = ingestionService.ingestText(
                    snapshot.filename(),
                    "text/plain",
                    snapshot.sourceType(),
                    snapshot.content());
            run.setDocumentId(documentId);
            run.setStatus(SyncStatus.SUCCESS);
            run.setMessage("Operations Agent indexed " + snapshot.itemCount() + " " + source + " items");
            return save(run);
        } catch (RuntimeException ex) {
            run.setStatus(SyncStatus.FAILED);
            run.setItemCount(0);
            run.setMessage(ex.getMessage());
            return save(run);
        }
    }

    private OperationalSyncResponse save(OperationalSyncRun run) {
        run.setFinishedAt(Instant.now());
        return toResponse(syncRunRepository.save(run));
    }

    private OperationalSyncResponse toResponse(OperationalSyncRun run) {
        return new OperationalSyncResponse(
                run.getId(),
                run.getSource(),
                run.getStatus(),
                run.getItemCount(),
                run.getDocumentId(),
                run.getMessage(),
                run.getStartedAt(),
                run.getFinishedAt());
    }

    @FunctionalInterface
    private interface SnapshotSupplier {
        OperationalSnapshot get();
    }
}
