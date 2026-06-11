package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.domain.content.RendererVersion;

final class MarkdownRendererVersion {
    private static final RendererVersion VERSION = RendererVersion.of("persefonia-markdown-v1");

    RendererVersion current() {
        return VERSION;
    }
}
