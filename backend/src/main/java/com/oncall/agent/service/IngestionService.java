package com.oncall.agent.service;

import com.oncall.agent.domain.DocumentChunkEntity;
import com.oncall.agent.domain.DocumentStatus;
import com.oncall.agent.domain.UploadedDocument;
import com.oncall.agent.dto.DocumentResponse;
import com.oncall.agent.repository.DocumentChunkRepository;
import com.oncall.agent.repository.UploadedDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionService {
    private final UploadedDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentParserService parserService;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final boolean demoMode;

    public IngestionService(
            UploadedDocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DocumentParserService parserService,
            ChunkingService chunkingService,
            VectorStore vectorStore,
            @Value("${app.demo-mode}") boolean demoMode) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.parserService = parserService;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.demoMode = demoMode;
    }

    @Transactional
    public DocumentResponse ingest(MultipartFile file, String sourceType) {
        return ingestSections(
                file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(),
                file.getContentType(),
                sourceType,
                parserService.parseSections(file));
    }

    @Transactional
    public UUID ingestText(String filename, String contentType, String sourceType, String text) {
        return ingestSections(filename, contentType, sourceType, parserService.parseTextSections(text, filename)).id();
    }

    private DocumentResponse ingestSections(
            String filename,
            String contentType,
            String sourceType,
            List<ParsedSection> sections) {
        String normalizedFilename = filename == null || filename.isBlank() ? "ingested-context" : filename;
        String normalizedSourceType = sourceType == null || sourceType.isBlank() ? "knowledge" : sourceType;
        return documentRepository.findFirstByFilenameAndSourceTypeAndStatusOrderByCreatedAtDesc(
                        normalizedFilename, normalizedSourceType, DocumentStatus.READY)
                .map(existing -> toResponse(existing, true))
                .orElseGet(() -> ingestNewSections(normalizedFilename, contentType, normalizedSourceType, sections));
    }

    private DocumentResponse ingestNewSections(
            String filename,
            String contentType,
            String sourceType,
            List<ParsedSection> sections) {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now();
        UploadedDocument uploaded = new UploadedDocument();
        uploaded.setId(documentId);
        uploaded.setFilename(filename);
        uploaded.setContentType(contentType);
        uploaded.setSourceType(sourceType);
        uploaded.setStatus(DocumentStatus.PROCESSING);
        uploaded.setCreatedAt(now);
        uploaded.setUpdatedAt(now);
        documentRepository.save(uploaded);

        try {
            List<ChunkCandidate> chunks = chunkingService.chunk(sections);
            List<Document> vectorDocuments = chunks.stream()
                    .map(chunk -> toVectorDocument(uploaded, chunk))
                    .toList();
            if (!vectorDocuments.isEmpty()) {
                if (!demoMode) {
                    vectorStore.add(vectorDocuments);
                }
                chunkRepository.saveAll(chunks.stream()
                        .map(chunk -> toEntity(uploaded.getId(), vectorDocuments.get(chunk.chunkIndex()).getId(), chunk))
                        .toList());
            }
            uploaded.setChunkCount(chunks.size());
            uploaded.setStatus(DocumentStatus.READY);
            uploaded.setUpdatedAt(Instant.now());
        } catch (RuntimeException ex) {
            uploaded.setStatus(DocumentStatus.FAILED);
            uploaded.setErrorMessage(ex.getMessage());
            uploaded.setUpdatedAt(Instant.now());
        }

        return toResponse(documentRepository.save(uploaded));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments() {
        return documentRepository.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    private Document toVectorDocument(UploadedDocument uploaded, ChunkCandidate chunk) {
        String vectorId = UUID.randomUUID().toString();
        return Document.builder()
                .id(vectorId)
                .text(chunk.content())
                .metadata(Map.of(
                        "documentId", uploaded.getId().toString(),
                        "filename", uploaded.getFilename(),
                        "sourceType", uploaded.getSourceType(),
                        "title", chunk.title(),
                        "chapterPath", chunk.chapterPath(),
                        "chunkIndex", chunk.chunkIndex()))
                .build();
    }

    private DocumentChunkEntity toEntity(UUID documentId, String vectorId, ChunkCandidate chunk) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(UUID.randomUUID());
        entity.setDocumentId(documentId);
        entity.setVectorId(vectorId);
        entity.setTitle(chunk.title());
        entity.setChapterPath(chunk.chapterPath());
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setContent(chunk.content());
        entity.setTokenEstimate(chunk.tokenEstimate());
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private DocumentResponse toResponse(UploadedDocument document) {
        return toResponse(document, false);
    }

    private DocumentResponse toResponse(UploadedDocument document, boolean exists) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getContentType(),
                document.getSourceType(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                exists);
    }
}
