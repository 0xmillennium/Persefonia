package dev.persefonia.app.medialibrary.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class MediaLibrarySql {
    private MediaLibrarySql() {
    }

    static int update(String sql, Object... arguments) throws SQLException {
        try (var connection = MediaLibraryMigrationDatabase.POSTGRES.createConnection("");
                var statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            return statement.executeUpdate();
        }
    }

    static long count(String sql, Object... arguments) throws SQLException {
        try (var connection = MediaLibraryMigrationDatabase.POSTGRES.createConnection("");
                var statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    static List<String> strings(String sql, Object... arguments) throws SQLException {
        try (var connection = MediaLibraryMigrationDatabase.POSTGRES.createConnection("");
                var statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (result.next()) {
                    values.add(result.getString(1));
                }
                return values;
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }
}
