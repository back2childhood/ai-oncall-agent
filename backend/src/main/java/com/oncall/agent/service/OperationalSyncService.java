package com.oncall.agent.service;

import com.oncall.agent.dto.OperationalSyncResponse;
import com.oncall.agent.dto.OperationalSyncSummary;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OperationalSyncService {
    private final OperationsAgent operationsAgent;

    public OperationalSyncService(OperationsAgent operationsAgent) {
        this.operationsAgent = operationsAgent;
    }

    @Scheduled(fixedDelayString = "${app.operations.fixed-delay-ms}")
    public void scheduledSync() {
        if (operationsAgent.isSyncEnabled()) {
            operationsAgent.processSignalsNow();
        }
    }

    public List<OperationalSyncResponse> syncNow() {
        return operationsAgent.processSignalsNow();
    }

    public OperationalSyncSummary summary() {
        return operationsAgent.summary();
    }
}
