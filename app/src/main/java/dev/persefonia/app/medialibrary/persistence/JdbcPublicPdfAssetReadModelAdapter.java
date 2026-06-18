package dev.persefonia.app.medialibrary.persistence;

import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReadModel;
import dev.persefonia.medialibrary.application.publicview.PublicPdfAssetReference;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPublicPdfAssetReadModelAdapter implements PublicPdfAssetReadModel {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcPublicPdfAssetReadModelAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<PublicPdfAssetReference> findEligiblePublicPdf(AssetId assetId) {
        Objects.requireNonNull(assetId, "assetId");
        return jdbc().query("""
                SELECT id, original_filename, content_type, size_bytes, updated_at
                FROM media.assets
                WHERE id = :id
                  AND kind = 'PDF'
                  AND visibility = 'PUBLIC'
                  AND processing_status = 'NOT_REQUIRED'
                  AND content_type = 'application/pdf'
                """, Map.of("id", assetId.value()), (resultSet, rowNumber) -> reference(resultSet)).stream().findFirst();
    }

    @Override
    public List<PublicPdfAssetReference> listEligiblePublicPdfs() {
        return jdbc().query("""
                SELECT id, original_filename, content_type, size_bytes, updated_at
                FROM media.assets
                WHERE kind = 'PDF'
                  AND visibility = 'PUBLIC'
                  AND processing_status = 'NOT_REQUIRED'
                  AND content_type = 'application/pdf'
                ORDER BY updated_at DESC, original_filename ASC
                """, (resultSet, rowNumber) -> reference(resultSet));
    }

    private static PublicPdfAssetReference reference(ResultSet resultSet) throws SQLException {
        return new PublicPdfAssetReference(
                AssetId.from(resultSet.getObject("id", UUID.class)),
                resultSet.getString("original_filename"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                instant(resultSet, "updated_at"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp.toInstant();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new MediaLibraryPersistenceException("JDBC public PDF asset read model is not available.");
        }
        return available;
    }
}
