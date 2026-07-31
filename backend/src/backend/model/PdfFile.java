package backend.model;

import java.io.File;

public final class PdfFile {
    private final File file;
    private final String title;

    public PdfFile(File file, String title) {
        this.file = file;
        this.title = title;
    }

    public File getFile() {
        return file;
    }

    public String getTitle() {
        return title;
    }
}
