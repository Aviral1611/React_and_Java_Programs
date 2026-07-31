package backend.model;

public final class AnnotationSaveResult {
    private final String message;
    private final boolean alreadySaved;

    public AnnotationSaveResult(String message, boolean alreadySaved) {
        this.message = message;
        this.alreadySaved = alreadySaved;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAlreadySaved() {
        return alreadySaved;
    }
}
