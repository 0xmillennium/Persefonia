package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;

public record ContentPreviewResult(ContentId contentId, ContentRenderSnapshot snapshot) {
}
