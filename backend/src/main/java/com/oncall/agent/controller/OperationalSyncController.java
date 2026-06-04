package com.oncall.agent.controller;

import com.oncall.agent.dto.OperationalSyncResponse;
import com.oncall.agent.dto.OperationalSyncSummary;
import com.oncall.agent.service.OperationalSyncService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class OperationalSyncController {
    private final OperationalSyncService operationalSyncService;

    public OperationalSyncController(OperationalSyncService operationalSyncService) {
        this.operationalSyncService = operationalSyncService;
    }

    @GetMapping("/sync")
    public OperationalSyncSummary summary() {
        return operationalSyncService.summary();
    }

    @PostMapping("/sync")
    public List<OperationalSyncResponse> syncNow() {
        return operationalSyncService.syncNow();
    }
}
