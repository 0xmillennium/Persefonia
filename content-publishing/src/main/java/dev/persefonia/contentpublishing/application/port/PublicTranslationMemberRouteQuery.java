package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.List;

public interface PublicTranslationMemberRouteQuery {
    List<PublicUrl> findPublicMemberRoutes(TranslationGroupId groupId, int limit);
}
