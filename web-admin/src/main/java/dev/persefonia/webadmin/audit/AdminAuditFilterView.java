package dev.persefonia.webadmin.audit;

import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import org.springframework.web.util.UriComponentsBuilder;

/** Normalized filter state and safe pagination URI composition. */
public record AdminAuditFilterView(AuditSearchRequest request) {
    public String action() { return request.actionOptional().map(AuditAction::value).orElse(""); }
    public String actorType() { return request.actorType() == null ? "" : request.actorType().name(); }
    public String actorId() { return request.actorIdOptional().map(SourceEntityId::value).map(Object::toString).orElse(""); }
    public String entityContext() { return request.entityContextOptional().map(SourceContext::value).orElse(""); }
    public String entityType() { return request.entityTypeOptional().map(SourceType::value).orElse(""); }
    public String entityId() { return request.entityIdOptional().map(SourceEntityId::value).map(Object::toString).orElse(""); }
    public String from() { return request.occurredFromInclusiveOptional().map(Object::toString).orElse(""); }
    public String to() { return request.occurredToExclusiveOptional().map(Object::toString).orElse(""); }

    public String pageUrl(int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/audit");
        add(builder, "action", action());
        add(builder, "actorType", actorType());
        add(builder, "actorId", actorId());
        add(builder, "entityContext", entityContext());
        add(builder, "entityType", entityType());
        add(builder, "entityId", entityId());
        add(builder, "from", from());
        add(builder, "to", to());
        builder.queryParam("pageSize", request.pageSize());
        builder.queryParam("page", page);
        return builder.build().encode().toUriString();
    }

    private static void add(UriComponentsBuilder builder, String name, String value) {
        if (!value.isEmpty()) {
            builder.queryParam(name, value);
        }
    }
}
