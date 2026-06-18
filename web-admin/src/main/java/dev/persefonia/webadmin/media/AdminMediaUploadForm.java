package dev.persefonia.webadmin.media;

import org.springframework.web.multipart.MultipartFile;

public final class AdminMediaUploadForm {
    private MultipartFile file;

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
