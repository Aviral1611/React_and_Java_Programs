package backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DocumentRecord {
    private final String documentId;
    private final String title;
    private final String content;
    private final String documentType;
    private final String filePath;
    private final String lastUpdatedBy;
    private final String lastUpdatedAt;

    public DocumentRecord(
            String documentId,
            String title,
            String content,
            String documentType,
            String filePath,
            String lastUpdatedBy,
            String lastUpdatedAt) {
        this.documentId = documentId;
        this.title = title;
        this.content = content;
        this.documentType = documentType;
        this.filePath = filePath;
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

    public String getContent() {
        return content;
    }

    @JsonIgnore
    public String getDocumentType() {
        return documentType;
    }

    @JsonIgnore
    public String getFilePath() {
        return filePath;
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
