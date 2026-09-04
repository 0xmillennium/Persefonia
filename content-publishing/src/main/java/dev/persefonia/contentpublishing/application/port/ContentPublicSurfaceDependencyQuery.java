package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.publicview.ContentPublicSurfaceDependencies;
import dev.persefonia.contentpublishing.domain.content.ContentId;

public interface ContentPublicSurfaceDependencyQuery {
    ContentPublicSurfaceDependencies findFor(ContentId contentId, int limit);
}
