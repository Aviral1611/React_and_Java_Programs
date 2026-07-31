package backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class DocumentSummary {
    private final String documentId;
    private final String title;
    private final String documentType;
    private final String lastUpdatedBy;
    private final String lastUpdatedAt;

    public DocumentSummary(
            String documentId,
            String title,
            String documentType,
            String lastUpdatedBy,
            String lastUpdatedAt) {
        this.documentId = documentId;
        this.title = title;
        this.documentType = documentType;
        this.lastUpdatedBy = lastUpdatedBy;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    @JsonProperty("doc_id")
    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    @JsonProperty("doc_type")
    public String getDocumentType() {
        return documentType;
    }

    @JsonProperty("last_updated_by")
    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    @JsonProperty("last_updated_at")
    public String getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
