package dev.persefonia.app.medialibrary.recovery;

import dev.persefonia.medialibrary.application.recovery.*;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcMediaRecoveryInventoryReadAdapter implements MediaRecoveryInventoryReadPort {
    private static final UUID LOWEST_UUID = new UUID(0, 0);
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public JdbcMediaRecoveryInventoryReadAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public MediaRecoveryInventoryPage readPage(MediaRecoveryCursor after, int pageSize) {
        if (pageSize < 1 || pageSize > 1000) throw new IllegalArgumentException("invalid recovery page size");
        int afterKind = after == null ? -1 : rank(after.kind());
        UUID afterId = after == null ? LOWEST_UUID : after.objectId();
        List<MediaRecoveryObjectReference> rows = jdbc.getObject().query("""
                SELECT object_kind, kind_rank, object_id, asset_id, variant_name,
                       storage_path, size_bytes, checksum
                FROM (
                    SELECT 'ORIGINAL' AS object_kind, 0 AS kind_rank, id AS object_id,
                           id AS asset_id, NULL::text AS variant_name, storage_path, size_bytes, checksum
                    FROM media.assets
                    UNION ALL
                    SELECT 'VARIANT' AS object_kind, 1 AS kind_rank, id AS object_id,
                           asset_id, name AS variant_name, storage_path, size_bytes, checksum
                    FROM media.asset_variants
                ) inventory
                WHERE kind_rank > :afterKind
                   OR (kind_rank = :afterKind AND object_id > :afterId)
                ORDER BY kind_rank, object_id
                LIMIT :limit
                """, Map.of("afterKind", afterKind, "afterId", afterId, "limit", pageSize + 1),
                (resultSet, rowNumber) -> new MediaRecoveryObjectReference(
                        MediaRecoveryObjectKind.valueOf(resultSet.getString("object_kind")),
                        resultSet.getObject("object_id", UUID.class),
                        AssetId.from(resultSet.getObject("asset_id", UUID.class)),
                        resultSet.getString("variant_name"),
                        StoragePath.of(resultSet.getString("storage_path")),
                        resultSet.getLong("size_bytes"),
                        resultSet.getString("checksum")));
        boolean more = rows.size() > pageSize;
        List<MediaRecoveryObjectReference> items = more ? List.copyOf(rows.subList(0, pageSize)) : List.copyOf(rows);
        MediaRecoveryCursor next = more ? cursor(items.getLast()) : null;
        return new MediaRecoveryInventoryPage(items, next);
    }

    private static int rank(MediaRecoveryObjectKind kind) { return kind == MediaRecoveryObjectKind.ORIGINAL ? 0 : 1; }
    private static MediaRecoveryCursor cursor(MediaRecoveryObjectReference reference) {
        return new MediaRecoveryCursor(reference.kind(), reference.objectId());
    }
}
