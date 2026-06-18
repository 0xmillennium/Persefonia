package dev.persefonia.medialibrary.application.processing;

public interface ImageMetadataReader {
    ImageMetadata read(byte[] imageBytes);
}
