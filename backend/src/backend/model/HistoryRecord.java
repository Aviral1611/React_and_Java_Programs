package backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class HistoryRecord {
    private final int historyId;
    private final String oldTitle;
    private final String oldContent;
    private final String changedBy;
    private final String changedAt;
    private final String documentType;

    public HistoryRecord(
            int historyId,
            String oldTitle,
            String oldContent,
            String changedBy,
            String changedAt,
            String documentType) {
        this.historyId = historyId;
        this.oldTitle = oldTitle;
        this.oldContent = oldContent;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.documentType = documentType;
    }

    @JsonProperty("history_id")
    public int getHistoryId() {
        return historyId;
    }

    @JsonProperty("old_title")
    public String getOldTitle() {
        return oldTitle;
    }

    @JsonProperty("old_content")
    public String getOldContent() {
        return oldContent;
    }

    @JsonProperty("changed_by")
    public String getChangedBy() {
        return changedBy;
    }

    @JsonProperty("changed_at")
    public String getChangedAt() {
        return changedAt;
    }

    @JsonProperty("doc_type")
    public String getDocumentType() {
        return documentType;
    }
}
