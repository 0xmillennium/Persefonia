package dev.persefonia.medialibrary.application.upload;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface UploadByteSource {
    InputStream openStream() throws IOException;
}
