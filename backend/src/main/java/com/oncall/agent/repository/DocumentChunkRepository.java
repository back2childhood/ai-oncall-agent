package com.oncall.agent.repository;

import com.oncall.agent.domain.DocumentChunkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {
    List<DocumentChunkEntity> findByVectorIdIn(List<String> vectorIds);

    Optional<DocumentChunkEntity> findByVectorId(String vectorId);
}
