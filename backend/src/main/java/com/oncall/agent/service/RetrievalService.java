package com.oncall.agent.service;

import com.oncall.agent.domain.DocumentChunkEntity;
import com.oncall.agent.domain.UploadedDocument;
import com.oncall.agent.repository.DocumentChunkRepository;
import com.oncall.agent.repository.UploadedDocumentRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalService {
    private final VectorStore vectorStore;
    private final DocumentChunkRepository chunkRepository;
    private final UploadedDocumentRepository documentRepository;
    private final int defaultTopK;
    private final int candidateK;
    private final boolean demoMode;

    public RetrievalService(
            VectorStore vectorStore,
            DocumentChunkRepository chunkRepository,
            UploadedDocumentRepository documentRepository,
            @Value("${app.retrieval.top-k}") int defaultTopK,
            @Value("${app.retrieval.candidate-k}") int candidateK,
            @Value("${app.demo-mode}") boolean demoMode) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.defaultTopK = defaultTopK;
        this.candidateK = candidateK;
        this.demoMode = demoMode;
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(String question, Integer requestedTopK) {
        int topK = requestedTopK == null || requestedTopK < 1 ? defaultTopK : Math.min(requestedTopK, 20);
        if (demoMode) {
            return retrieveWithKeywords(question, topK);
        }
        int candidates = Math.max(candidateK, topK);
        List<Document> vectorHits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(candidates)
                .similarityThresholdAll()
                .build());

        List<String> vectorIds = vectorHits.stream().map(Document::getId).toList();
        Map<String, DocumentChunkEntity> chunksByVectorId = new HashMap<>();
        chunkRepository.findByVectorIdIn(vectorIds)
                .forEach(chunk -> chunksByVectorId.put(chunk.getVectorId(), chunk));

        Map<java.util.UUID, UploadedDocument> documentsById = new HashMap<>();
        documentRepository.findAllById(chunksByVectorId.values().stream()
                        .map(DocumentChunkEntity::getDocumentId)
                        .toList())
                .forEach(document -> documentsById.put(document.getId(), document));

        return vectorHits.stream()
                .map(hit -> toRetrieved(question, hit, chunksByVectorId, documentsById))
                .filter(item -> item != null)
                .sorted(Comparator.comparingDouble(RetrievedChunk::rerankScore).reversed())
                .limit(topK)
                .toList();
    }

    private List<RetrievedChunk> retrieveWithKeywords(String question, int topK) {
        List<DocumentChunkEntity> chunks = chunkRepository.findAll();
        Map<java.util.UUID, UploadedDocument> documentsById = new HashMap<>();
        documentRepository.findAllById(chunks.stream()
                        .map(DocumentChunkEntity::getDocumentId)
                        .toList())
                .forEach(document -> documentsById.put(document.getId(), document));

        return chunks.stream()
                .map(chunk -> new RetrievedChunk(
                        chunk,
                        documentsById.get(chunk.getDocumentId()),
                        0.0,
                        keywordBoost(question, chunk)))
                .filter(item -> item.rerankScore() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::rerankScore).reversed())
                .limit(topK)
                .toList();
    }

    private RetrievedChunk toRetrieved(
            String question,
            Document hit,
            Map<String, DocumentChunkEntity> chunksByVectorId,
            Map<java.util.UUID, UploadedDocument> documentsById) {
        DocumentChunkEntity chunk = chunksByVectorId.get(hit.getId());
        if (chunk == null) {
            return null;
        }
        UploadedDocument document = documentsById.get(chunk.getDocumentId());
        double vectorScore = hit.getScore() == null ? 0.0 : hit.getScore();
        double rerankScore = vectorScore + keywordBoost(question, chunk);
        return new RetrievedChunk(chunk, document, vectorScore, rerankScore);
    }

    private double keywordBoost(String question, DocumentChunkEntity chunk) {
        Set<String> queryTerms = terms(question);
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> chunkTerms = terms(chunk.getTitle() + " " + chunk.getChapterPath() + " " + chunk.getContent());
        long matches = queryTerms.stream().filter(chunkTerms::contains).count();
        double coverage = (double) matches / queryTerms.size();
        double titleBonus = terms(chunk.getTitle()).stream().anyMatch(queryTerms::contains) ? 0.08 : 0.0;
        return Math.min(0.25, coverage * 0.17 + titleBonus);
    }

    private Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_./:-]+")) {
            if (token.length() > 2) {
                result.add(token);
            }
        }
        return result;
    }
}
