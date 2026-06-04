package com.oncall.agent.repository;

import com.oncall.agent.domain.OperationalSyncRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalSyncRunRepository extends JpaRepository<OperationalSyncRun, UUID> {
    Optional<OperationalSyncRun> findFirstBySourceOrderByStartedAtDesc(String source);

    List<OperationalSyncRun> findTop20ByOrderByStartedAtDesc();
}
