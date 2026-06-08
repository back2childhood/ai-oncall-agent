package com.oncall.agent.repository;

import com.oncall.agent.domain.DocumentStatus;
import com.oncall.agent.domain.UploadedDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {
    Optional<UploadedDocument> findFirstByFilenameAndSourceTypeAndStatusOrderByCreatedAtDesc(
            String filename, String sourceType, DocumentStatus status);
}

